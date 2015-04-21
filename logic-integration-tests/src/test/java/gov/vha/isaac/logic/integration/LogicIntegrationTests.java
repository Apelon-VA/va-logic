/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.logic.integration;

import gov.vha.isaac.cradle.CradleExtensions;
import gov.vha.isaac.cradle.identifier.IdentifierProvider;
import gov.vha.isaac.logic.LogicService;
import static gov.vha.isaac.ochre.api.constants.Constants.CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY;
import gov.vha.isaac.metadata.coordinates.EditCoordinates;
import gov.vha.isaac.metadata.coordinates.LogicCoordinates;
import gov.vha.isaac.metadata.coordinates.StampCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.ObjectChronicleTaskService;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.commit.CommitManager;
import gov.vha.isaac.ochre.api.commit.CommitService;
import gov.vha.isaac.ochre.api.logic.LogicalDefinition;
import gov.vha.isaac.ochre.api.logic.LogicalDefinitionBuilder;
import static gov.vha.isaac.ochre.api.logic.LogicalDefinitionBuilder.*;
import gov.vha.isaac.ochre.api.logic.LogicalDefinitionBuilderService;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import gov.vha.isaac.ochre.api.memory.HeapUseTicker;
import gov.vha.isaac.ochre.api.progress.ActiveTasksTicker;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.api.MultiException;
import org.ihtsdo.otf.lookup.contracts.contracts.ActiveTaskSet;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.ihtsdo.otf.tcc.model.cc.termstore.PersistentStoreI;
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
    private static IdentifierProvider sequenceProvider;
    private static TaxonomyService taxonomyProvider;
    private static CommitService commitProvider;

    /**
     * @return the sequenceProvider
     */
    public static IdentifierProvider getSequenceService() {
        if (sequenceProvider == null) {
            sequenceProvider = LookupService.getService(IdentifierProvider.class);
        }
        return sequenceProvider;
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
        ObjectChronicleTaskService tts = LookupService.getService(ObjectChronicleTaskService.class);
        PersistentStoreI ps = LookupService.getService(PersistentStoreI.class);

        String mapDbFolder = System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY);
        if (mapDbFolder == null || mapDbFolder.isEmpty()) {
            throw new IllegalStateException(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY + " has not been set.");
        }

        CradleExtensions mapDbService = (CradleExtensions) ps;

        if (!dbExists) {
            loadDatabase(tts, mapDbService);
        }
        
        LogicService logic = LookupService.getService(LogicService.class);

        if (!dbExists) {
            logic.initialize(LogicCoordinates.getStandardElProfile());
        }

        logic.fullClassification(StampCoordinates.getDevelopmentLatest(), 
                LogicCoordinates.getStandardElProfile(), EditCoordinates.getDefaultUserSolorOverlay());
        
        // Add new concept and definition here to classify. 
        
        LogicalDefinitionBuilderService defBuilderService = LookupService.getService(LogicalDefinitionBuilderService.class);
        LogicalDefinitionBuilder defBuilder = defBuilderService.getBuilder();
        
        NecessarySet(And(ConceptAssertion(Snomed.BLEEDING_FINDING, defBuilder)));
        
        LogicalDefinition def = defBuilder.build();
        log.info("Created definition: " + def);
        
        getCommitService().commit("Commit for logic integration incremental classification test. ");
        logic.incrementalClassification(StampCoordinates.getDevelopmentLatest(), 
                LogicCoordinates.getStandardElProfile(), EditCoordinates.getDefaultUserSolorOverlay());
        
        //exportDatabase(tts);
        //exportLogicGraphDatabase(tts);
    }
    
    private void exportDatabase(ObjectChronicleTaskService tts) throws InterruptedException, ExecutionException {
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
   private void exportLogicGraphDatabase(ObjectChronicleTaskService tts) throws InterruptedException, ExecutionException {
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

    private void loadDatabase(ObjectChronicleTaskService tts, CradleExtensions ps) throws ExecutionException, IOException, MultiException, InterruptedException {
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

        log.info("  concepts in map: {}", ps.getConceptCount());

        log.info("  sequences map: {}", getSequenceService().getConceptSequenceStream().distinct().count());
    }
}
