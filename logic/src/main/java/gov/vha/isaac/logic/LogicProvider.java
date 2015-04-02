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
import au.csiro.ontology.classification.IReasoner;
import au.csiro.ontology.model.Axiom;
import au.csiro.snorocket.core.SnorocketReasoner;
import gov.vha.isaac.cradle.CradleExtensions;
import gov.vha.isaac.cradle.component.ConceptChronicleDataEager;
import gov.vha.isaac.cradle.taxonomy.CradleTaxonomyProvider;
import gov.vha.isaac.cradle.taxonomy.graph.GraphCollector;
import gov.vha.isaac.logic.axioms.GraphToAxiomTranslator;
import gov.vha.isaac.metadata.coordinates.EditCoordinates;
import gov.vha.isaac.metadata.coordinates.ViewCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.DataTarget;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.SequenceService;
import gov.vha.isaac.ochre.api.State;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.chronicle.LatestVersion;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.sememe.SememeChronicle;
import gov.vha.isaac.ochre.api.sememe.SememeService;
import gov.vha.isaac.ochre.api.sememe.SememeSnapshotService;
import gov.vha.isaac.ochre.api.sememe.version.LogicGraphSememe;
import gov.vha.isaac.ochre.api.sememe.version.MutableLogicGraphSememe;
import gov.vha.isaac.ochre.api.tree.TreeNodeVisitData;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeBuilder;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeWithBitSets;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import gov.vha.isaac.ochre.model.sememe.SememeChronicleImpl;
import gov.vha.isaac.ochre.model.sememe.SememeType;
import gov.vha.isaac.ochre.model.sememe.version.LogicGraphSememeImpl;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.runlevel.RunLevel;
import org.ihtsdo.otf.tcc.api.contradiction.ContradictionException;
import org.ihtsdo.otf.tcc.api.coordinate.Position;
import org.ihtsdo.otf.tcc.api.coordinate.Status;
import org.ihtsdo.otf.tcc.api.coordinate.ViewCoordinate;
import org.ihtsdo.otf.tcc.api.metadata.binding.Snomed;
import org.ihtsdo.otf.tcc.api.metadata.binding.Taxonomies;
import org.ihtsdo.otf.tcc.api.refex.RefexChronicleBI;
import org.ihtsdo.otf.tcc.api.relationship.RelAssertionType;
import org.ihtsdo.otf.tcc.api.spec.ConceptSpec;
import org.ihtsdo.otf.tcc.model.cc.concept.ConceptChronicle;
import org.ihtsdo.otf.tcc.model.cc.concept.ConceptVersion;
import org.ihtsdo.otf.tcc.model.cc.refex.type_array_of_bytearray.ArrayOfByteArrayMember;
import org.ihtsdo.otf.tcc.model.cc.refex.type_array_of_bytearray.ArrayOfByteArrayMemberVersion;
import org.ihtsdo.otf.tcc.model.cc.refex.type_array_of_bytearray.ArrayOfByteArrayRevision;
import org.ihtsdo.otf.tcc.model.version.Stamp;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author kec
 */
@Service(name = "logic provider")
@RunLevel(value = 2)
public class LogicProvider implements LogicService {

    private static final Logger log = LogManager.getLogger();
    private static SequenceService sequenceProvider;
    private static TaxonomyService taxonomyProvider;
    private static SememeService sememeProvider;
    GraphToAxiomTranslator graphToAxiomTranslator = new GraphToAxiomTranslator();
    IReasoner r = new SnorocketReasoner();

    @PostConstruct
    private void startMe() throws IOException {
        System.out.println("Starting LogicProvider.");
    }
    @PreDestroy
    private void stopMe() throws IOException {
        System.out.println("Stopping LogicProvider.");
    }
    /**
     * @return the sequenceProvider
     */
    public static SequenceService getSequenceProvider() {
        if (sequenceProvider == null) {
            sequenceProvider = LookupService.getService(SequenceService.class);
        }
        return sequenceProvider;
    }

    /**
     * @return the taxonomyProvider
     */
    public static TaxonomyService getTaxonomyProvider() {
        if (taxonomyProvider == null) {
            taxonomyProvider = LookupService.getService(TaxonomyService.class);
        }
        return taxonomyProvider;
    }

        public static SememeService getSememeProvider() {
        if (sememeProvider == null) {
            sememeProvider = LookupService.getService(SememeService.class);
        }
        return sememeProvider;
    }


