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

import gov.vha.isaac.ochre.api.logic.LogicService;
import gov.vha.isaac.csiro.classify.ClassifierProvider;
import gov.vha.isaac.metadata.coordinates.LogicCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.TaxonomyService;
import gov.vha.isaac.ochre.api.classifier.ClassifierService;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.component.sememe.SememeChronology;
import gov.vha.isaac.ochre.api.component.sememe.SememeService;
import gov.vha.isaac.ochre.api.component.sememe.version.LogicGraphSememe;
import gov.vha.isaac.ochre.api.component.sememe.version.SememeVersion;
import gov.vha.isaac.ochre.api.coordinate.EditCoordinate;
import gov.vha.isaac.ochre.api.coordinate.LogicCoordinate;
import gov.vha.isaac.ochre.api.coordinate.PremiseType;
import gov.vha.isaac.ochre.api.coordinate.StampCoordinate;
import gov.vha.isaac.ochre.api.logic.Node;
import gov.vha.isaac.ochre.api.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.relationship.RelationshipAdaptorChronicleKey;
import gov.vha.isaac.ochre.api.relationship.RelationshipVersionAdaptor;
import gov.vha.isaac.ochre.model.logic.LogicalExpressionOchreImpl;
import gov.vha.isaac.ochre.model.logic.node.AndNode;
import gov.vha.isaac.ochre.model.logic.node.internal.ConceptNodeWithNids;
import gov.vha.isaac.ochre.model.logic.node.internal.RoleNodeSomeWithNids;
import gov.vha.isaac.ochre.model.relationship.RelationshipAdaptorChronicleKeyImpl;
import gov.vha.isaac.ochre.model.relationship.RelationshipAdaptorChronologyImpl;
import gov.vha.isaac.ochre.model.relationship.RelationshipVersionAdaptorImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author kec
 */
@Service(name = "logic provider")
@RunLevel(value = 2)
public class LogicProvider implements LogicService {

    private static final Logger log = LogManager.getLogger();

    private static final Map<ClassifierServiceKey, ClassifierService> classifierServiceMap = new ConcurrentHashMap<>();

    private static IdentifierService identifierService;

    private static IdentifierService getIdentifierService() {
        if (identifierService == null) {
            identifierService = LookupService.getService(IdentifierService.class);
        }
        return identifierService;
    }

    private static SememeService sememeService;

    protected static SememeService getSememeService() {
        if (sememeService == null) {
            sememeService = LookupService.getService(SememeService.class);
        }
        return sememeService;
    }

    private static TaxonomyService taxonomyService;

    private static TaxonomyService getTaxonomyService() {
        if (taxonomyService == null) {
            taxonomyService = LookupService.getService(TaxonomyService.class);
        }
        return taxonomyService;
    }

    private LogicProvider() {
        //For HK2
        log.info("logic provider constructed");
    }

    @PostConstruct
    private void startMe() throws IOException {
        log.info("Starting LogicProvider.");
    }

    @PreDestroy
    private void stopMe() throws IOException {
        log.info("Stopping LogicProvider.");
    }

    @Override
    public ClassifierService getClassifierService(
            StampCoordinate stampCoordinate,
            LogicCoordinate logicCoordinate,
            EditCoordinate editCoordinate) {
        ClassifierServiceKey key = new ClassifierServiceKey(stampCoordinate, logicCoordinate, editCoordinate);
        if (!classifierServiceMap.containsKey(key)) {
            classifierServiceMap.putIfAbsent(key,
                    new ClassifierProvider(stampCoordinate, logicCoordinate, editCoordinate));
        }
        return classifierServiceMap.get(key);
    }

    private static class ClassifierServiceKey {

        StampCoordinate stampCoordinate;
        LogicCoordinate logicCoordinate;
        EditCoordinate editCoordinate;

        public ClassifierServiceKey(StampCoordinate stampCoordinate, LogicCoordinate logicCoordinate, EditCoordinate editCoordinate) {
            this.stampCoordinate = stampCoordinate;
            this.logicCoordinate = logicCoordinate;
            this.editCoordinate = editCoordinate;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 59 * hash + Objects.hashCode(this.logicCoordinate);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ClassifierServiceKey other = (ClassifierServiceKey) obj;
            if (!Objects.equals(this.stampCoordinate, other.stampCoordinate)) {
                return false;
            }
            if (!Objects.equals(this.logicCoordinate, other.logicCoordinate)) {
                return false;
            }
            return Objects.equals(this.editCoordinate, other.editCoordinate);
        }
    }

    @Override
    public Stream<? extends SememeChronology<? extends RelationshipVersionAdaptor>>
            getRelationshipAdaptorsOriginatingWithConcept(ConceptChronology conceptChronology) {
        return getRelationshipAdaptorsOriginatingWithConcept(conceptChronology, LogicCoordinates.getStandardElProfile());
    }

