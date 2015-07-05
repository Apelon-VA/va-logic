/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.logic.integration;

import gov.vha.isaac.ochre.api.logic.LogicService;
import static gov.vha.isaac.ochre.api.constants.Constants.CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY;
import gov.vha.isaac.metadata.coordinates.EditCoordinates;
import gov.vha.isaac.metadata.coordinates.LogicCoordinates;
import gov.vha.isaac.metadata.coordinates.StampCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.ConceptModel;
import gov.vha.isaac.ochre.api.ConfigurationService;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.Get;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.ObjectChronicleTaskService;
import gov.vha.isaac.ochre.api.State;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.chronicle.LatestVersion;
import gov.vha.isaac.ochre.api.chronicle.ObjectChronology;
import gov.vha.isaac.ochre.api.chronicle.StampedVersion;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.classifier.ClassifierService;
import gov.vha.isaac.ochre.api.commit.ChangeCheckerMode;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilder;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilderService;
import gov.vha.isaac.ochre.api.component.concept.ConceptService;
import gov.vha.isaac.ochre.api.component.concept.ConceptVersion;
import gov.vha.isaac.ochre.api.component.sememe.SememeChronology;
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
import gov.vha.isaac.ochre.api.component.sememe.SememeSnapshotService;
import gov.vha.isaac.ochre.api.component.sememe.version.LogicGraphSememe;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.PremiseType;
import gov.vha.isaac.ochre.api.relationship.RelationshipVersionAdaptor;
import gov.vha.isaac.ochre.model.logic.LogicalExpressionOchreImpl;
import gov.vha.isaac.ochre.model.relationship.RelationshipAdaptorChronologyImpl;
import gov.vha.isaac.ochre.util.UuidT3Generator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.api.MultiException;
import org.ihtsdo.otf.lookup.contracts.contracts.ActiveTaskSet;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.ihtsdo.otf.tcc.api.spec.ConceptSpec;
import org.ihtsdo.otf.tcc.api.spec.ValidationException;
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

    private boolean dbExists = false;

    @BeforeSuite
    public void setUpSuite() throws Exception {
        log.info("oneTimeSetUp");
        System.setProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY, "target/object-chronicles");

        java.nio.file.Path dbFolderPath = Paths.get(System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY));
        dbExists = dbFolderPath.toFile().exists();
        System.out.println("termstore folder path: " + dbFolderPath.toFile().exists());
        LookupService.getService(ConfigurationService.class).setConceptModel(ConceptModel.OCHRE_CONCEPT_MODEL);

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

        LogicService logicService = LookupService.getService(LogicService.class);
        LogicCoordinate logicCoordinate = LogicCoordinates.getStandardElProfile();
        StampCoordinate stampCoordinate = StampCoordinates.getDevelopmentLatestActiveOnly();
        ClassifierService classifier = logicService.getClassifierService(stampCoordinate, logicCoordinate, EditCoordinates.getDefaultUserSolorOverlay());
        Task<ClassifierResults> classifyTask = classifier.classify();
        ClassifierResults results = classifyTask.get();

        log.info(results);
        logResultDetails(results, StampCoordinates.getDevelopmentLatest());

        UUID bleedingSnomedUuid = UuidT3Generator.fromSNOMED(131148009L);

        ConceptChronology<? extends ConceptVersion> bleedingConcept1 = Get.conceptService().getConcept(bleedingSnomedUuid);
        System.out.println("\nFound [1] nid: " + bleedingConcept1.getNid());
        System.out.println("Found [1] concept sequence: " + Get.identifierService().getConceptSequence(bleedingConcept1.getNid()));
        System.out.println("Found [1]: " + bleedingConcept1.toUserString() + "\n " + bleedingConcept1.toString());

        Optional<LatestVersion<? extends LogicalExpression>> lg1
                = logicService.getLogicalExpression(bleedingConcept1.getNid(), logicCoordinate.getStatedAssemblageSequence(), stampCoordinate);
        System.out.println("Stated logic graph:  " + lg1);
        Optional<LatestVersion<? extends LogicalExpression>> lg2
                = logicService.getLogicalExpression(bleedingConcept1.getNid(), logicCoordinate.getInferredAssemblageSequence(), stampCoordinate);
        System.out.println("Inferred logic graph:  " + lg2);

        // Add new concept and definition here to classify. 
        ConceptBuilderService conceptBuilderService = LookupService.getService(ConceptBuilderService.class);
        conceptBuilderService.setDefaultLanguageForDescriptions(IsaacMetadataAuxiliaryBinding.ENGLISH);
        conceptBuilderService.setDefaultDialectAssemblageForDescriptions(IsaacMetadataAuxiliaryBinding.US_ENGLISH_DIALECT);
        conceptBuilderService.setDefaultLogicCoordinate(LogicCoordinates.getStandardElProfile());

        LogicalExpressionBuilderService expressionBuilderService
                = LookupService.getService(LogicalExpressionBuilderService.class);
        LogicalExpressionBuilder defBuilder = expressionBuilderService.getLogicalExpressionBuilder();

        NecessarySet(And(ConceptAssertion(Get.conceptService().getConcept(Snomed.BLEEDING_FINDING.getSequence()), defBuilder)));

        LogicalExpression def = defBuilder.build();
        log.info("Created definition:\n\n " + def);

        ConceptBuilder builder = conceptBuilderService.getDefaultConceptBuilder(
                "primitive child of bleeding", "test concept", def);

        List createdComponents = new ArrayList();
        ConceptChronology concept = builder.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE, createdComponents);

        for (Object component : createdComponents) {
            component.toString();
        }

        Get.commitService().commit("Commit for logic integration incremental classification test. ").get();

        classifyTask = classifier.classify();
        results = classifyTask.get();
        log.info(results);
        validateRelationshipAdaptors();
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

        Task<Integer> loadTask = tts.startLoadTask(ConceptModel.OCHRE_CONCEPT_MODEL, IsaacMetadataAuxiliaryBinding.DEVELOPMENT,
                snomedDataFile, logicMetadataFile);
        int conceptCount = loadTask.get();
        Instant finish = Instant.now();
        Duration duration = Duration.between(start, finish);
        log.info("  Loaded " + conceptCount + " concepts in: " + duration);
        double nsPerConcept = 1.0d * duration.toNanos() / conceptCount;
        log.info("  nsPerConcept: {}", nsPerConcept);

        double msPerConcept = 1.0d * duration.toMillis() / conceptCount;
        log.info("  msPerConcept: {}", msPerConcept);

        log.info("  concepts in map: {}", Get.conceptService().getConceptCount());

        log.info("  sequences map: {}", Get.identifierService().getConceptSequenceStream().distinct().count());
    }

    private void logResultDetails(ClassifierResults results, StampCoordinate stampCoordinate) {
        StringBuilder builder = new StringBuilder();
        SememeSnapshotService<LogicGraphSememe> sememeSnapshot = Get.sememeService().getSnapshot(LogicGraphSememe.class, stampCoordinate);
        results.getEquivalentSets().forEach((conceptSequenceSet) -> {
            builder.append("--------- Equivalent Set ---------\n");
            conceptSequenceSet.stream().forEach((conceptSequence) -> {
                int conceptNid = Get.identifierService().getConceptNid(conceptSequence);
                Optional<? extends ObjectChronology<? extends StampedVersion>> optionalConcept = Get.identifiedObjectService().getIdentifiedObjectChronology(conceptNid);
                builder.append(conceptSequence);
                if (optionalConcept.isPresent()) {
                    builder.append(" ");
                    builder.append(optionalConcept.get().toString());
                }
                builder.append(":\n ");

                sememeSnapshot.getLatestSememeVersionsForComponentFromAssemblage(conceptNid,
                        LogicCoordinates.getStandardElProfile().getStatedAssemblageSequence())
                        .forEach((LatestVersion<LogicGraphSememe> logicGraphSememe) -> {
                            LogicalExpressionOchreImpl graph = new LogicalExpressionOchreImpl(logicGraphSememe.value().getGraphData(),
                                    DataSource.INTERNAL);
                            builder.append(graph.toString());

                        });
            });
        });

        log.info(builder.toString());
    }
    

    private void validateRelationshipAdaptors() throws ValidationException {
        System.out.println("\n\n");
        log.info("Validating relationship adaptors...");
        ConceptSpec pancreatitis = new ConceptSpec("Acute pancreatitis (disorder)", UUID.fromString("97eb352a-bfa3-304d-be67-2a2730e43bbb"));
        ConceptChronology<? extends ConceptVersion> concept = Get.conceptService().getConcept(pancreatitis.getLenient().getPrimordialUuid());
        System.out.println("GETTING ORIGINATING RELATIONSHIPS FOR CONCEPT: " + concept.toUserString() + " CONCEPT SEQUENCE: " + concept.getConceptSequence());
        List<? extends SememeChronology<? extends RelationshipVersionAdaptor>> relationships = concept.getRelationshipListOriginatingFromConcept(LogicCoordinates.getStandardElProfile());
        printFormatedRelationshipAdaptors(relationships);
        
        System.out.println("GETTING RELATIONSHIPS WITH CONCEPT AS DEST: " + concept.toUserString() + " CONCEPT SEQUENCE: " + concept.getConceptSequence());
        List<? extends SememeChronology<? extends RelationshipVersionAdaptor>> relationshipsDest = concept.getRelationshipListWithConceptAsDestination(LogicCoordinates.getStandardElProfile());
        printFormatedRelationshipAdaptors(relationshipsDest);
        log.info("Finished validating relationship adaptors.");
        System.out.println("\n\n");
    }
    
    private void printFormatedRelationshipAdaptors(List<? extends SememeChronology<? extends RelationshipVersionAdaptor>> rels){
        System.out.println("RELATIONSHIPS SIZE : " + rels.size());
        for (SememeChronology<? extends RelationshipVersionAdaptor> s : rels) {
            RelationshipAdaptorChronologyImpl rel = (RelationshipAdaptorChronologyImpl) s;
            for(RelationshipVersionAdaptor rv : rel.getVersionList()){
                int originSequence = rv.getOriginSequence();
                ConceptChronology<? extends ConceptVersion> originConcept = LookupService.getService(ConceptService.class).getConcept(originSequence);
                int destinationSequence = rv.getDestinationSequence();
                ConceptChronology<? extends ConceptVersion> destinationConcept = LookupService.getService(ConceptService.class).getConcept(destinationSequence);
                int group = rv.getGroup();
                PremiseType premiseType = rv.getPremiseType();
                int typeSequence = rv.getTypeSequence();
                ConceptChronology<? extends ConceptVersion> typeConcept = LookupService.getService(ConceptService.class).getConcept(typeSequence);
                int stampSequence = rv.getStampSequence();
                State statusForStamp = Get.commitService().getStatusForStamp(stampSequence);
                System.out.println("-NID-: " + rv.getNid() + " -ORIGIN-: " + originConcept.toUserString()
                    + " -ORIGIN SEQUENCE-: " + originSequence +" -TYPE-: " + typeConcept.toUserString()
                    + " -DEST-: " + destinationConcept.toUserString() + " -DEST SEQUENCE: " + destinationSequence
                    + " -GROUP-: " + group + " -CHAR-: " + premiseType + " -STATUS-: " + statusForStamp);
            }
        }
    }    
}
