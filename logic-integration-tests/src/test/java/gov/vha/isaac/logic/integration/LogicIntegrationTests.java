/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.logic.integration;

import gov.vha.isaac.cradle.identifier.IdentifierProvider;
import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.LogicService;
import static gov.vha.isaac.ochre.api.constants.Constants.CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY;
import gov.vha.isaac.metadata.coordinates.EditCoordinates;
import gov.vha.isaac.metadata.coordinates.LogicCoordinates;
import gov.vha.isaac.metadata.coordinates.StampCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.ConceptModel;
import gov.vha.isaac.ochre.api.ConfigurationService;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.IdentifiedObjectService;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.ObjectChronicleTaskService;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.chronicle.LatestVersion;
import gov.vha.isaac.ochre.api.chronicle.ObjectChronology;
import gov.vha.isaac.ochre.api.chronicle.StampedVersion;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.commit.ChangeCheckerMode;
import gov.vha.isaac.ochre.api.commit.CommitService;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilder;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilderService;
import gov.vha.isaac.ochre.api.component.concept.ConceptService;
import gov.vha.isaac.ochre.api.component.concept.ConceptServiceManagerI;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import static gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder.*;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilderService;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import gov.vha.isaac.ochre.api.memory.HeapUseTicker;
import gov.vha.isaac.ochre.api.progress.ActiveTasksTicker;
import gov.vha.isaac.ochre.api.component.sememe.SememeService;
import gov.vha.isaac.ochre.api.component.sememe.SememeSnapshotService;
import gov.vha.isaac.ochre.api.component.sememe.version.LogicGraphSememe;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.api.MultiException;
import org.ihtsdo.otf.lookup.contracts.contracts.ActiveTaskSet;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.jvnet.testing.hk2testng.HK2;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

/**
 *
 * @author kec
 */
@HK2("mapdb")

public class LogicIntegrationTests {

    private static final Logger log = LogManager.getLogger();
    private static IdentifierProvider identifierProvider;
    private static TaxonomyService taxonomyProvider;
    private static CommitService commitProvider;
    private static IdentifiedObjectService identifiedObjectProvider;
    private static ConceptService conceptService;
    
    public static ConceptService getConceptService() {
        if (conceptService == null) {
            conceptService = LookupService.getService(ConceptServiceManagerI.class).get();
        }
        return conceptService;
    }

    
     public static IdentifiedObjectService getIdentifiedObjectService() {
        if (identifiedObjectProvider == null) {
            identifiedObjectProvider = LookupService.getService(IdentifiedObjectService.class);
        }
        return identifiedObjectProvider;
    }
   /**
     * @return the identifierProvider
     */
    public static IdentifierProvider getIdentifierService() {
        if (identifierProvider == null) {
            identifierProvider = LookupService.getService(IdentifierProvider.class);
        }
        return identifierProvider;
    }

    /**
     * @return the taxonomyProvider
     */
    public static TaxonomyService getTaxonomyService() {
        if (taxonomyProvider == null) {
            taxonomyProvider = LookupService.getService(TaxonomyService.class);
        }
        return taxonomyProvider;
    }
    public static CommitService getCommitService() {
        if (commitProvider == null) {
            commitProvider = LookupService.getService(CommitService.class);
        }
        return commitProvider;
    }

    private static SememeService sememeService;
    public static SememeService getSememeService() {
        if (sememeService == null) {
            sememeService = LookupService.getService(SememeService.class);
        }
        return sememeService;
    }    
    private boolean dbExists = false;

    @BeforeSuite
    public void setUpSuite() throws Exception {
        log.info("oneTimeSetUp");
        System.setProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY, "target/object-chronicles");

