/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.logic.axioms;

// TODO move to CSIRO specific module

import au.csiro.ontology.Factory;

import java.util.Set;
import au.csiro.ontology.model.Axiom;
import au.csiro.ontology.model.Concept;
import au.csiro.ontology.model.ConceptInclusion;
import au.csiro.ontology.model.Feature;
import au.csiro.ontology.model.Literal;
import au.csiro.ontology.model.Operator;
import au.csiro.ontology.model.Role;
import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.node.AndNode;
import gov.vha.isaac.logic.node.internal.ConceptNodeWithNids;
import gov.vha.isaac.logic.node.internal.FeatureNodeWithNids;
import gov.vha.isaac.logic.node.LiteralNodeBoolean;
import gov.vha.isaac.logic.node.LiteralNodeFloat;
import gov.vha.isaac.logic.node.LiteralNodeInstant;
import gov.vha.isaac.logic.node.LiteralNodeInteger;
import gov.vha.isaac.logic.node.LiteralNodeString;
import gov.vha.isaac.logic.node.NecessarySetNode;
import gov.vha.isaac.logic.node.internal.RoleNodeSomeWithNids;
import gov.vha.isaac.logic.node.RootNode;
import gov.vha.isaac.logic.node.SufficientSetNode;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.SequenceProvider;
import gov.vha.isaac.ochre.collections.ConcurrentSequenceObjectMap;
import java.util.Calendar;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.ihtsdo.otf.tcc.api.refex.type_array_of_bytearray.RefexArrayOfBytearrayVersionBI;
import org.ihtsdo.otf.tcc.lookup.Hk2Looker;

/**
 *
 * @author kec
 */
public class GraphToAxiomTranslator {

    Set<Axiom> axioms = new ConcurrentSkipListSet<>();

    ConcurrentSequenceObjectMap<Concept> sequenceLogicConceptMap = new ConcurrentSequenceObjectMap<>();
    ConcurrentHashMap<Integer, Role> sequenceLogicRoleMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, Feature> sequenceLogicFeatureMap = new ConcurrentHashMap<>();
    Factory f = new Factory();
    private static final SequenceProvider sequenceProvider = Hk2Looker.getService(SequenceProvider.class);

    private Concept getConcept(int name) {
        if (name < 0) {
            name = sequenceProvider.getConceptSequence(name);
        }
        Optional<Concept> optionalConcept = sequenceLogicConceptMap.get(name);
        if (optionalConcept.isPresent()) {
            return optionalConcept.get();
        }
        return sequenceLogicConceptMap.put(name, Factory.createNamedConcept(Integer.toString(name)));
    }

    private Feature getFeature(int name) {
        if (name < 0) {
            name = sequenceProvider.getConceptSequence(name);
        }
        Feature feature = sequenceLogicFeatureMap.get(name);
        if (feature != null) {
            return feature;
        }
        sequenceLogicFeatureMap.putIfAbsent(name, Factory.createNamedFeature(Integer.toString(name)));
        return sequenceLogicFeatureMap.get(name);
    }

    private Role getRole(int name) {
        if (name < 0) {
            name = sequenceProvider.getConceptSequence(name);
        }
        Role role = sequenceLogicRoleMap.get(name);
        if (role != null) {
            return role;
        }
        sequenceLogicRoleMap.putIfAbsent(name, Factory.createNamedRole(Integer.toString(name)));
        return sequenceLogicRoleMap.get(name);
    }

    public void translate(RefexArrayOfBytearrayVersionBI logicGraphSememe) {
        LogicGraph logicGraph = new LogicGraph(logicGraphSememe.getArrayOfByteArray(), DataSource.INTERNAL);
        Concept c = getConcept(logicGraphSememe.getReferencedComponentNid());
        generateAxioms(logicGraph.getRoot(), c, logicGraph);
    }

