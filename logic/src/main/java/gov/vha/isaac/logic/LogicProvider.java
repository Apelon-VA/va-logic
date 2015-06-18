/*
 * Copyright 2015 kec.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.vha.isaac.logic;

import au.csiro.ontology.Ontology;
import gov.vha.isaac.cradle.CradleExtensions;
import gov.vha.isaac.cradle.component.ConceptChronicleDataEager;
import gov.vha.isaac.cradle.taxonomy.CradleTaxonomyProvider;
import gov.vha.isaac.cradle.taxonomy.graph.GraphCollector;
import gov.vha.isaac.csiro.classify.ClassifierData;
import gov.vha.isaac.metadata.coordinates.EditCoordinates;
import gov.vha.isaac.metadata.coordinates.LogicCoordinates;
import gov.vha.isaac.metadata.coordinates.ViewCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.DataTarget;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.State;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.chronicle.LatestVersion;
import gov.vha.isaac.ochre.api.classifier.ClassifierResults;
import gov.vha.isaac.ochre.api.commit.ChangeCheckerMode;
import gov.vha.isaac.ochre.api.commit.CommitService;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilder;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilderService;
import gov.vha.isaac.ochre.api.component.concept.ConceptService;
import gov.vha.isaac.ochre.api.component.concept.ConceptServiceManagerI;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampPosition;
import static gov.vha.isaac.ochre.api.coordinate.TaxonomyType.INFERRED;
import gov.vha.isaac.ochre.api.memory.MemoryConfigurations;
import gov.vha.isaac.ochre.api.memory.MemoryManagementService;
import gov.vha.isaac.ochre.api.component.sememe.SememeService;
import gov.vha.isaac.ochre.api.component.sememe.SememeSnapshotService;
import gov.vha.isaac.ochre.api.tree.TreeNodeVisitData;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeBuilder;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeWithBitSets;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import gov.vha.isaac.ochre.collections.NidSet;
import gov.vha.isaac.ochre.api.component.sememe.SememeType;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import gov.vha.isaac.ochre.collections.SememeSequenceSet;
import gov.vha.isaac.ochre.model.coordinate.StampPositionImpl;
import gov.vha.isaac.ochre.model.logic.LogicExpressionOchreImpl;
import gov.vha.isaac.ochre.model.sememe.SememeChronologyImpl;
import gov.vha.isaac.ochre.model.sememe.version.LogicGraphSememeImpl;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.concurrent.Task;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.runlevel.RunLevel;
import org.ihtsdo.otf.tcc.api.concept.ConceptVersionBI;
import org.ihtsdo.otf.tcc.api.contradiction.ContradictionException;
import org.ihtsdo.otf.tcc.api.coordinate.Position;
import org.ihtsdo.otf.tcc.api.coordinate.ViewCoordinate;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.ihtsdo.otf.tcc.api.relationship.RelAssertionType;
import org.ihtsdo.otf.tcc.api.spec.ConceptSpec;
import org.ihtsdo.otf.tcc.model.cc.concept.ConceptChronicle;
import org.ihtsdo.otf.tcc.model.cc.concept.ConceptVersion;
import org.ihtsdo.otf.tcc.model.version.Stamp;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author kec
 */
@Service(name = "logic provider")
@RunLevel(value = 2)
public class LogicProvider implements LogicService {

    private static boolean VERBOSE = false;

    private static final Logger log = LogManager.getLogger();
    private static IdentifierService identifierService;
    private static TaxonomyService taxonomyService;
    private static SememeService sememeService;
    private static CommitService commitService;
    private static ConceptService conceptService;

    public static ConceptService getConceptService() {
        if (conceptService == null) {
            conceptService = LookupService.getService(ConceptServiceManagerI.class).get();
        }
        return conceptService;
    }

    public static CommitService getCommitService() {
        if (commitService == null) {
            commitService = LookupService.getService(CommitService.class);
        }
        return commitService;
    }

    public static IdentifierService getIdentifierService() {
        if (identifierService == null) {
            identifierService = LookupService.getService(IdentifierService.class);
        }
        return identifierService;
    }

    /**
     * @return the taxonomyService
     */
    public static TaxonomyService getTaxonomyService() {
        if (taxonomyService == null) {
            taxonomyService = LookupService.getService(TaxonomyService.class);
        }
        return taxonomyService;
    }

    public static SememeService getSememeService() {
        if (sememeService == null) {
            sememeService = LookupService.getService(SememeService.class);
        }
        return sememeService;
    }

