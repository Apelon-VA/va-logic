/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright
 * protection in the United States. Foreign copyrights may apply.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.vha.isaac.logic.integration;

import static gov.vha.isaac.ochre.api.constants.Constants.DATA_STORE_ROOT_LOCATION_PROPERTY;
import static gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder.And;
import static gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder.ConceptAssertion;
import static gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder.NecessarySet;
import gov.vha.isaac.expression.parser.ExpressionReader;
import gov.vha.isaac.expression.parser.ISAACVisitor;
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
import gov.vha.isaac.ochre.api.chronicle.LatestVersion;
import gov.vha.isaac.ochre.api.chronicle.ObjectChronology;
import gov.vha.isaac.ochre.api.chronicle.StampedVersion;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.classifier.ClassifierService;
import gov.vha.isaac.ochre.api.commit.ChangeCheckerMode;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilder;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilderService;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.component.concept.ConceptService;
import gov.vha.isaac.ochre.api.component.concept.ConceptVersion;
import gov.vha.isaac.ochre.api.component.sememe.SememeChronology;
import gov.vha.isaac.ochre.api.component.sememe.SememeSnapshotService;
import gov.vha.isaac.ochre.api.component.sememe.version.LogicGraphSememe;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.PremiseType;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.logic.LogicService;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilderService;
import gov.vha.isaac.ochre.api.memory.HeapUseTicker;
import gov.vha.isaac.ochre.api.progress.ActiveTasksTicker;
import gov.vha.isaac.ochre.api.relationship.RelationshipVersionAdaptor;
import gov.vha.isaac.ochre.model.logic.LogicalExpressionOchreImpl;
import gov.vha.isaac.ochre.model.relationship.RelationshipAdaptorChronologyImpl;
import gov.vha.isaac.ochre.util.UuidT3Generator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.api.MultiException;
import org.ihtsdo.otf.lookup.contracts.contracts.ActiveTaskSet;
import org.ihtsdo.otf.query.lucene.indexers.SememeIndexer;
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
        System.setProperty(DATA_STORE_ROOT_LOCATION_PROPERTY, "target/ochre.data");

        java.nio.file.Path dbFolderPath = Paths.get(System.getProperty(DATA_STORE_ROOT_LOCATION_PROPERTY));
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
        String mapDbFolder = System.getProperty(DATA_STORE_ROOT_LOCATION_PROPERTY);
        if (mapDbFolder == null || mapDbFolder.isEmpty()) {
            throw new IllegalStateException(DATA_STORE_ROOT_LOCATION_PROPERTY + " has not been set.");
        }

        if (!dbExists) {
            loadDatabase();
        }

         validateRelationshipAdaptors();
      
        testHealthConcept();

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

        NecessarySet(And(ConceptAssertion(Get.conceptService().getConcept(Snomed.BLEEDING_FINDING.getConceptSequence()), defBuilder)));

        LogicalExpression def = defBuilder.build();
        log.info("Created definition:\n\n " + def);

        ConceptBuilder builder = conceptBuilderService.getDefaultConceptBuilder(
                "primitive child of bleeding", "test concept", def);

        builder.getFullySpecifiedDescriptionBuilder().setAcceptableInDialectAssemblage(IsaacMetadataAuxiliaryBinding.US_ENGLISH_DIALECT);
        List createdComponents = new ArrayList();
        ConceptChronology concept = builder.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE, createdComponents);

        for (Object component : createdComponents) {
            component.toString();
        }

        Get.commitService().commit("Commit for logic integration incremental classification test. ").get();

        classifyTask = classifier.classify();
        results = classifyTask.get();
        log.info(results);
        //exportDatabase(tts);
        //exportLogicGraphDatabase(tts);
        
        testExpressions();
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

        Path snomedDataFile = Paths.get("target/data/SnomedCoreEConcepts.jbin");
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
        
        log.info("Indexing sememes for logic expression test");
        tts.startIndexTask(SememeIndexer.class).get();
        log.info("Indexing complete");
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
                System.out.println("-NID-: " + rv.getNid() + " -TYPE-: " + typeConcept.toUserString()
                    + " -DEST-: " + destinationConcept.toUserString() + "<" + destinationSequence
                    + "> g: " + group + " " + premiseType + " " + Get.commitService().describeStampSequence(stampSequence));
            }
        }
    }    
    
    
    private void testHealthConcept() {
        ConceptChronology healthConcept = Get.conceptService().getConcept(IsaacMetadataAuxiliaryBinding.HEALTH_CONCEPT.getPrimodialUuid());
        Get.taxonomyService().getTaxonomyParentSequences(IsaacMetadataAuxiliaryBinding.HEALTH_CONCEPT
                .getConceptSequence()).forEach((parentSequence) -> {log.info("Parent: " + Get.conceptDescriptionText(parentSequence));});

         Get.taxonomyService().getTaxonomyParentSequences(IsaacMetadataAuxiliaryBinding.HEALTH_CONCEPT
                .getConceptSequence(), Get.coordinateFactory().createDefaultInferredTaxonomyCoordinate()).forEach((parentSequence) -> {log.info("Parent with tc: " + Get.conceptDescriptionText(parentSequence));});

        List<? extends SememeChronology<? extends RelationshipVersionAdaptor<?>>> originRels = healthConcept.getRelationshipListOriginatingFromConcept();
        
         log.info("Origin relationships:\n" + formatLinePerListElement(originRels));
         
        Get.taxonomyService().getTaxonomyChildSequences(IsaacMetadataAuxiliaryBinding.HEALTH_CONCEPT
                .getConceptSequence()).forEach((childSequence) -> {log.info("Child: " + Get.conceptDescriptionText(childSequence));});
        Get.taxonomyService().getTaxonomyChildSequences(IsaacMetadataAuxiliaryBinding.HEALTH_CONCEPT
                .getConceptSequence(), Get.coordinateFactory().createDefaultInferredTaxonomyCoordinate()).forEach((childSequence) -> {
                    log.info("Child with tc:" + Get.conceptDescriptionText(childSequence) + "<" + childSequence + ">");});
        Get.taxonomyService().getTaxonomyChildSequences(IsaacMetadataAuxiliaryBinding.HEALTH_CONCEPT
                .getConceptSequence(), Get.coordinateFactory().createDefaultInferredTaxonomyCoordinate().makeAnalog(State.ACTIVE)).forEach((childSequence) -> {
                    log.info("Child with tc2:" + Get.conceptDescriptionText(childSequence) + "<" + childSequence + ">");});
        List<? extends SememeChronology<? extends RelationshipVersionAdaptor<?>>> destinationRels = healthConcept.getRelationshipListWithConceptAsDestination();
         log.info("Destination relationships:\n" + formatLinePerListElement(destinationRels));
    }
    
    String formatLinePerListElement(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
                list.stream().forEach((element) -> {
            sb.append(element);
            sb.append(",\n ");
        });
        sb.delete(sb.length() - 4, sb.length() -1);
        
        sb.append("]");
        return sb.toString();
    }
    
    private void testExpressions() throws InterruptedException, ExecutionException, IOException
    {
        LogicCoordinate logicCoordinate = LogicCoordinates.getStandardElProfile();
        StampCoordinate stampCoordinate = StampCoordinates.getDevelopmentLatest();
        EditCoordinate editCoordinate = EditCoordinates.getDefaultUserSolorOverlay();
        ClassifierService classifierService = Get.logicService().getClassifierService(stampCoordinate, logicCoordinate, editCoordinate);

        new File("../logic-integration-tests/src/test/resources/expressionSample.txt");
        ExpressionReader.read(new File("src/test/resources/expressionSample.txt")).forEach(parseTree ->
        {
            try
            {
                LogicalExpressionBuilder defBuilder = Get.logicalExpressionBuilderService().getLogicalExpressionBuilder();
                ISAACVisitor visitor = new ISAACVisitor(defBuilder);
                visitor.visit(parseTree);
                LogicalExpression expression = defBuilder.build();
                System.out.println("LOINC EXPRESSION SERVICE> Created definition:\n\n " + expression);
                
                int newSequence = classifierService.getConceptSequenceForExpression((LogicalExpressionOchreImpl) expression, editCoordinate).get();
                System.out.println("LOINC EXPRESSION SERVICE> New sequence: " + newSequence);
            }
            catch (Exception e)
            {
                LogManager.getLogger().error("Error processing tree " + parseTree.toStringTree(), e);
                throw new RuntimeException(e);
            }
        });
    
        System.out.println("LOINC EXPRESSION SERVICE> Classifying ...");
        ClassifierResults results = classifierService.classify().get();
        System.out.println("LOINC EXPRESSION SERVICE> Classification results: " + results);

        System.out.println("LOINC EXPRESSION SERVICE> Done.");
    }

}
