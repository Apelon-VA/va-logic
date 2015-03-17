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
import gov.vha.isaac.cradle.taxonomy.TaxonomyService;
import gov.vha.isaac.cradle.taxonomy.graph.GraphCollector;
import gov.vha.isaac.logic.axioms.GraphToAxiomTranslator;
import gov.vha.isaac.metadata.coordinates.ViewCoordinates;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.DataTarget;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.SequenceProvider;
import gov.vha.isaac.ochre.api.TaxonomyProvider;
import gov.vha.isaac.ochre.api.tree.TreeNodeVisitData;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeBuilder;
import gov.vha.isaac.ochre.api.tree.hashtree.HashTreeWithBitSets;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.BitSet;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.mahout.math.set.OpenIntHashSet;
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
    private static SequenceProvider sequenceProvider;
    private static TaxonomyProvider taxonomyProvider;
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
    public static SequenceProvider getSequenceProvider() {
        if (sequenceProvider == null) {
            sequenceProvider = LookupService.getService(SequenceProvider.class);
        }
        return sequenceProvider;
    }

    /**
     * @return the taxonomyProvider
     */
    public static TaxonomyProvider getTaxonomyProvider() {
        if (taxonomyProvider == null) {
            taxonomyProvider = LookupService.getService(TaxonomyProvider.class);
        }
        return taxonomyProvider;
    }

    @Override
    public void initialize() {
        CradleExtensions cradleService = LookupService.getService(CradleExtensions.class);

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
            GraphCollector collector = new GraphCollector(((TaxonomyService) getTaxonomyProvider()).getOriginDestinationTaxonomyRecords(),
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

            log.info("  Start makeDlGraph.");
            collectStart = Instant.now();
            ViewCoordinate developmentStatedLatest = ViewCoordinates.getDevelopmentStatedLatest();
            AtomicInteger logicGraphMembers = new AtomicInteger();
            AtomicInteger logicGraphVersions = new AtomicInteger();
            AtomicInteger maxGraphVersionsPerMember = new AtomicInteger();

            // Figure out why role group is missing from current database, or is it a problem with the map...
            ConceptSpec tempRoleGroup = new ConceptSpec("Linkage concept", "1a3399bc-e6b5-3dea-8058-4e08012ff00f");
            ConceptSpec roleRoot = new ConceptSpec("Concept model attribute (attribute)", "6155818b-09ed-388e-82ce-caa143423e99");
            resultGraph.getDescendentSequenceSet(getSequenceProvider().getConceptSequence(roleRoot.getNid()));

            ConceptSpec tempDefinitionRefex = new ConceptSpec("Defining relationship", "e607218d-7027-3058-ae5f-0d4ccd148fd0");
            int tempDefinitionRefexNid = tempDefinitionRefex.getNid();

            ConceptSpec tempModule = new ConceptSpec("Module", "40d1c869-b509-32f8-b735-836eac577a67");
            int tempModuleNid = tempModule.getNid();

            ConceptSpec author = new ConceptSpec("user", "f7495b58-6630-3499-a44e-2052b5fcf06c");
            int tempAuthorNid = author.getNid();

            OpenIntHashSet roleConceptSequences = new OpenIntHashSet();
            BitSet roleConceptSequencesArray = resultGraph.getDescendentSequenceSet(getSequenceProvider().getConceptSequence(roleRoot.getNid()));
            roleConceptSequencesArray.stream().forEach(roleConceptSequence -> {
                roleConceptSequences.add(roleConceptSequence);
            });
            OpenIntHashSet featureConceptSequences = new OpenIntHashSet(); //empty set for now.

            OpenIntHashSet neverRoleGroupConceptSequences = new OpenIntHashSet();
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.PART_OF.getNid()));
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.LATERALITY.getNid()));
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.HAS_ACTIVE_INGREDIENT.getNid()));
            neverRoleGroupConceptSequences.add(getSequenceProvider().getConceptSequence(Snomed.HAS_DOSE_FORM.getNid()));

            int roleGroupNid = tempRoleGroup.getNid();

            AtomicInteger maxGraphSize = new AtomicInteger(0);

            cradleService.getParallelConceptDataEagerStream().forEach((ConceptChronicleDataEager conceptChronicleDataEager) -> {
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
                                        logicGraphMember.setNid(cradleService.getNidForUuids(primordialUuid));
                                        logicGraphMember.setArrayOfByteArray(logicGraphBytes);
                                        logicGraphMember.setAssemblageNid(tempDefinitionRefexNid);
                                        logicGraphMember.setReferencedComponentNid(conceptChronicle.getNid());
                                        logicGraphMember.setSTAMP(cradleService.getStamp(
                                                Status.ACTIVE, vcForPosition.getViewPosition().getTime(),
                                                tempAuthorNid,
                                                tempModuleNid,
                                                position.getPath().getConceptNid()));
                                        logicGraphMember.enclosingConceptNid = tempDefinitionRefexNid;
                                        cradleService.setConceptNidForNid(tempDefinitionRefexNid, logicGraphMember.getNid());

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
                        cradleService.writeSememe(logicGraphMember);
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
                                            getSequenceProvider().getConceptSequence(lgmv.getReferencedComponentNid()));
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
            collectEnd = Instant.now();
            collectDuration = Duration.between(collectStart, collectEnd);
            log.info("  Finished makeDlGraph. Member count: " + logicGraphMembers
                    + " Version count: " + logicGraphVersions);
            log.info("  Collection duration: " + collectDuration);
        } catch (IOException ex) {
            log.error(ex.getLocalizedMessage(), ex);
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
            GraphCollector collector = new GraphCollector(((TaxonomyService) getTaxonomyProvider()).getOriginDestinationTaxonomyRecords(),
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
            BitSet conceptsToClassify = resultGraph.getDescendentSequenceSet(getSequenceProvider().getConceptSequence(Taxonomies.SNOMED.getNid()));
            log.info("   Concepts to classify: " + conceptsToClassify.cardinality());

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
                if (conceptsToClassify.get(getSequenceProvider().getConceptSequence(sememe.getReferencedComponentNid()))) {
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

}