    @Override
    public Stream<? extends SememeChronology<? extends RelationshipVersionAdaptor>>
            getRelationshipAdaptorsWithConceptAsDestination(ConceptChronology conceptChronology) {
        return getRelationshipAdaptorsWithConceptAsDestination(conceptChronology, LogicCoordinates.getStandardElProfile());
    }

    @Override
    public Stream<? extends SememeChronology<? extends RelationshipVersionAdaptor>>
            getRelationshipAdaptorsWithConceptAsDestination(ConceptChronology conceptChronology, LogicCoordinate logicCoordinate) {
        List<SememeChronology<? extends SememeVersion>> statedDefinitions = new ArrayList<>();
        List<SememeChronology<? extends SememeVersion>> inferredDefinitions = new ArrayList<>();
        Stream.Builder<RelationshipAdaptorChronologyImpl> streamBuilder = Stream.builder();
        HashMap<RelationshipAdaptorChronicleKey, RelationshipAdaptorChronologyImpl> conceptDestinationRelationshipMap = new HashMap<>();

        getTaxonomyService().getAllRelationshipOriginSequences(conceptChronology.getConceptSequence()).forEach((originConceptSequence) -> {
            statedDefinitions.addAll(getSememeService().getSememesForComponentFromAssemblage(originConceptSequence,
                    logicCoordinate.getStatedAssemblageSequence()).collect(Collectors.toList()));
            inferredDefinitions.addAll(getSememeService().getSememesForComponentFromAssemblage(originConceptSequence,
                    logicCoordinate.getInferredAssemblageSequence()).collect(Collectors.toList()));
        });

        statedDefinitions.forEach((statedDef) -> {
            generateRelAdaptorChronicles(conceptChronology.getConceptSequence(), statedDef, conceptDestinationRelationshipMap, PremiseType.STATED);
        });

        inferredDefinitions.forEach((inferredDef) -> {
            generateRelAdaptorChronicles(conceptChronology.getConceptSequence(), inferredDef, conceptDestinationRelationshipMap, PremiseType.INFERRED);
        });

        conceptDestinationRelationshipMap.values().stream().forEach((relAdaptor) -> {
            streamBuilder.accept(relAdaptor);
        });
        return streamBuilder.build();
    }

    @Override
    public Stream<? extends SememeChronology<? extends RelationshipVersionAdaptor>>
            getRelationshipAdaptorsOriginatingWithConcept(ConceptChronology conceptChronology,
                    LogicCoordinate logicCoordinate) {

        Stream.Builder<RelationshipAdaptorChronologyImpl> streamBuilder = Stream.builder();
        HashMap<RelationshipAdaptorChronicleKey, RelationshipAdaptorChronologyImpl> conceptOriginRelationshipMap = new HashMap<>();

        List<SememeChronology<? extends SememeVersion>> statedDefinitions
                = getSememeService().getSememesForComponentFromAssemblage(conceptChronology.getNid(),
                        logicCoordinate.getStatedAssemblageSequence()).collect(Collectors.toList());
        List<SememeChronology<? extends SememeVersion>> inferredDefinitions
                = getSememeService().getSememesForComponentFromAssemblage(conceptChronology.getNid(),
                        logicCoordinate.getInferredAssemblageSequence()).collect(Collectors.toList());

        statedDefinitions.forEach((statedDef) -> {
            generateRelAdaptorChronicles(statedDef, conceptOriginRelationshipMap, PremiseType.STATED);
        });

        inferredDefinitions.forEach((inferredDef) -> {
            generateRelAdaptorChronicles(inferredDef, conceptOriginRelationshipMap, PremiseType.INFERRED);
        });

        conceptOriginRelationshipMap.values().stream().forEach((relAdaptor) -> {
            streamBuilder.accept(relAdaptor);
        });
        return streamBuilder.build();
    }

    private void generateRelAdaptorChronicles(SememeChronology<? extends SememeVersion> logicalDef,
            HashMap<RelationshipAdaptorChronicleKey, RelationshipAdaptorChronologyImpl> conceptOriginRelationshipMap,
            PremiseType premiseType) {
        generateRelAdaptorChronicles(Integer.MAX_VALUE, logicalDef, conceptOriginRelationshipMap, premiseType);
    }

    private void generateRelAdaptorChronicles(int conceptDestinationSequence, SememeChronology<? extends SememeVersion> logicalDef,
            HashMap<RelationshipAdaptorChronicleKey, RelationshipAdaptorChronologyImpl> conceptOriginRelationshipMap,
            PremiseType premiseType) {
        extractRelationshipAdaptors((SememeChronology<LogicGraphSememe>) logicalDef, premiseType)
                .forEach((relAdaptor) -> {
                    if (conceptDestinationSequence == Integer.MAX_VALUE || conceptDestinationSequence == relAdaptor.getDestinationSequence()) {
                        RelationshipAdaptorChronologyImpl chronicle
                        = conceptOriginRelationshipMap.get(relAdaptor.getChronicleKey());
                        if (chronicle == null) {
                            // compute nid, combine the sememe sequence + the node sequence from which
                            int topBits = relAdaptor.getNodeSequence() << 24;
                            int adaptorNid = logicalDef.getSememeSequence() + topBits;

                            chronicle = new RelationshipAdaptorChronologyImpl(adaptorNid, logicalDef.getNid());
                            conceptOriginRelationshipMap.put(relAdaptor.getChronicleKey(), chronicle);
                        }
                        relAdaptor.setChronology(chronicle);
                        chronicle.getVersionList().add(relAdaptor);
                    }
                });
    }