    @Override
    public void initialize() {
        CradleExtensions cradleService = LookupService.getService(CradleExtensions.class);

        try {
            log.info("  Start to make graph.");
            Instant collectStart = Instant.now();
            IntStream conceptSequenceStream = getSequenceProvider().getParallelConceptSequenceStream();
            GraphCollector collector = new GraphCollector(((CradleTaxonomyProvider) getTaxonomyProvider()).getOriginDestinationTaxonomyRecords(),
                    ViewCoordinates.getDevelopmentStatedLatest());
            HashTreeBuilder graphBuilder = conceptSequenceStream.collect(
                    HashTreeBuilder::new,
                    collector,
                    collector);
            HashTreeWithBitSets statedTree = graphBuilder.getSimpleDirectedGraphGraph();
            Instant collectEnd = Instant.now();
            Duration collectDuration = Duration.between(collectStart, collectEnd);
            log.info("  Finished making graph: " + statedTree);
            log.info("  Generation duration: " + collectDuration);

           
            ConceptSpec roleGroup = IsaacMetadataAuxiliaryBinding.ROLE_GROUP;
            ConceptSpec roleRoot = IsaacMetadataAuxiliaryBinding.ROLE;
            ConceptSpec featureRoot = IsaacMetadataAuxiliaryBinding.FEATURE;
            
            EditCoordinate ec = EditCoordinates.getDefaultUserVeteransAdministrationExtension();

            ConceptSequenceSet roleConceptSequences = statedTree.getDescendentSequenceSet(getSequenceProvider().getConceptSequence(roleRoot.getNid()));

            ConceptSequenceSet featureConceptSequences = statedTree.getDescendentSequenceSet(getSequenceProvider().getConceptSequence(featureRoot.getNid()));

            ConceptSequenceSet neverRoleGroupConceptSequences = new ConceptSequenceSet();
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.PART_OF.getNid()));
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.LATERALITY.getNid()));
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.HAS_ACTIVE_INGREDIENT.getNid()));
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.HAS_DOSE_FORM.getNid()));
            
           //------------
            makeGraphs(cradleService,
                    ViewCoordinates.getDevelopmentStatedLatest(), 
                    roleGroup, 
                    IsaacMetadataAuxiliaryBinding.EL_PLUS_PLUS_STATED_FORM, 
                    ec, 
                    roleConceptSequences, 
                    featureConceptSequences, 
                    neverRoleGroupConceptSequences);
            makeGraphs(cradleService,
                    ViewCoordinates.getDevelopmentInferredLatest(), 
                    roleGroup, 
                    IsaacMetadataAuxiliaryBinding.EL_PLUS_PLUS_INFERRED_FORM, 
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
            ConceptSpec statedDefinitionRefex, 
            EditCoordinate ec, 
            ConceptSequenceSet roleConceptSequences, 
            ConceptSequenceSet featureConceptSequences, 
            ConceptSequenceSet neverRoleGroupConceptSequences) {
        log.info("  Start makeDlGraph: " + viewForLogicGraph.getRelationshipAssertionType());
        Instant collectStart = Instant.now();
        Instant collectEnd;
        Duration collectDuration;
        AtomicInteger logicGraphMembers = new AtomicInteger();
        AtomicInteger logicGraphVersions = new AtomicInteger();
        AtomicInteger maxGraphVersionsPerMember = new AtomicInteger();
        int roleGroupNid = roleGroup.getNid();
        int definitionRefexNid = statedDefinitionRefex.getNid();
        AtomicInteger maxGraphSize = new AtomicInteger(0);
        cradleService.getParallelConceptDataEagerStream().forEach((ConceptChronicleDataEager conceptChronicleDataEager) -> {
            try {
                ConceptChronicle conceptChronicle = ConceptChronicle.get(conceptChronicleDataEager.getNid(), conceptChronicleDataEager);
                
                logicGraphMembers.incrementAndGet();
                SememeChronicleImpl<LogicGraphSememeImpl> logicGraphChronicle = null;
                LogicGraph lastLogicGraph = null;
                for (Position position : conceptChronicle.getPositions()) {
                    ViewCoordinate vcForPosition = new ViewCoordinate(UUID.randomUUID(),
                            "vc for position", viewForLogicGraph);
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
                                printIfMoreNodes(graphNodeCount, maxGraphSize, conceptChronicle, logicGraph);
                                
                                if (logicGraphChronicle == null) {
                                    logicGraphVersions.incrementAndGet();
                                    UUID primordialUuid = UUID.randomUUID();
                                    int nid = cradleService.getNidForUuids(primordialUuid);
                                    int containerSequence = sequenceProvider.getSememeSequence(nid);
                                    logicGraphChronicle = new SememeChronicleImpl<>(
                                            SememeType.LOGIC_GRAPH,
                                            primordialUuid,
                                            nid,
                                            sequenceProvider.getConceptSequence(definitionRefexNid),
                                            conceptChronicle.getNid(),
                                            containerSequence
                                    );
                                    LogicGraphSememeImpl mutable = logicGraphChronicle.createMutableVersion(
                                            LogicGraphSememeImpl.class, State.ACTIVE, ec);
                                    mutable.setTime(position.getTime());
                                    
                                    mutable.setGraphData(logicGraphBytes);
                                    
                                    cradleService.setConceptNidForNid(definitionRefexNid, logicGraphChronicle.getNid());
                                    
                                } else if (!logicGraph.equals(lastLogicGraph)) {
                                    logicGraphVersions.incrementAndGet();
                                    LogicGraphSememeImpl mutable = logicGraphChronicle.createMutableVersion(
                                            LogicGraphSememeImpl.class, State.ACTIVE, ec);
                                    mutable.setTime(position.getTime());
                                    
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
                    sememeProvider.writeSememe(logicGraphChronicle);
                    printIfMoreRevisions(logicGraphChronicle, maxGraphVersionsPerMember, conceptChronicle, maxGraphSize);
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

    public void printIfMoreNodes(int graphNodeCount, AtomicInteger maxGraphSize, ConceptChronicle conceptChronicle, LogicGraph logicGraph) {
        if (graphNodeCount > maxGraphSize.get()) {
            StringBuilder builder = new StringBuilder();
            printGraph(builder, "\n Make dl graph for: ", conceptChronicle, maxGraphSize, graphNodeCount, logicGraph);
            System.out.println(builder.toString());
        }
    }

    public void printIfMoreRevisions(SememeChronicleImpl<LogicGraphSememeImpl> logicGraphMember, AtomicInteger maxGraphVersionsPerMember, ConceptChronicle conceptChronicle, AtomicInteger maxGraphSize) {
        if (logicGraphMember.getVersions() != null) {
            Collection<LogicGraphSememeImpl> versions = logicGraphMember.getVersions();
            int versionCount = versions.size();
            if (versionCount > maxGraphVersionsPerMember.get()) {
                maxGraphVersionsPerMember.set(versionCount);
                StringBuilder builder = new StringBuilder();
                builder.append("Encountered logic definition with ").append(versionCount).append(" versions:\n\n");
                int version = 0;
                LogicGraph previousVersion = null;
                for (LogicGraphSememeImpl lgmv : versions) {
                    LogicGraph lg = new LogicGraph(lgmv.getGraphData(), DataSource.INTERNAL,
                            getSequenceProvider().getConceptSequence(lgmv.getReferencedComponentNid()));
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

    @Override
    public void fullClassification() {
        try {
            log.info("  Start to make graph.");
            Instant collectStart = Instant.now();
            IntStream conceptSequenceStream = getSequenceProvider().getParallelConceptSequenceStream();
            log.info("  conceptSequenceStream count 1:" + conceptSequenceStream.count());
            conceptSequenceStream = getSequenceProvider().getParallelConceptSequenceStream();
            log.info("  conceptSequenceStream count 2:" + conceptSequenceStream.count());
            conceptSequenceStream = getSequenceProvider().getParallelConceptSequenceStream();
            log.info("  conceptSequenceStream distinct count :" + conceptSequenceStream.distinct().count());
            conceptSequenceStream = getSequenceProvider().getConceptSequenceStream();
            GraphCollector collector = new GraphCollector(((CradleTaxonomyProvider) getTaxonomyProvider()).getOriginDestinationTaxonomyRecords(),
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
            log.info("  Start classify.");
            ConceptSequenceSet conceptsToClassify = resultGraph.getDescendentSequenceSet(getSequenceProvider().getConceptSequence(Taxonomies.SNOMED.getNid()));
            log.info("   Concepts to classify: " + conceptsToClassify.size());

            AtomicInteger logicGraphMembers = new AtomicInteger();
            AtomicInteger rejectedLogicGraphMembers = new AtomicInteger();
            Instant classifyStart = Instant.now();
            log.info("     Start axiom construction.");
            Instant axiomConstructionStart = Instant.now();
            graphToAxiomTranslator = new GraphToAxiomTranslator();
            ViewCoordinate viewCoordinate = ViewCoordinates.getDevelopmentStatedLatest();
            ConceptSpec tempDefinitionRefex = new ConceptSpec("Defining relationship", "e607218d-7027-3058-ae5f-0d4ccd148fd0");
            int tempDefinitionRefexNid = tempDefinitionRefex.getNid();
            ConceptChronicle chronicle = ConceptChronicle.get(tempDefinitionRefexNid);
            ConceptVersion concept = chronicle.getVersion(viewCoordinate);

            // get refset members as a parallel stream?
            for (RefexChronicleBI<?> sememe : concept.getRefsetMembers()) {
                if (conceptsToClassify.contains(getSequenceProvider().getConceptSequence(sememe.getReferencedComponentNid()))) {
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

            r = new SnorocketReasoner();
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
        // TODO write back. 
            Instant retrieveResultsEnd = Instant.now();
            Duration retrieveResultsDuration = Duration.between(retrieveResultsStart, retrieveResultsEnd);

            log.info("     Finished retrieve results. ");
            log.info("     Retrieve results duration: " + retrieveResultsDuration);
            Instant classifyEnd = Instant.now();
            Duration classifyDuration = Duration.between(classifyStart, classifyEnd);
            log.info("  Finished classify. LogicGraphMembers: " + logicGraphMembers + " rejected members: " + rejectedLogicGraphMembers);
            log.info("  Classify duration: " + classifyDuration);
        } catch (IOException | ContradictionException ex) {
            log.error(ex.getLocalizedMessage(), ex);
        }
    }

    @Override
    public void incrementalClassification() {
        log.info("\n  Start incremental test.");
        Instant incrementalStart = Instant.now();
        HashSet<Axiom> newAxioms = new HashSet<>();
//        Axiom incremental1 = au.csiro.ontology.Factory.createConceptInclusion(specialAppendicitis, appendicitis.get());
//        newAxioms.add(incremental1);
        r.loadAxioms(newAxioms);
        r.classify();
        Ontology res = r.getClassifiedOntology();
        // TODO write back. 
        Instant incrementalEnd = Instant.now();
        Duration incrementalClassifyDuration = Duration.between(incrementalStart, incrementalEnd);
        log.info("  Incremental classify duration: " + incrementalClassifyDuration);
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
    public Optional<LatestVersion<LogicGraph>>  getLogicGraph(int conceptId, int logicAssemblageId, 
            StampCoordinate stampCoordinate) {
        SememeSnapshotService<LogicGraphSememeImpl> ssp = 
                sememeProvider.getSnapshot(LogicGraphSememeImpl.class, stampCoordinate);
        
        Stream<LatestVersion<LogicGraphSememeImpl>> latestVersions = 
                ssp.getLatestActiveSememeVersionsForComponentFromAssemblage(
                        conceptId, logicAssemblageId);
        
        
        LatestVersion<LogicGraph> latest = latestVersions.collect(new LogicGraphCollector());
        if (latest.value() == null) {
            return Optional.empty();
        }    
        return Optional.of(latest);
    }
    
    private static class LogicGraphCollector implements Collector<LatestVersion<LogicGraphSememeImpl>,
            LatestVersion<LogicGraph>, LatestVersion<LogicGraph>> {

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(Characteristics.CONCURRENT, Characteristics.UNORDERED);
        }

        @Override
        public Supplier<LatestVersion<LogicGraph>> supplier() {
            return LatestVersion::new;
        }

        @Override
        public BiConsumer<LatestVersion<LogicGraph>, LatestVersion<LogicGraphSememeImpl>> accumulator() {
            return (latestGraph, latestSememe) -> {
                latestGraph.addLatest(new LogicGraph(latestSememe.value().getGraphData(), 
                        DataSource.INTERNAL, 
                        latestSememe.value().getReferencedComponentNid()));
                if (latestSememe.contradictions().isPresent()) {
                    latestSememe.contradictions().get().stream().forEach((sememe) -> {
                        latestGraph.addLatest(new LogicGraph(sememe.getGraphData(),
                                DataSource.INTERNAL,
                                sememe.getReferencedComponentNid()));
                    });
                }
            };
            
        }

        @Override
        public BinaryOperator<LatestVersion<LogicGraph>> combiner() {
            return (latest1, latest2) -> {
                
                if (latest1.value() != latest2.value()) {
                    latest1.addLatest(latest2.value());
                }
                if (latest2.contradictions().isPresent()) {
                    latest2.contradictions().get().forEach((logicGraph) 
                            -> latest1.addLatest(logicGraph));
                }
            
                return latest1;
            };
        }

        @Override
        public Function<LatestVersion<LogicGraph>, LatestVersion<LogicGraph>> finisher() {
            return Function.identity();
        }
        
    }

}