    private LogicServiceChangeListener logicServiceChangeListener; // strong reference to prevent garbage collection

    private LogicProvider() {
        //For HK2
        log.info("logic provider constructed");
    }

    @PostConstruct
    private void startMe() throws IOException {
        System.out.println("Starting LogicProvider.");
        logicServiceChangeListener = new LogicServiceChangeListener(
                LogicCoordinates.getStandardElProfile(), this);
        getCommitService().addChangeListener(logicServiceChangeListener);
    }

    @PreDestroy
    private void stopMe() throws IOException {
        System.out.println("Stopping LogicProvider.");
    }

    @Override
    public void initialize(LogicCoordinate logicCoordinate) {
        CradleExtensions cradleService = LookupService.getService(CradleExtensions.class);

        try {
            log.info("  Start to make graph.");
            Instant collectStart = Instant.now();
            HashTreeWithBitSets statedTree = getStatedTaxonomyGraph();
            Instant collectEnd = Instant.now();
            Duration collectDuration = Duration.between(collectStart, collectEnd);
            log.info("  Finished making graph: " + statedTree);
            log.info("  Generation duration: " + collectDuration);

            ConceptSpec roleGroup = IsaacMetadataAuxiliaryBinding.ROLE_GROUP;
            ConceptSpec roleRoot = IsaacMetadataAuxiliaryBinding.ROLE;
            ConceptSpec featureRoot = IsaacMetadataAuxiliaryBinding.FEATURE;

            EditCoordinate ec = EditCoordinates.getDefaultUserVeteransAdministrationExtension();

            ConceptSequenceSet roleConceptSequences = statedTree.getDescendentSequenceSet(getIdentifierService().getConceptSequence(roleRoot.getNid()));

            ConceptSequenceSet featureConceptSequences = statedTree.getDescendentSequenceSet(getIdentifierService().getConceptSequence(featureRoot.getNid()));

            ConceptSequenceSet neverRoleGroupConceptSequences = new ConceptSequenceSet();
            neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.PART_OF.getNid()));
            neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.LATERALITY.getNid()));
            neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.HAS_ACTIVE_INGREDIENT.getNid()));
            neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.HAS_DOSE_FORM.getNid()));

            //------------
            makeGraphs(cradleService,
                    ViewCoordinates.getDevelopmentStatedLatest(),
                    roleGroup,
                    logicCoordinate.getStatedAssemblageSequence(),
                    ec,
                    roleConceptSequences,
                    featureConceptSequences,
                    neverRoleGroupConceptSequences);
            makeGraphs(cradleService,
                    ViewCoordinates.getDevelopmentInferredLatest(),
                    roleGroup,
                    logicCoordinate.getInferredAssemblageSequence(),
                    ec,
                    roleConceptSequences,
                    featureConceptSequences,
                    neverRoleGroupConceptSequences);
        } catch (IOException ex) {
            log.error(ex.getLocalizedMessage(), ex);
        }
    }

    private void makeGraphs(
            CradleExtensions cradleService,
            ViewCoordinate viewForLogicGraph,
            ConceptSpec roleGroup,
            int assemblageSequence,
            EditCoordinate ec,
            ConceptSequenceSet roleConceptSequences,
            ConceptSequenceSet featureConceptSequences,
            ConceptSequenceSet neverRoleGroupConceptSequences) {
        log.info("  Start makeDlGraph: " + viewForLogicGraph.getRelationshipAssertionType());
        int definitionAssemblageNid = getIdentifierService().getConceptNid(assemblageSequence);
        Instant collectStart = Instant.now();
        Instant collectEnd;
        Duration collectDuration;
        AtomicInteger logicGraphMembers = new AtomicInteger();
        AtomicInteger logicGraphVersions = new AtomicInteger();
        AtomicInteger maxGraphVersionsPerMember = new AtomicInteger();
        int roleGroupNid = roleGroup.getNid();
        AtomicInteger maxGraphSize = new AtomicInteger(0);
        cradleService.getParallelConceptDataEagerStream().forEach((ConceptChronicleDataEager conceptChronicleDataEager) -> {
            try {
                ConceptChronicle conceptChronicle = ConceptChronicle.get(conceptChronicleDataEager.getNid(), conceptChronicleDataEager);

                logicGraphMembers.incrementAndGet();
                SememeChronologyImpl<LogicGraphSememeImpl> logicGraphChronicle = null;
                LogicGraph lastLogicGraph = null;
                for (Position position : conceptChronicle.getPositions()) {
                    ViewCoordinate vcForPosition = new ViewCoordinate(UUID.randomUUID(), "vc for position", viewForLogicGraph);
                    vcForPosition.setViewPosition(position);
                    Optional<ConceptVersionBI> conceptVersion = conceptChronicle.getVersion(vcForPosition);
                    if (conceptVersion.isPresent() && conceptVersion.get().isActive()) {
                        try {

                            LogicGraph logicGraph = new LogicGraph(conceptVersion.get(),
                                    roleConceptSequences,
                                    featureConceptSequences,
                                    neverRoleGroupConceptSequences,
                                    roleGroupNid);
                            if (!logicGraph.isMeaningful()) {
                                vcForPosition.setRelationshipAssertionType(RelAssertionType.INFERRED);
                                conceptVersion = conceptChronicle.getVersion(vcForPosition);
                                logicGraph = new LogicGraph(conceptVersion.get(),
                                        roleConceptSequences,
                                        featureConceptSequences,
                                        neverRoleGroupConceptSequences,
                                        roleGroupNid);
                            }
                            if (logicGraph.isMeaningful()) {
                                byte[][] logicGraphBytes = logicGraph.pack(DataTarget.INTERNAL);
                                int graphNodeCount = logicGraphBytes.length;
                                if (VERBOSE) {
                                    printIfMoreNodes(graphNodeCount, maxGraphSize, conceptChronicle, logicGraph);
                                }

                                if (logicGraphChronicle == null) {
                                    logicGraphVersions.incrementAndGet();
                                    UUID primordialUuid = UUID.randomUUID();
                                    int nid = cradleService.getNidForUuids(primordialUuid);
                                    int containerSequence = getIdentifierService().getSememeSequence(nid);
                                    logicGraphChronicle = new SememeChronologyImpl<>(
                                            SememeType.LOGIC_GRAPH,
                                            primordialUuid,
                                            nid,
                                            assemblageSequence,
                                            conceptChronicle.getNid(),
                                            containerSequence
                                    );

                                    int stampSequence = getCommitService().getStampSequence(State.ACTIVE, position.getTime(),
                                            ec.getAuthorSequence(), ec.getModuleSequence(),
                                            ec.getPathSequence());

                                    LogicGraphSememeImpl mutable = logicGraphChronicle.createMutableVersion(
                                            LogicGraphSememeImpl.class, stampSequence);

                                    mutable.setGraphData(logicGraphBytes);
                                    cradleService.setConceptNidForNid(definitionAssemblageNid, logicGraphChronicle.getNid());

                                } else if (!logicGraph.equals(lastLogicGraph)) {
                                    logicGraphVersions.incrementAndGet();
                                    int stampSequence = getCommitService().getStampSequence(State.ACTIVE, position.getTime(),
                                            ec.getAuthorSequence(), ec.getModuleSequence(),
                                            ec.getPathSequence());
                                    LogicGraphSememeImpl mutable = logicGraphChronicle.createMutableVersion(
                                            LogicGraphSememeImpl.class, stampSequence);
                                    mutable.setGraphData(logicGraphBytes);
                                }
                                lastLogicGraph = logicGraph;
                            }

                        } catch (IllegalStateException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
                if (logicGraphChronicle != null) {
                    getSememeService().writeSememe(logicGraphChronicle);
                    if (VERBOSE) {
                        printIfMoreRevisions(logicGraphChronicle, maxGraphVersionsPerMember, conceptChronicle, maxGraphSize);
                    }
                }
            } catch (IOException | ContradictionException e) {
                throw new RuntimeException(e);
            }
        });
        collectEnd = Instant.now();
        collectDuration = Duration.between(collectStart, collectEnd);
        log.info("  Finished makeDlGraph. Member count: " + logicGraphMembers
                + " Version count: " + logicGraphVersions);
        log.info("  Collection duration: " + collectDuration);
    }

    @Override
    public LogicGraph createLogicGraph(ConceptVersion conceptVersion) {
        HashTreeWithBitSets tree;
        if (conceptVersion.getViewCoordinate().getTaxonomyType().equals(INFERRED)) {
            tree = getStatedTaxonomyGraph();

        } else {
            tree = getStatedTaxonomyGraph();

        }
        ConceptSpec roleGroup = IsaacMetadataAuxiliaryBinding.ROLE_GROUP;
        ConceptSpec roleRoot = IsaacMetadataAuxiliaryBinding.ROLE;
        ConceptSpec featureRoot = IsaacMetadataAuxiliaryBinding.FEATURE;

        EditCoordinate ec = EditCoordinates.getDefaultUserVeteransAdministrationExtension();

        ConceptSequenceSet roleConceptSequences = tree.getDescendentSequenceSet(getIdentifierService().getConceptSequence(roleRoot.getNid()));

        ConceptSequenceSet featureConceptSequences = tree.getDescendentSequenceSet(getIdentifierService().getConceptSequence(featureRoot.getNid()));

        ConceptSequenceSet neverRoleGroupConceptSequences = new ConceptSequenceSet();
        neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.PART_OF.getNid()));
        neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.LATERALITY.getNid()));
        neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.HAS_ACTIVE_INGREDIENT.getNid()));
        neverRoleGroupConceptSequences.add(getIdentifierService().getConceptSequence(Snomed.HAS_DOSE_FORM.getNid()));

        try {
            return new LogicGraph(conceptVersion,
                    roleConceptSequences,
                    featureConceptSequences,
                    neverRoleGroupConceptSequences,
                    roleGroup.getNid());
        } catch (IOException | ContradictionException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void printIfMoreNodes(int graphNodeCount, AtomicInteger maxGraphSize, ConceptChronicle conceptChronicle, LogicGraph logicGraph) {
        if (graphNodeCount > maxGraphSize.get()) {
            StringBuilder builder = new StringBuilder();
            printGraph(builder, "Make dl graph for: ", conceptChronicle, maxGraphSize, graphNodeCount, logicGraph);
            System.out.println(builder.toString());
        }
    }

    public void printIfMoreRevisions(SememeChronologyImpl<LogicGraphSememeImpl> logicGraphMember, AtomicInteger maxGraphVersionsPerMember, ConceptChronicle conceptChronicle, AtomicInteger maxGraphSize) {
        if (logicGraphMember.getVersionList() != null) {
            Collection<LogicGraphSememeImpl> versions = (Collection<LogicGraphSememeImpl>) logicGraphMember.getVersionList();
            int versionCount = versions.size();
            if (versionCount > maxGraphVersionsPerMember.get()) {
                maxGraphVersionsPerMember.set(versionCount);
                StringBuilder builder = new StringBuilder();
                builder.append("Encountered logic definition with ").append(versionCount).append(" versions:\n\n");
                int version = 0;
                LogicGraph previousVersion = null;
                for (LogicGraphSememeImpl lgmv : versions) {
                    LogicGraph lg = new LogicGraph(lgmv.getGraphData(), DataSource.INTERNAL,
                            getIdentifierService().getConceptSequence(logicGraphMember.getReferencedComponentNid()));
                    printGraph(builder, "Version " + version++ + " stamp: " + Stamp.stampFromIntStamp(lgmv.getStampSequence()).toString() + "\n ",
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

    /*
    @Override
    public ClassifierResults fullClassification(StampCoordinate stampCoordinate,
            LogicCoordinate logicCoordinate, EditCoordinate editCoordinate) {
        assert logicCoordinate.getClassifierSequence()
                == editCoordinate.getAuthorSequence() :
                "classifier sequence: " + logicCoordinate.getClassifierSequence()
                + " author sequence: " + editCoordinate.getAuthorSequence();
        log.info("  Start classify.");
        AtomicInteger logicGraphMembers = new AtomicInteger();
        Instant classifyStart = Instant.now();
        log.info("     Start axiom construction.");
        Instant axiomConstructionStart = Instant.now();
        ClassifierData cd = ClassifierData.get(stampCoordinate, logicCoordinate);
        log.info("     classifier data before: \n" + cd);
        processAllStatedAxioms(stampCoordinate, logicCoordinate,
                cd, logicGraphMembers);
        Instant axiomConstructionEnd = Instant.now();
        log.info("     classifier data after: \n" + cd);
        Duration axiomConstructionDuration = Duration.between(axiomConstructionStart, axiomConstructionEnd);
        log.info("     Finished axiom construction. LogicGraphMembers: " + logicGraphMembers);
        log.info("     Axiom construction duration: " + axiomConstructionDuration);
        log.info("     Start axiom load.");
        Instant axiomLoadStart = Instant.now();
        LookupService.getService(MemoryManagementService.class).setMemoryConfiguration(MemoryConfigurations.CLASSIFY);
        cd.loadAxioms();
        Instant axiomLoadEnd = Instant.now();
        Duration axiomLoadDuration = Duration.between(axiomLoadStart, axiomLoadEnd);
        log.info("     Finished axiom load. ");
        log.info("     Axiom load duration: " + axiomLoadDuration);
        log.info("     Start reasoner classify. ");
        Instant reasonerClassifyStart = Instant.now();
        cd.classify();
        Instant reasonerClassifyEnd = Instant.now();
        Duration reasonerClassifyDuration = Duration.between(reasonerClassifyStart, reasonerClassifyEnd);
        log.info("     Finished reasoner classify. ");
        log.info("     Reasoner classify duration: " + reasonerClassifyDuration);
        Instant retrieveResultsStart = Instant.now();
        Ontology res = cd.getClassifiedOntology();
        Instant retrieveResultsEnd = Instant.now();
        Duration retrieveResultsDuration = Duration.between(retrieveResultsStart, retrieveResultsEnd);
        log.info("     Finished retrieve results. ");
        log.info("     Retrieve results duration: " + retrieveResultsDuration);
        ClassifierResults classifierResults = collectResults(res, res.getNodeMap().values());
        Instant classifyEnd = Instant.now();
        Duration classifyDuration = Duration.between(classifyStart, classifyEnd);
        log.info("  Finished classify. LogicGraphMembers: " + logicGraphMembers);
        log.info("  Classify duration: " + classifyDuration);

        return classifierResults;
    }
	*/
    
    @Override
    public Task<ClassifierResults> getFullClassificationTask(StampCoordinate pStampCoordinate,
            LogicCoordinate pLogicCoordinate, EditCoordinate pEditCoordinate) {
    	
    	final StampCoordinate stampCoordinate = pStampCoordinate;
    	final LogicCoordinate logicCoordinate = pLogicCoordinate;
    	final EditCoordinate  editCoordinate  = pEditCoordinate;
    	
    	Task<ClassifierResults> classificationTask = new Task<ClassifierResults>() {

            @Override protected ClassifierResults call() throws Exception {

                assert logicCoordinate.getClassifierSequence() == editCoordinate.getAuthorSequence() :
	                "classifier sequence: " + logicCoordinate.getClassifierSequence()
	                + " author sequence: " + editCoordinate.getAuthorSequence();

	            
                updateMessage("Beginning classification...");

                log.info("  Start classify.");
		        AtomicInteger logicGraphMembers = new AtomicInteger();
		        Instant classifyStart = Instant.now();

		        if (isCancelled()) return null;
		        updateMessage("Performing axion construction...");

                log.info("     Start axiom construction.");
		        Instant axiomConstructionStart = Instant.now();
		        ClassifierData cd = ClassifierData.get(stampCoordinate, logicCoordinate);
		        log.info("     classifier data before: \n" + cd);
		        processAllStatedAxioms(stampCoordinate, logicCoordinate,
		                cd, logicGraphMembers);
		        Instant axiomConstructionEnd = Instant.now();
		        log.info("     classifier data after: \n" + cd);
		        Duration axiomConstructionDuration = Duration.between(axiomConstructionStart, axiomConstructionEnd);
		        log.info("     Finished axiom construction. LogicGraphMembers: " + logicGraphMembers);
		        log.info("     Axiom construction duration: " + axiomConstructionDuration);
		        log.info("     Start axiom load.");

		        if (isCancelled()) return null;
                updateMessage("Performing axion load...");

		        Instant axiomLoadStart = Instant.now();
		        LookupService.getService(MemoryManagementService.class).setMemoryConfiguration(MemoryConfigurations.CLASSIFY);
		        cd.loadAxioms();
		        Instant axiomLoadEnd = Instant.now();
		        Duration axiomLoadDuration = Duration.between(axiomLoadStart, axiomLoadEnd);
		        log.info("     Finished axiom load. ");
		        log.info("     Axiom load duration: " + axiomLoadDuration);
		        log.info("     Start reasoner classify. ");

		        if (isCancelled()) return null;
                updateMessage("Performing reasoner classify...");

		        Instant reasonerClassifyStart = Instant.now();
		        cd.classify();
		        Instant reasonerClassifyEnd = Instant.now();
		        Duration reasonerClassifyDuration = Duration.between(reasonerClassifyStart, reasonerClassifyEnd);
		        log.info("     Finished reasoner classify. ");
		        log.info("     Reasoner classify duration: " + reasonerClassifyDuration);


		        if (isCancelled()) return null;
                updateMessage("Retrieving results...");

                Instant retrieveResultsStart = Instant.now();
		        Ontology res = cd.getClassifiedOntology();
		        Instant retrieveResultsEnd = Instant.now();
		        Duration retrieveResultsDuration = Duration.between(retrieveResultsStart, retrieveResultsEnd);
		        log.info("     Finished retrieve results. ");
		        log.info("     Retrieve results duration: " + retrieveResultsDuration);
		        ClassifierResults classifierResults = collectResults(res, res.getNodeMap().values());
		        Instant classifyEnd = Instant.now();
		        Duration classifyDuration = Duration.between(classifyStart, classifyEnd);
		        log.info("  Finished classify. LogicGraphMembers: " + logicGraphMembers);
		        log.info("  Classify duration: " + classifyDuration);

                updateMessage("Classification complete.");
		        
		        return classifierResults;
            }

            @Override protected void succeeded() {
                super.succeeded();
                updateMessage("Classification complete.");
            }

            @Override protected void cancelled() {
                super.cancelled();
                updateMessage("Classification cancelled.");
            }

            @Override protected void failed() {
                super.failed();
                updateMessage("Classification failed. Check log for details.");
            }
    	
    	};

    	return classificationTask;
    }
    
    
    private ClassifierResults collectResults(Ontology res, Collection<au.csiro.ontology.Node> affectedNodes) {
        ConceptSequenceSet affectedConcepts = new ConceptSequenceSet();
        HashSet<ConceptSequenceSet> equivalentSets = new HashSet<>();
        affectedNodes.forEach((node) -> {
            Set<String> equivalentConcepts = node.getEquivalentConcepts();
            if (node.getEquivalentConcepts().size() > 1) {
                ConceptSequenceSet equivalentSet = new ConceptSequenceSet();
                equivalentSets.add(equivalentSet);
                equivalentConcepts.forEach((conceptSequence) -> {
                    equivalentSet.add(Integer.parseInt(conceptSequence));
                    affectedConcepts.add(Integer.parseInt(conceptSequence));
                });
            } else {
                equivalentConcepts.forEach((conceptSequence) -> {
                    try {
                        affectedConcepts.add(Integer.parseInt(conceptSequence));

                    } catch (NumberFormatException numberFormatException) {
                        if (conceptSequence.equals("_BOTTOM_")
                                || conceptSequence.equals("_TOP_")) {
                            // do nothing. 
                        } else {
                            throw numberFormatException;
                        }
                    }
                });
            }
        });
        return new ClassifierResults(affectedConcepts, equivalentSets);
    }

    @Override
    public ClassifierResults incrementalClassification(StampCoordinate stampCoordinate,
            LogicCoordinate logicCoordinate, EditCoordinate editCoordinate,
            ConceptSequenceSet newConcepts) {
        assert logicCoordinate.getClassifierSequence()
                == editCoordinate.getAuthorSequence() :
                "classifier sequence: " + logicCoordinate.getClassifierSequence()
                + " author sequence: " + editCoordinate.getAuthorSequence();
        log.info("Start incremental test.");
        log.info("  Start to make graph for classification.");
        log.info("  Start classify.");
        LookupService.getService(MemoryManagementService.class).setMemoryConfiguration(MemoryConfigurations.CLASSIFY);

        Instant incrementalStart = Instant.now();
        NidSet conceptNidSetToClassify = NidSet.of(newConcepts);
        log.info("   Concepts to classify: " + conceptNidSetToClassify.size());

        AtomicInteger logicGraphMembers = new AtomicInteger();
        AtomicInteger rejectedLogicGraphMembers = new AtomicInteger();
        ClassifierData cd = ClassifierData.get(stampCoordinate, logicCoordinate);
        log.info("     classifier data before: " + cd);
        if (cd.getLastClassifyInstant() != null) {
            log.info("Incremental classification ok.");
            StampPosition lastClassifyPosition = new StampPositionImpl(
                    cd.getLastClassifyInstant().toEpochMilli(),
                    editCoordinate.getPathSequence());
            SememeSequenceSet modifiedSememeSequences = getSememeService().
                    getSememeSequencesForComponentsFromAssemblageModifiedAfterPosition(
                            conceptNidSetToClassify,
                            logicCoordinate.getStatedAssemblageSequence(),
                            lastClassifyPosition);
            log.info("Modified graph count: " + modifiedSememeSequences.size());
            if (modifiedSememeSequences.isEmpty()) {
                log.info("No changes to classify.");
            } else {
                ConceptSequenceSet modifiedConcepts
                        = getIdentifierService().getConceptSequencesForReferencedComponents(modifiedSememeSequences);
                log.info("Modified concept count: " + modifiedConcepts.size());

                processIncrementalStatedAxioms(stampCoordinate, logicCoordinate,
                        NidSet.of(modifiedConcepts), cd,
                        logicGraphMembers,
                        rejectedLogicGraphMembers);
                log.info("classifying new axioms.");
                cd.incrementalClassify();
            }

        } else {
            log.info("Full classification required.");
            processAllStatedAxioms(stampCoordinate, logicCoordinate,
                    cd, logicGraphMembers);
            log.info("classifying all axioms.");
            cd.classify();
        }
        log.info("     classifier data after: " + cd);

        log.info("getting results.");
        Ontology res = cd.getClassifiedOntology();
        newConcepts.stream().forEach((sequence) -> {
            au.csiro.ontology.Node incrementalNode = res.getNode(Integer.toString(sequence));

            log.info("Incremental concept: " + sequence);
            log.info("  Parents: " + incrementalNode.getParents());
            log.info("  Equivalent concepts: " + incrementalNode.getEquivalentConcepts());
            log.info("  Child concepts: " + incrementalNode.getChildren());
        });
        ClassifierResults classifierResults = collectResults(res, res.getAffectedNodes());
        // TODO write back. 
        Instant incrementalEnd = Instant.now();
        Duration incrementalClassifyDuration = Duration.between(incrementalStart, incrementalEnd);
        log.info("  Incremental classify duration: " + incrementalClassifyDuration);
        return classifierResults;
    }

    protected HashTreeWithBitSets getStatedTaxonomyGraph() {
        try {
            IntStream conceptSequenceStream = getIdentifierService().getParallelConceptSequenceStream();
            GraphCollector collector = new GraphCollector(((CradleTaxonomyProvider) getTaxonomyService()).getOriginDestinationTaxonomyRecords(),
                    ViewCoordinates.getDevelopmentStatedLatest());
            HashTreeBuilder graphBuilder = conceptSequenceStream.collect(
                    HashTreeBuilder::new,
                    collector,
                    collector);
            HashTreeWithBitSets resultGraph = graphBuilder.getSimpleDirectedGraphGraph();
            return resultGraph;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected HashTreeWithBitSets getInferredTaxonomyGraph() {
        try {
            IntStream conceptSequenceStream = getIdentifierService().getParallelConceptSequenceStream();
            GraphCollector collector = new GraphCollector(((CradleTaxonomyProvider) getTaxonomyService()).getOriginDestinationTaxonomyRecords(),
                    ViewCoordinates.getDevelopmentInferredLatest());
            HashTreeBuilder graphBuilder = conceptSequenceStream.collect(
                    HashTreeBuilder::new,
                    collector,
                    collector);
            HashTreeWithBitSets resultGraph = graphBuilder.getSimpleDirectedGraphGraph();
            return resultGraph;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void processAllStatedAxioms(StampCoordinate stampCoordinate, LogicCoordinate logicCoordinate, ClassifierData cd, AtomicInteger logicGraphMembers) {
        SememeSnapshotService<LogicGraphSememeImpl> sememeSnapshot = getSememeService().getSnapshot(LogicGraphSememeImpl.class, stampCoordinate);
        sememeSnapshot.getLatestActiveSememeVersionsFromAssemblage(logicCoordinate.getStatedAssemblageSequence()).forEach(
                (LatestVersion<LogicGraphSememeImpl> latest) -> {
                    LogicGraphSememeImpl lgs = latest.value();
                    int conceptSequence = getIdentifierService().getConceptSequence(lgs.getReferencedComponentNid());
                    if (getConceptService().isConceptActive(conceptSequence, stampCoordinate)) {
                        cd.translate(lgs);
                        logicGraphMembers.incrementAndGet();
                    }
                });
    }

    protected void processIncrementalStatedAxioms(StampCoordinate stampCoordinate,
            LogicCoordinate logicCoordinate, NidSet conceptNidSetToClassify,
            ClassifierData cd, AtomicInteger logicGraphMembers,
            AtomicInteger rejectedLogicGraphMembers) {

        SememeSnapshotService<LogicGraphSememeImpl> sememeSnapshot = getSememeService().getSnapshot(LogicGraphSememeImpl.class, stampCoordinate);
        conceptNidSetToClassify.stream().forEach((conceptNid) -> {
            sememeSnapshot.getLatestActiveSememeVersionsForComponentFromAssemblage(conceptNid,
                    logicCoordinate.getStatedAssemblageSequence()).forEach((LatestVersion<LogicGraphSememeImpl> latest) -> {
                        LogicGraphSememeImpl lgs = latest.value();
                        if (conceptNidSetToClassify.contains(lgs.getReferencedComponentNid())) {
                            cd.translateForIncremental(lgs);
                            logicGraphMembers.incrementAndGet();
                        } else {
                            rejectedLogicGraphMembers.incrementAndGet();
                        }
                    });
        });
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

    @Override
    public Optional<LatestVersion<LogicExpressionOchreImpl>> getLogicGraph(int conceptId, int logicAssemblageId,
            StampCoordinate stampCoordinate) {
        SememeSnapshotService<LogicGraphSememeImpl> ssp
                = getSememeService().getSnapshot(LogicGraphSememeImpl.class, stampCoordinate);

        List<LatestVersion<LogicExpressionOchreImpl>> latestVersions
                = ssp.getLatestActiveSememeVersionsForComponentFromAssemblage(
                        conceptId, logicAssemblageId).map((LatestVersion<LogicGraphSememeImpl> lgs) -> {
                            LogicExpressionOchreImpl expressionValue
                            = new LogicExpressionOchreImpl(lgs.value().getGraphData(), DataSource.INTERNAL, lgs.value().getReferencedComponentNid());
                            LatestVersion<LogicExpressionOchreImpl> latestExpressionValue = new LatestVersion<>(expressionValue);

                            if (lgs.contradictions().isPresent()) {
                                lgs.contradictions().get().forEach((LogicGraphSememeImpl contradiction) -> {
                                    LogicExpressionOchreImpl contradictionValue
                                    = new LogicExpressionOchreImpl(contradiction.getGraphData(), DataSource.INTERNAL, contradiction.getReferencedComponentNid());
                                    latestExpressionValue.addLatest(contradictionValue);
                                });
                            }

                            return latestExpressionValue;
                        }).collect(Collectors.toList());

        if (latestVersions.isEmpty()) {
            return Optional.empty();
        }

        if (latestVersions.size() > 1) {
            throw new IllegalStateException("More than one LogicGraphSememeImpl for concept in assemblage: "
                    + latestVersions);
        }
        return Optional.of(latestVersions.get(0));
    }

    @Override
    public int getConceptSequenceForExpression(LogicalExpression expression,
            StampCoordinate stampCoordinate,
            LogicCoordinate logicCoordinate,
            EditCoordinate editCoordinate) {

        SememeSnapshotService<LogicGraphSememeImpl> sememeSnapshot = getSememeService().getSnapshot(LogicGraphSememeImpl.class, stampCoordinate);
        Optional<LatestVersion<LogicGraphSememeImpl>> match = sememeSnapshot.
                getLatestActiveSememeVersionsFromAssemblage(
                        logicCoordinate.getStatedAssemblageSequence()).
                filter((LatestVersion<LogicGraphSememeImpl> t) -> {
                    LogicGraphSememeImpl lgs = t.value();
                    LogicExpressionOchreImpl existingGraph = new LogicExpressionOchreImpl(lgs.getGraphData(), DataSource.INTERNAL);
                    return existingGraph.equals(expression);
                }).findFirst();

        if (match.isPresent()) {
            LogicGraphSememeImpl lgs = match.get().value();
            return getIdentifierService().getConceptSequence(lgs.getReferencedComponentNid());
        }

        UUID uuidForNewConcept = UUID.randomUUID();
        ConceptBuilderService conceptBuilderService = LookupService.getService(ConceptBuilderService.class);
        conceptBuilderService.setDefaultLanguageForDescriptions(IsaacMetadataAuxiliaryBinding.ENGLISH);
        conceptBuilderService.setDefaultDialectAssemblageForDescriptions(IsaacMetadataAuxiliaryBinding.US_ENGLISH_DIALECT);
        conceptBuilderService.setDefaultLogicCoordinate(logicCoordinate);
        ConceptBuilder builder = conceptBuilderService.getDefaultConceptBuilder(
                uuidForNewConcept.toString(), "expression", expression);

        ConceptChronology concept = builder.build(editCoordinate, ChangeCheckerMode.INACTIVE);
        try {
            getCommitService().commit("Expression commit.").get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        return concept.getConceptSequence();
    }

}