    private Stream<RelationshipVersionAdaptorImpl> extractRelationshipAdaptors(
            SememeChronology<LogicGraphSememe> logicGraphChronology,
            PremiseType premiseType) {

        Stream.Builder<RelationshipVersionAdaptorImpl> streamBuilder = Stream.builder();
        int originConceptSequence = getIdentifierService().getConceptSequence(logicGraphChronology.getReferencedComponentNid());
        logicGraphChronology.getVersionList().forEach((logicVersion) -> {
            LogicalExpressionOchreImpl expression
                    = new LogicalExpressionOchreImpl(logicVersion.getGraphData(),
                            DataSource.INTERNAL,
                            originConceptSequence);

            expression.getRoot()
                    .getChildStream().forEach((necessaryOrSufficientSet) -> {
                        necessaryOrSufficientSet.getChildStream().forEach((Node andOrOrNode)
                                -> andOrOrNode.getChildStream().forEach((Node aNode) -> {
                            switch (aNode.getNodeSemantic()) {
                                case CONCEPT:
                                    streamBuilder.accept(
                                            createIsaRel(originConceptSequence,
                                                    (ConceptNodeWithNids) aNode,
                                                    logicVersion.getStampSequence(),
                                                    premiseType));
                                    break;
                                case ROLE_SOME:

                                    createSomeRole(originConceptSequence,
                                            (RoleNodeSomeWithNids) aNode,
                                            logicVersion.getStampSequence(),
                                            premiseType, 0).forEach((someRelAdaptor) -> {
                                        streamBuilder.accept(someRelAdaptor);
                                    });
                                    break;
                                default:
                                    throw new UnsupportedOperationException("Can't handle: " + aNode.getNodeSemantic());
                            }
                        }));
                    });
        });

        return streamBuilder.build();
    }

    private RelationshipVersionAdaptorImpl createIsaRel(int originSequence,
            ConceptNodeWithNids destinationNode,
            int stampSequence, PremiseType premiseType) {
        int destinationSequence = getIdentifierService().getConceptSequence(destinationNode.getConceptNid());
        int typeSequence = IsaacMetadataAuxiliaryBinding.IS_A.getSequence();
        int group = 0;

        RelationshipAdaptorChronicleKeyImpl key
                = new RelationshipAdaptorChronicleKeyImpl(originSequence,
                        destinationSequence, typeSequence, group, premiseType, destinationNode.getNodeIndex());
        return new RelationshipVersionAdaptorImpl(key, stampSequence);

    }

    private Stream<RelationshipVersionAdaptorImpl> createSomeRole(int originSequence,
            RoleNodeSomeWithNids someNode,
            int stampSequence, PremiseType premiseType, int roleGroup) {

        Stream.Builder<RelationshipVersionAdaptorImpl> roleStream = Stream.builder();

        if (someNode.getTypeConceptNid() == IsaacMetadataAuxiliaryBinding.ROLE_GROUP.getNid()) {
            AndNode andNode = (AndNode) someNode.getOnlyChild();
            andNode.getChildStream().forEach((roleGroupSomeNode) -> {
                createSomeRole(originSequence, (RoleNodeSomeWithNids) roleGroupSomeNode,
                        stampSequence, premiseType, someNode.getNodeIndex())
                        .forEach((adaptor) -> {
                            roleStream.add(adaptor);
                        });
            });

        } else {
            Node restriction = someNode.getOnlyChild();
            int destinationSequence;
            if (restriction.getNodeSemantic() == NodeSemantic.CONCEPT) {
                ConceptNodeWithNids restrictionNode = (ConceptNodeWithNids) someNode.getOnlyChild();
                destinationSequence = getIdentifierService().getConceptSequence(restrictionNode.getConceptNid());
            } else {
                destinationSequence = IsaacMetadataAuxiliaryBinding.ANONYMOUS_CONCEPT.getSequence();
            }
            int typeSequence = getIdentifierService().getConceptSequence(someNode.getTypeConceptNid());

            RelationshipAdaptorChronicleKeyImpl key
                    = new RelationshipAdaptorChronicleKeyImpl(originSequence,
                            destinationSequence, typeSequence, roleGroup, premiseType, someNode.getNodeIndex());
            roleStream.accept(new RelationshipVersionAdaptorImpl(key, stampSequence));
        }
        return roleStream.build();
    }

}