    public Optional<Literal> generateLiterals(Node node, Concept c, LogicGraph logicGraph) {
        switch (node.getNodeSemantic()) {
            case LITERAL_BOOLEAN:
                LiteralNodeBoolean literalNodeBoolean = (LiteralNodeBoolean) node;
                return Optional.of(Factory.createBooleanLiteral(literalNodeBoolean.getLiteralValue()));
            case LITERAL_FLOAT:
                LiteralNodeFloat literalNodeFloat = (LiteralNodeFloat) node;
                return Optional.of(Factory.createFloatLiteral(literalNodeFloat.getLiteralValue()));
            case LITERAL_INSTANT:
                LiteralNodeInstant literalNodeInstant = (LiteralNodeInstant) node;
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(literalNodeInstant.getLiteralValue().toEpochMilli());
                return Optional.of(Factory.createDateLiteral(calendar));
            case LITERAL_INTEGER:
                LiteralNodeInteger literalNodeInteger = (LiteralNodeInteger) node;
                return Optional.of(Factory.createIntegerLiteral(literalNodeInteger.getLiteralValue()));
            case LITERAL_STRING:
                LiteralNodeString literalNodeString = (LiteralNodeString) node;
                return Optional.of(Factory.createStringLiteral(literalNodeString.getLiteralValue()));
            default:
                throw new UnsupportedOperationException("Expected literal node, found: " + node
                        + " Concept: " + c + " graph: " + logicGraph);
        }
    }

    public Optional<Concept> generateAxioms(Node node, Concept c, LogicGraph logicGraph) {
        switch (node.getNodeSemantic()) {
            case AND:
                return processAnd((AndNode) node, c, logicGraph);
            case CONCEPT:
                ConceptNodeWithNids conceptNode = (ConceptNodeWithNids) node;
                return Optional.of(getConcept(conceptNode.getConceptNid()));
            case DEFINITION_ROOT:
                processRoot(node, c, logicGraph);
                break;
            case DISJOINT_WITH:
                throw new UnsupportedOperationException("Not supported by SnoRocket/EL++.");
            case FEATURE:
                return processFeatureNode((FeatureNodeWithNids) node, c, logicGraph);
            case NECESSARY_SET:
                processNecessarySet((NecessarySetNode) node, c, logicGraph);
                break;
            case OR:
                throw new UnsupportedOperationException("Not supported by SnoRocket/EL++.");
            case ROLE_ALL:
                throw new UnsupportedOperationException("Not supported by SnoRocket/EL++.");
            case ROLE_SOME:
                return processRoleNodeSome((RoleNodeSomeWithNids) node, c, logicGraph);
            case SUBSTITUTION_BOOLEAN:
                throw new UnsupportedOperationException("Supported, but not yet implemented.");
            case SUBSTITUTION_CONCEPT:
                throw new UnsupportedOperationException("Supported, but not yet implemented.");
            case SUBSTITUTION_FLOAT:
                throw new UnsupportedOperationException("Supported, but not yet implemented.");
            case SUBSTITUTION_INSTANT:
                throw new UnsupportedOperationException("Supported, but not yet implemented.");
            case SUBSTITUTION_INTEGER:
                throw new UnsupportedOperationException("Supported, but not yet implemented.");
            case SUBSTITUTION_STRING:
                throw new UnsupportedOperationException("Supported, but not yet implemented.");
            case SUFFICIENT_SET:
                processSufficientSet((SufficientSetNode) node, c, logicGraph);
                break;
            case TEMPLATE:
                throw new UnsupportedOperationException("Supported, but not yet implemented.");
            case LITERAL_BOOLEAN:
            case LITERAL_FLOAT:
            case LITERAL_INSTANT:
            case LITERAL_INTEGER:
            case LITERAL_STRING:
                throw new UnsupportedOperationException("Expected concept node, found literal node: " + node
                        + " Concept: " + c + " graph: " + logicGraph);
            default:
                throw new UnsupportedOperationException("Can't handle: " + node.getNodeSemantic());
        }
        return Optional.empty();
    }

    private Optional<Concept> processAnd(AndNode andNode, Concept c, LogicGraph logicGraph) {
        Node[] childrenNodes = andNode.getChildren();
        Concept[] conjunctionConcepts = new Concept[childrenNodes.length];
        for (int i = 0; i < childrenNodes.length; i++) {
            conjunctionConcepts[i] = generateAxioms(childrenNodes[i], c, logicGraph).get();
        }
        return Optional.of(Factory.createConjunction(conjunctionConcepts));
    }

    private void processSufficientSet(SufficientSetNode sufficientSetNode, Concept c, LogicGraph logicGraph) {
        Node[] children = sufficientSetNode.getChildren();
        if (children.length != 1) {
            throw new IllegalStateException("SufficientSetNode can only have one child. Concept: " + c + " graph: " + logicGraph);
        }
        if (!(children[0] instanceof AndNode)) {
            throw new IllegalStateException("SufficientSetNode can only have AND for a child. Concept: " + c + " graph: " + logicGraph);
        }
        Optional<Concept> conjunctionConcept = generateAxioms(children[0], c, logicGraph);
        if (conjunctionConcept.isPresent()) {
            axioms.add(new ConceptInclusion(c, conjunctionConcept.get()));
            axioms.add(new ConceptInclusion(conjunctionConcept.get(), c));
        } else {
            throw new IllegalStateException("Child node must return a conjunction concept. Concept: " + c + " graph: " + logicGraph);
        }
    }