        java.nio.file.Path dbFolderPath = Paths.get(System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY));
        dbExists = dbFolderPath.toFile().exists();
        System.out.println("termstore folder path: " + dbFolderPath.toFile().exists());

        LookupService.startupIsaac();
        ActiveTasksTicker.start(10);
        HeapUseTicker.start(10);
    }

    @AfterSuite
    public void tearDownSuite() throws Exception {
        log.info("oneTimeTearDown");
        ActiveTasksTicker.stop();
        HeapUseTicker.stop();
        LookupService.shutdownIsaac();

    }

    @Test
    public void testLoad() throws Exception {

        log.info("  Testing load...");
        String mapDbFolder = System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY);
        if (mapDbFolder == null || mapDbFolder.isEmpty()) {
            throw new IllegalStateException(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY + " has not been set.");
        }


        if (!dbExists) {
            loadDatabase();
        }
        
        LogicService logic = LookupService.getService(LogicService.class);

        if (!dbExists) {
            if (LookupService.getService(ConfigurationService.class).getConceptModel() == ConceptModel.OTF_CONCEPT_MODEL) {
                logic.initialize(LogicCoordinates.getStandardElProfile());
            }
        }

        ClassifierResults results = logic.fullClassification(StampCoordinates.getDevelopmentLatest(), 
                LogicCoordinates.getStandardElProfile(), EditCoordinates.getDefaultUserSolorOverlay());
        log.info(results);
        logResultDetails(results, StampCoordinates.getDevelopmentLatest());
        
        
        
        // Add new concept and definition here to classify. 
        ConceptBuilderService conceptBuilderService = LookupService.getService(ConceptBuilderService.class);
        conceptBuilderService.setDefaultLanguageForDescriptions(IsaacMetadataAuxiliaryBinding.ENGLISH);
        conceptBuilderService.setDefaultDialectAssemblageForDescriptions(IsaacMetadataAuxiliaryBinding.US_ENGLISH_DIALECT);
        conceptBuilderService.setDefaultLogicCoordinate(LogicCoordinates.getStandardElProfile());
        
        
        LogicalExpressionBuilderService expressionBuilderService = 
                LookupService.getService(LogicalExpressionBuilderService.class);
        LogicalExpressionBuilder defBuilder = expressionBuilderService.getLogicalExpressionBuilder();
        
        NecessarySet(And(ConceptAssertion(getConceptService().getConcept(Snomed.BLEEDING_FINDING.getSequence()), defBuilder)));
        
        
        
        
        LogicalExpression def = defBuilder.build();
        log.info("Created definition:\n\n " + def);
        
        ConceptBuilder builder = conceptBuilderService.getDefaultConceptBuilder(
                "primitive child of bleeding", "test concept", def);
        
        List createdComponents = new ArrayList();
        ConceptChronology concept = builder.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE, createdComponents);
        
        for (Object component: createdComponents) {
            component.toString();
        }
        
        getCommitService().commit("Commit for logic integration incremental classification test. ").get();
        ConceptSequenceSet newConcepts = new ConceptSequenceSet();
        newConcepts.add(concept.getConceptSequence());
        results = logic.incrementalClassification(StampCoordinates.getDevelopmentLatest(), 
                LogicCoordinates.getStandardElProfile(), EditCoordinates.getDefaultUserSolorOverlay(), newConcepts);
        log.info(results);
        //exportDatabase(tts);
        //exportLogicGraphDatabase(tts);
    }
    
    private void exportDatabase() throws InterruptedException, ExecutionException {
        ObjectChronicleTaskService tts = LookupService.getService(ObjectChronicleTaskService.class);

        Path logicExportFile = Paths.get("target/logicExportFile.econ");
        Instant start = Instant.now();
        Task<Integer> exportTask = tts.startExportTask(logicExportFile);
        LookupService.getService(ActiveTaskSet.class).get().add(exportTask);
        int conceptCount = exportTask.get();
        LookupService.getService(ActiveTaskSet.class).get().remove(exportTask);
        Instant finish = Instant.now();
        Duration duration = Duration.between(start, finish);
        log.info("  Exported " + conceptCount + " concepts in: " + duration);
        double nsPerConcept = 1.0d * duration.toNanos() / conceptCount;
        log.info("  nsPerConcept: {}", nsPerConcept);
    }
   private void exportLogicGraphDatabase() throws InterruptedException, ExecutionException {
        ObjectChronicleTaskService tts = LookupService.getService(ObjectChronicleTaskService.class);

        Path logicExportFile = Paths.get("target/logicGraphExportFile.econ");
        Instant start = Instant.now();
        Task<Integer> exportTask = tts.startLogicGraphExportTask(logicExportFile);
        LookupService.getService(ActiveTaskSet.class).get().add(exportTask);
        int conceptCount = exportTask.get();
        LookupService.getService(ActiveTaskSet.class).get().remove(exportTask);
        Instant finish = Instant.now();
        Duration duration = Duration.between(start, finish);
        log.info("  Exported " + conceptCount + " concepts in: " + duration);
        double nsPerConcept = 1.0d * duration.toNanos() / conceptCount;
        log.info("  nsPerConcept: {}", nsPerConcept);
    }

    private void loadDatabase() throws ExecutionException, IOException, MultiException, InterruptedException {
        ObjectChronicleTaskService tts = LookupService.getService(ObjectChronicleTaskService.class);

        Path snomedDataFile = Paths.get("target/data/sctSiEConcepts.jbin");
        Path logicMetadataFile = Paths.get("target/data/isaac/metadata/econ/IsaacMetadataAuxiliary.econ");
        Instant start = Instant.now();

        Task<Integer> loadTask = tts.startLoadTask(IsaacMetadataAuxiliaryBinding.DEVELOPMENT,
                snomedDataFile, logicMetadataFile);
        int conceptCount = loadTask.get();
        Instant finish = Instant.now();
        Duration duration = Duration.between(start, finish);
        log.info("  Loaded " + conceptCount + " concepts in: " + duration);
        double nsPerConcept = 1.0d * duration.toNanos() / conceptCount;
        log.info("  nsPerConcept: {}", nsPerConcept);

        double msPerConcept = 1.0d * duration.toMillis() / conceptCount;
        log.info("  msPerConcept: {}", msPerConcept);

        log.info("  concepts in map: {}", LookupService.getService(ConceptServiceManagerI.class).get().getConceptCount());

        log.info("  sequences map: {}", getIdentifierService().getConceptSequenceStream().distinct().count());
    }

    private void logResultDetails(ClassifierResults results, StampCoordinate stampCoordinate) {
        StringBuilder builder = new StringBuilder();
        SememeSnapshotService<LogicGraphSememe> sememeSnapshot = getSememeService().getSnapshot(LogicGraphSememe.class, stampCoordinate);
        results.getEquivalentSets().forEach((conceptSequenceSet) -> {
            builder.append("--------- Equivalent Set ---------\n");
            conceptSequenceSet.stream().forEach((conceptSequence) -> {
                int conceptNid = getIdentifierService().getConceptNid(conceptSequence);
                Optional<? extends ObjectChronology<? extends StampedVersion>> optionalConcept = getIdentifiedObjectService().getIdentifiedObjectChronology(conceptNid);
                builder.append(conceptSequence);
                if (optionalConcept.isPresent()) {
                    builder.append(" ");
                    builder.append(optionalConcept.get().toString());
                }
                builder.append(":\n ");
                
                sememeSnapshot.getLatestActiveSememeVersionsForComponentFromAssemblage(conceptNid, 
                        LogicCoordinates.getStandardElProfile().getStatedAssemblageSequence())
                        .forEach((LatestVersion<LogicGraphSememe> logicGraphSememe) -> {
                            LogicGraph graph = new LogicGraph(logicGraphSememe.value().getGraphData(), 
                                    DataSource.INTERNAL);
                            builder.append(graph.toString());
                        
                        });
                
            });
        });
        
        log.info(builder.toString());
    }
}
