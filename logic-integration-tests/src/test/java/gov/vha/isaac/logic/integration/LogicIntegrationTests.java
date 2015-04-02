/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.logic.integration;

import au.csiro.ontology.Ontology;
import au.csiro.ontology.classification.IReasoner;
import au.csiro.ontology.model.Axiom;
import au.csiro.ontology.model.Concept;
import au.csiro.snorocket.core.SnorocketReasoner;
import gov.vha.isaac.cradle.CradleExtensions;
import gov.vha.isaac.cradle.component.ConceptChronicleDataEager;
import gov.vha.isaac.cradle.sequence.SequenceProvider;
import gov.vha.isaac.cradle.taxonomy.CradleTaxonomyProvider;
import gov.vha.isaac.cradle.taxonomy.graph.GraphCollector;
import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.axioms.GraphToAxiomTranslator;
import static gov.vha.isaac.lookup.constants.Constants.CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY;
import gov.vha.isaac.metadata.coordinates.ViewCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.DataTarget;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.ObjectChronicleTaskService;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.tree.TreeNodeVisitData;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeBuilder;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeWithBitSets;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.mahout.math.set.OpenIntHashSet;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.runlevel.RunLevelController;
import org.ihtsdo.otf.lookup.contracts.contracts.ActiveTaskSet;
import org.ihtsdo.otf.tcc.api.contradiction.ContradictionException;
import org.ihtsdo.otf.tcc.api.coordinate.Position;
import org.ihtsdo.otf.tcc.api.coordinate.Status;
import org.ihtsdo.otf.tcc.api.coordinate.ViewCoordinate;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.ihtsdo.otf.tcc.api.metadata.binding.Taxonomies;
import org.ihtsdo.otf.tcc.api.refex.RefexChronicleBI;
import org.ihtsdo.otf.tcc.api.relationship.RelAssertionType;
import org.ihtsdo.otf.tcc.api.spec.ConceptSpec;
import org.ihtsdo.otf.tcc.lookup.Hk2Looker;
import org.ihtsdo.otf.tcc.model.cc.concept.ConceptChronicle;
import org.ihtsdo.otf.tcc.model.cc.concept.ConceptVersion;
import org.ihtsdo.otf.tcc.model.cc.refex.type_array_of_bytearray.ArrayOfByteArrayMember;
import org.ihtsdo.otf.tcc.model.cc.refex.type_array_of_bytearray.ArrayOfByteArrayMemberVersion;
import org.ihtsdo.otf.tcc.model.cc.refex.type_array_of_bytearray.ArrayOfByteArrayRevision;
import org.ihtsdo.otf.tcc.model.cc.termstore.PersistentStoreI;
import org.ihtsdo.otf.tcc.model.version.Stamp;
import org.jvnet.testing.hk2testng.HK2;
import org.reactfx.EventStreams;
import org.reactfx.Subscription;
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
    private static SequenceProvider sequenceProvider;
    private static TaxonomyService taxonomyProvider;

    /**
     * @return the sequenceProvider
     */
    public static SequenceProvider getSequenceService() {
        if (sequenceProvider == null) {
            sequenceProvider = LookupService.getService(SequenceProvider.class);
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
    Subscription tickSubscription;
    RunLevelController runLevelController;
    private boolean dbExists = false;

    @BeforeSuite
    public void setUpSuite() throws Exception {
        log.info("oneTimeSetUp");
        JFXPanel panel = new JFXPanel();
        System.setProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY, "target/object-chronicles");

        java.nio.file.Path dbFolderPath = Paths.get(System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY));
        dbExists = dbFolderPath.toFile().exists();
        System.out.println("termstore folder path: " + dbFolderPath.toFile().exists());

        runLevelController = Hk2Looker.get().getService(RunLevelController.class);

        log.info("going to run level 1");
        runLevelController.proceedTo(1);
        log.info("going to run level 2");
        runLevelController.proceedTo(2);
        tickSubscription = EventStreams.ticks(Duration.ofSeconds(10))
                .subscribe(tick -> {
                    Set<Task> taskSet = Hk2Looker.get().getService(ActiveTaskSet.class).get();
                    taskSet.stream().forEach((task) -> {
                        double percentProgress = task.getProgress() * 100;
                        if (percentProgress < 0) {
                            percentProgress = 0;
                        }
                        log.printf(org.apache.logging.log4j.Level.INFO, "%n    %s%n    %s%n    %.1f%% complete",
                                task.getTitle(), task.getMessage(), percentProgress);
                    });
                });
    }

    @AfterSuite
    public void tearDownSuite() throws Exception {
        log.info("oneTimeTearDown");
        log.info("going to run level 1");
        runLevelController.proceedTo(1);
        log.info("going to run level 0");
        runLevelController.proceedTo(0);
        tickSubscription.unsubscribe();
    }

    @Test
    public void testLoad() throws Exception {

        log.info("  Testing load...");
        ObjectChronicleTaskService tts = Hk2Looker.get().getService(ObjectChronicleTaskService.class);
        PersistentStoreI ps = Hk2Looker.get().getService(PersistentStoreI.class);

        String mapDbFolder = System.getProperty(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY);
        if (mapDbFolder == null || mapDbFolder.isEmpty()) {
            throw new IllegalStateException(CHRONICLE_COLLECTIONS_ROOT_LOCATION_PROPERTY + " has not been set.");
        }

        CradleExtensions mapDbService = (CradleExtensions) ps;

        if (!dbExists) {
            loadDatabase(tts, mapDbService);
        }
        HashTreeWithBitSets g = makeGraph(mapDbService);

        if (!dbExists) {
            makeDlGraph(mapDbService, g);
        }

        classify(mapDbService, g);
        
        exportDatabase(tts);
        exportLogicGraphDatabase(tts);
    }
    
    private void exportDatabase(ObjectChronicleTaskService tts) throws InterruptedException, ExecutionException {
        Path logicExportFile = Paths.get("target/logicExportFile.econ");
        Instant start = Instant.now();
        Task<Integer> exportTask = tts.startExportTask(logicExportFile);
        Hk2Looker.get().getService(ActiveTaskSet.class).get().add(exportTask);
        int conceptCount = exportTask.get();
        Hk2Looker.get().getService(ActiveTaskSet.class).get().remove(exportTask);
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
        Hk2Looker.get().getService(ActiveTaskSet.class).get().add(exportTask);
        int conceptCount = exportTask.get();
        Hk2Looker.get().getService(ActiveTaskSet.class).get().remove(exportTask);
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
        Hk2Looker.get().getService(ActiveTaskSet.class).get().add(loadTask);
        int conceptCount = loadTask.get();
        Hk2Looker.get().getService(ActiveTaskSet.class).get().remove(loadTask);
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

    private HashTreeWithBitSets makeGraph(CradleExtensions cradle) throws IOException {
        log.info("  Start to make graph.");
        Instant collectStart = Instant.now();
        IntStream conceptSequenceStream = getSequenceService().getParallelConceptSequenceStream();
        log.info("  conceptSequenceStream count 1:" + conceptSequenceStream.count());
        conceptSequenceStream = getSequenceService().getParallelConceptSequenceStream();
        log.info("  conceptSequenceStream count 2:" + conceptSequenceStream.count());
        conceptSequenceStream = getSequenceService().getParallelConceptSequenceStream();
        log.info("  conceptSequenceStream distinct count :" + conceptSequenceStream.distinct().count());
        conceptSequenceStream = getSequenceService().getConceptSequenceStream();
        GraphCollector collector = new GraphCollector(((CradleTaxonomyProvider)getTaxonomyService()).getOriginDestinationTaxonomyRecords(),
                ViewCoordinates.getDevelopmentStatedLatest());
        HashTreeBuilder graphBuilder = conceptSequenceStream.collect(
                HashTreeBuilder::new,
                collector,
                collector);
        HashTreeWithBitSets resultGraph = graphBuilder.getSimpleDirectedGraphGraph();
        Instant collectEnd = Instant.now();
        Duration collectDuration = Duration.between(collectStart, collectEnd);
        log.info("  Finished making graph: " + resultGraph);
        log.info("  Generation duration: " + collectDuration);
        return resultGraph;
    }

    private void classify(CradleExtensions mapDbService, HashTreeWithBitSets g) throws IOException, ContradictionException, PropertyVetoException {
        log.info("  Start classify.");
        ConceptSequenceSet conceptsToClassify = g.getDescendentSequenceSet(getSequenceService().getConceptSequence(Taxonomies.SNOMED.getNid()));
        log.info("   Concepts to classify: " + conceptsToClassify.size());

        AtomicInteger logicGraphMembers = new AtomicInteger();
        AtomicInteger rejectedLogicGraphMembers = new AtomicInteger();
        Instant classifyStart = Instant.now();
        log.info("     Start axiom construction.");
        Instant axiomConstructionStart = Instant.now();

        ViewCoordinate viewCoordinate = ViewCoordinates.getDevelopmentStatedLatest();
        ConceptSpec tempDefinitionRefex = new ConceptSpec("Defining relationship", "e607218d-7027-3058-ae5f-0d4ccd148fd0");
        int tempDefinitionRefexNid = tempDefinitionRefex.getNid();
        ConceptChronicle chronicle = ConceptChronicle.get(tempDefinitionRefexNid);
        ConceptVersion concept = chronicle.getVersion(viewCoordinate);
        GraphToAxiomTranslator graphToAxiomTranslator = new GraphToAxiomTranslator();

        // get refset members as a parallel stream?
        for (RefexChronicleBI<?> sememe : concept.getRefsetMembers()) {
            if (conceptsToClassify.contains(getSequenceService().getConceptSequence(sememe.getReferencedComponentNid()))) {
                ArrayOfByteArrayMember logicSememe = (ArrayOfByteArrayMember) sememe;
                ArrayOfByteArrayMemberVersion logicGraphSememe = (ArrayOfByteArrayMemberVersion) logicSememe.getVersion(viewCoordinate);
                graphToAxiomTranslator.translate(logicGraphSememe);
                logicGraphMembers.incrementAndGet();
            } else {
                rejectedLogicGraphMembers.incrementAndGet();
            }
        }
        Instant axiomConstructionEnd = Instant.now();
        Duration axiomConstructionDuration = Duration.between(axiomConstructionStart, axiomConstructionEnd);
        log.info("     Finished axiom construction. LogicGraphMembers: " + logicGraphMembers + " rejected members: " + rejectedLogicGraphMembers);
        log.info("     Axiom construction duration: " + axiomConstructionDuration);

        IReasoner r = new SnorocketReasoner();
        log.info("     Start axiom load.");
        Instant axiomLoadStart = Instant.now();
        r.loadAxioms(graphToAxiomTranslator.getAxioms());
        Instant axiomLoadEnd = Instant.now();
        Duration axiomLoadDuration = Duration.between(axiomLoadStart, axiomLoadEnd);
        log.info("     Finished axiom load. ");
        log.info("     Axiom load duration: " + axiomLoadDuration);
        log.info("     Start reasoner classify. ");

        Instant reasonerClassifyStart = Instant.now();
        r = r.classify();
        Instant reasonerClassifyEnd = Instant.now();
        Duration reasonerClassifyDuration = Duration.between(reasonerClassifyStart, reasonerClassifyEnd);
        log.info("     Finished reasoner classify. ");
        log.info("     Reasoner classify duration: " + reasonerClassifyDuration);

        // Get only the taxonomy
        Instant retrieveResultsStart = Instant.now();
        Ontology res = r.getClassifiedOntology();
        Instant retrieveResultsEnd = Instant.now();
        Duration retrieveResultsDuration = Duration.between(retrieveResultsStart, retrieveResultsEnd);

        log.info("     Finished retrieve results. ");
        log.info("     Retrieve results duration: " + retrieveResultsDuration);
        Instant classifyEnd = Instant.now();
        Duration classifyDuration = Duration.between(classifyStart, classifyEnd);
        log.info("  Finished classify. LogicGraphMembers: " + logicGraphMembers + " rejected members: " + rejectedLogicGraphMembers);
        log.info("  Classify duration: " + classifyDuration);
        log.info("\n  Start incremental test.");

        UUID appendicitsUuid = UUID.fromString("55450fab-6786-394d-89f9-a0fd44bd7e7e");
        int appendicitisSequence = getSequenceService().getConceptSequence(mapDbService.getNidForUuids(appendicitsUuid));
        java.util.Optional<Concept> appendicitis = graphToAxiomTranslator.getConceptFromSequence(appendicitisSequence);
        if (appendicitis.isPresent()) {
            Instant incrementalStart = Instant.now();
            String newId = UUID.randomUUID().toString();
            Concept specialAppendicitis = au.csiro.ontology.Factory.createNamedConcept(newId);

            HashSet<Axiom> newAxioms = new HashSet<>();
            Axiom incremental1 = au.csiro.ontology.Factory.createConceptInclusion(specialAppendicitis, appendicitis.get());
            newAxioms.add(incremental1);
            r.loadAxioms(newAxioms);
            r.classify();
            res = r.getClassifiedOntology();
            Instant incrementalEnd = Instant.now();
            Duration incrementalClassifyDuration = Duration.between(incrementalStart, incrementalEnd);
            log.info("  Incremental classify duration: " + incrementalClassifyDuration);
        } else {
            throw new IllegalStateException("Can't find concept for appendicitis: " + appendicitisSequence);
        }

    }

    private void makeDlGraph(CradleExtensions mapDbService, HashTreeWithBitSets g) throws IOException {
        log.info("  Start makeDlGraph.");
        Instant collectStart = Instant.now();
        ViewCoordinate developmentStatedLatest = ViewCoordinates.getDevelopmentStatedLatest();
        AtomicInteger logicGraphMembers = new AtomicInteger();
        AtomicInteger logicGraphVersions = new AtomicInteger();
        AtomicInteger maxGraphVersionsPerMember = new AtomicInteger();

        // Figure out why role group is missing from current database, or is it a problem with the map...
        ConceptSpec tempRoleGroup = new ConceptSpec("Linkage concept", "1a3399bc-e6b5-3dea-8058-4e08012ff00f");
        ConceptSpec roleRoot = new ConceptSpec("Concept model attribute (attribute)", "6155818b-09ed-388e-82ce-caa143423e99");
        g.getDescendentSequenceSet(getSequenceService().getConceptSequence(roleRoot.getNid()));

        
        
        ConceptSpec tempDefinitionRefex = new ConceptSpec("Defining relationship", "e607218d-7027-3058-ae5f-0d4ccd148fd0");
        int tempDefinitionRefexNid = tempDefinitionRefex.getNid();

        ConceptSpec tempModule = new ConceptSpec("Module", "40d1c869-b509-32f8-b735-836eac577a67");
        int tempModuleNid = tempModule.getNid();

        ConceptSpec author = new ConceptSpec("user", "f7495b58-6630-3499-a44e-2052b5fcf06c");
        int tempAuthorNid = author.getNid();

        ConceptSequenceSet roleConceptSequences = new ConceptSequenceSet();
        ConceptSequenceSet roleConceptSequencesArray = g.getDescendentSequenceSet(getSequenceService().getConceptSequence(roleRoot.getNid()));
        roleConceptSequencesArray.stream().forEach(roleConceptSequence -> {
            roleConceptSequences.add(roleConceptSequence);
        });
        ConceptSequenceSet featureConceptSequences = new ConceptSequenceSet(); //empty set for now.

        ConceptSequenceSet neverRoleGroupConceptSequences = new ConceptSequenceSet();
        neverRoleGroupConceptSequences.add(getSequenceService().getConceptSequence(Snomed.PART_OF.getNid()));
        neverRoleGroupConceptSequences.add(getSequenceService().getConceptSequence(Snomed.LATERALITY.getNid()));
        neverRoleGroupConceptSequences.add(getSequenceService().getConceptSequence(Snomed.HAS_ACTIVE_INGREDIENT.getNid()));
        neverRoleGroupConceptSequences.add(getSequenceService().getConceptSequence(Snomed.HAS_DOSE_FORM.getNid()));

        int roleGroupNid = tempRoleGroup.getNid();

        AtomicInteger maxGraphSize = new AtomicInteger(0);

        mapDbService.getParallelConceptDataEagerStream().forEach((ConceptChronicleDataEager conceptChronicleDataEager) -> {
            try {
                ConceptChronicle conceptChronicle = ConceptChronicle.get(conceptChronicleDataEager.getNid(), conceptChronicleDataEager);

                logicGraphMembers.incrementAndGet();
                ArrayOfByteArrayMember logicGraphMember = null;
                LogicGraph lastLogicGraph = null;
                for (Position position : conceptChronicle.getPositions()) {
                    ViewCoordinate vcForPosition = new ViewCoordinate(UUID.randomUUID(),
                            "vc for position", developmentStatedLatest);
                    vcForPosition.setViewPosition(position);
                    ConceptVersion conceptVersion = conceptChronicle.getVersion(vcForPosition);
                    if (conceptVersion.isActive()) {
                        try {

                            LogicGraph logicGraph = new LogicGraph(conceptVersion,
                                    roleConceptSequences,
                                    featureConceptSequences,
                                    neverRoleGroupConceptSequences,
                                    roleGroupNid);
                            if (!logicGraph.isMeaningful()) {
                                vcForPosition.setRelationshipAssertionType(RelAssertionType.INFERRED);
                                conceptVersion = conceptChronicle.getVersion(vcForPosition);
                                logicGraph = new LogicGraph(conceptVersion,
                                        roleConceptSequences,
                                        featureConceptSequences,
                                        neverRoleGroupConceptSequences,
                                        roleGroupNid);
                            }
                            if (logicGraph.isMeaningful()) {
                                byte[][] logicGraphBytes = logicGraph.pack(DataTarget.INTERNAL);
                                int graphNodeCount = logicGraphBytes.length;
                                if (graphNodeCount > maxGraphSize.get()) {
                                    StringBuilder builder = new StringBuilder();
                                    printGraph(builder, "\n Make dl graph for: ", conceptChronicle, maxGraphSize, graphNodeCount, logicGraph);
                                    System.out.println(builder.toString());
                                }

                                if (logicGraphMember == null) {
                                    logicGraphVersions.incrementAndGet();
                                    logicGraphMember = new ArrayOfByteArrayMember();
                                    UUID primordialUuid = UUID.randomUUID();
                                    logicGraphMember.setPrimordialUuid(primordialUuid);
                                    logicGraphMember.setNid(mapDbService.getNidForUuids(primordialUuid));
                                    logicGraphMember.setArrayOfByteArray(logicGraphBytes);
                                    logicGraphMember.setAssemblageNid(tempDefinitionRefexNid);
                                    logicGraphMember.setReferencedComponentNid(conceptChronicle.getNid());
                                    logicGraphMember.setSTAMP(mapDbService.getStamp(
                                            Status.ACTIVE, vcForPosition.getViewPosition().getTime(),
                                            tempAuthorNid,
                                            tempModuleNid,
                                            position.getPath().getConceptNid()));
                                    logicGraphMember.enclosingConceptNid = tempDefinitionRefexNid;
                                    mapDbService.setConceptNidForNid(tempDefinitionRefexNid, logicGraphMember.getNid());

                                } else if (!logicGraph.equals(lastLogicGraph)) {
                                    logicGraphVersions.incrementAndGet();
                                    ArrayOfByteArrayRevision lgr = new ArrayOfByteArrayRevision();
                                    lgr.setArrayOfByteArray(logicGraphBytes);
                                    lgr.setStatusAtPosition(
                                            Status.ACTIVE,
                                            position.getTime(),
                                            tempAuthorNid,
                                            tempModuleNid,
                                            position.getPath().getConceptNid());
                                    logicGraphMember.addRevision(lgr);
                                }
                                lastLogicGraph = logicGraph;
                            }

                        } catch (IllegalStateException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                if (logicGraphMember != null) {
                    mapDbService.writeRefex(logicGraphMember);
                    if (logicGraphMember.revisions != null) {
                        int versionCount = logicGraphMember.revisions.size() + 1;
                        if (versionCount > maxGraphVersionsPerMember.get()) {
                            maxGraphVersionsPerMember.set(versionCount);
                            StringBuilder builder = new StringBuilder();
                            builder.append("Encountered logic definition with ").append(versionCount).append(" versions:\n\n");
                            int version = 0;
                            LogicGraph previousVersion = null;
                            for (ArrayOfByteArrayMemberVersion lgmv : logicGraphMember.getVersions()) {
                                LogicGraph lg = new LogicGraph(lgmv.getArrayOfByteArray(), DataSource.INTERNAL,
                                getSequenceService().getConceptSequence(lgmv.getReferencedComponentNid()));
                                printGraph(builder, "Version " + version++ + " stamp: " + Stamp.stampFromIntStamp(lgmv.getStamp()).toString() + "\n ",
                                        conceptChronicle, maxGraphSize, lg.getNodeCount(), lg);
                                if (previousVersion != null) {
                                    int[] solution1 = lg.maximalCommonSubgraph(previousVersion);
                                    int[] solution2 = previousVersion.maximalCommonSubgraph(lg);
                                    builder.append("Solution this to previous: [");
                                    for (int i = 0; i < solution1.length; i++) {
                                        if (solution1[i] != -1) {
                                            builder.append("(");
                                            builder.append(i);
                                            builder.append("->");
                                            builder.append(solution1[i]);
                                            builder.append(")");
                                        }
                                    }
                                    builder.append("]\nSolution previous to this: [");
                                    for (int i = 0; i < solution2.length; i++) {
                                        if (solution2[i] != -1) {
                                            builder.append("(");
                                            builder.append(i);
                                            builder.append("<-");
                                            builder.append(solution2[i]);
                                            builder.append(")");
                                        }
                                    }
                                    builder.append("]\n");
                                }
                                previousVersion = lg;
                            }
                            System.out.println(builder.toString());

                        }
                    }
                }
            } catch (PropertyVetoException | IOException | ContradictionException e) {
                throw new RuntimeException(e);
            }
        });
        Instant collectEnd = Instant.now();
        Duration collectDuration = Duration.between(collectStart, collectEnd);
        log.info("  Finished makeDlGraph. Member count: " + logicGraphMembers
                + " Version count: " + logicGraphVersions);
        log.info("  Collection duration: " + collectDuration);
    }

    private void printGraph(StringBuilder builder, String prefix, ConceptChronicle chronicle, AtomicInteger maxGraphSize, int graphNodeCount, LogicGraph logicGraph) {
        builder.append(prefix).append(chronicle.toString());
        builder.append("\n uuid: ");
        builder.append(chronicle.getPrimordialUuid());
        builder.append("\nnodes: ");
        builder.append(logicGraph.getNodeCount());
        builder.append("\n");
        maxGraphSize.set(Math.max(graphNodeCount, maxGraphSize.get()));
        logicGraph.processDepthFirst((Node node, TreeNodeVisitData graphVisitData) -> {
            for (int i = 0; i < graphVisitData.getDistance(node.getNodeIndex()); i++) {
                builder.append("    ");
            }
            builder.append(node);
            builder.append("\n");
        });
        builder.append(" \n\n");
    }

}