    private void processNecessarySet(NecessarySetNode necessarySetNode, Concept c, LogicGraph logicGraph) {
        Node[] children = necessarySetNode.getChildren();
        if (children.length != 1) {
            throw new IllegalStateException("necessarySetNode can only have one child. Concept: " + c + " graph: " + logicGraph);
        }
        if (!(children[0] instanceof AndNode)) {
            throw new IllegalStateException("necessarySetNode can only have AND for a child. Concept: " + c + " graph: " + logicGraph);
        }
        Optional<Concept> conjunctionConcept = generateAxioms(children[0], c, logicGraph);
        if (conjunctionConcept.isPresent()) {
            axioms.add(new ConceptInclusion(c, conjunctionConcept.get()));
        } else {
            throw new IllegalStateException("Child node must return a conjunction concept. Concept: " + c + " graph: " + logicGraph);
        }
    }

    private void processRoot(Node node, Concept c, LogicGraph logicGraph) throws IllegalStateException {
        RootNode rootNode = (RootNode) node;
        for (Node child : rootNode.getChildren()) {
            Optional<Concept> axiom = generateAxioms(child, c, logicGraph);
            if (axiom.isPresent()) {
                throw new IllegalStateException("Children of root node should not return axioms. Concept: " + c + " graph: " + logicGraph);
            }
        }
    }

    private Optional<Concept> processRoleNodeSome(RoleNodeSomeWithNids roleNodeSome, Concept c, LogicGraph logicGraph) {
        Role theRole = getRole(roleNodeSome.getTypeConceptNid());
        Node[] children = roleNodeSome.getChildren();
        if (children.length != 1) {
            throw new IllegalStateException("RoleNodeSome can only have one child. Concept: " + c + " graph: " + logicGraph);
        }
        Optional<Concept> restrictionConcept = generateAxioms(children[0], c, logicGraph);
        if (restrictionConcept.isPresent()) {
            return Optional.of(Factory.createExistential(theRole, restrictionConcept.get()));
        }
        throw new UnsupportedOperationException("Child of role node can not return null concept. Concept: " + c + " graph: " + logicGraph);
    }

    public Set<Axiom> getAxioms() {
        return axioms;
    }

    public Optional<Concept> getConceptFromSequence(int sequence) {
        return sequenceLogicConceptMap.get(sequence);
    }

    private Optional<Concept> processFeatureNode(FeatureNodeWithNids featureNode, Concept c, LogicGraph logicGraph) {
        Feature theFeature = getFeature(featureNode.getTypeConceptNid());
        Node[] children = featureNode.getChildren();
        if (children.length != 1) {
            throw new IllegalStateException("FeatureNode can only have one child. Concept: " + c + " graph: " + logicGraph);
        }
        Optional<Literal> optionalLiteral = generateLiterals(children[0], c, logicGraph);
        if (optionalLiteral.isPresent()) {
            switch (featureNode.getOperator()) {
                case EQUALS:
                    return Optional.of(Factory.createDatatype(theFeature, Operator.EQUALS, optionalLiteral.get()));
                case GREATER_THAN:
                    return Optional.of(Factory.createDatatype(theFeature, Operator.GREATER_THAN, optionalLiteral.get()));
                case GREATER_THAN_EQUALS:
                    return Optional.of(Factory.createDatatype(theFeature, Operator.GREATER_THAN_EQUALS, optionalLiteral.get()));
                case LESS_THAN:
                    return Optional.of(Factory.createDatatype(theFeature, Operator.LESS_THAN, optionalLiteral.get()));
                case LESS_THAN_EQUALS:
                    return Optional.of(Factory.createDatatype(theFeature, Operator.LESS_THAN_EQUALS, optionalLiteral.get()));
                default:
                    throw new UnsupportedOperationException(featureNode.getOperator().toString());
            }
        }
        throw new UnsupportedOperationException("Child of FeatureNode node cannot return null concept. Concept: " + c + " graph: " + logicGraph);
    }
}
