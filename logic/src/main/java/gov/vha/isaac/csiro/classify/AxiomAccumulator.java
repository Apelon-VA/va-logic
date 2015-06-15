package gov.vha.isaac.csiro.classify;

import au.csiro.ontology.Factory;
import au.csiro.ontology.model.Axiom;
import au.csiro.ontology.model.Concept;
import au.csiro.ontology.model.ConceptInclusion;
import au.csiro.ontology.model.Role;
import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.node.AndNode;
import gov.vha.isaac.logic.node.internal.ConceptNodeWithNids;
import gov.vha.isaac.logic.node.internal.RoleNodeSomeWithNids;
import org.apache.mahout.math.map.OpenIntObjectHashMap;
import org.apache.mahout.math.set.OpenIntHashSet;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Created by kec on 12/16/14.
 */

// TODO move to CSIRO specific module

public class AxiomAccumulator implements BiConsumer<Set<Axiom>, LogicGraph> {

    BitSet conceptSequences;
    Concept[] concepts;
    OpenIntObjectHashMap<Role> roles;
    OpenIntHashSet neverGroupRoleSequences;
    int roleGroupConceptSequence;

    public AxiomAccumulator(Concept[] concepts, BitSet conceptSequences, OpenIntObjectHashMap<Role> roles,
                            OpenIntHashSet neverGroupRoleSequences, int roleGroupConceptSequence) {
        this.concepts = concepts;
        this.conceptSequences = conceptSequences;
        this.roles = roles;
        this.neverGroupRoleSequences = neverGroupRoleSequences;
        this.roleGroupConceptSequence = roleGroupConceptSequence;
    }

    @Override
    public void accept(Set<Axiom> axioms, LogicGraph logicGraphVersion) {
        if (conceptSequences.get(logicGraphVersion.getConceptSequence())) {
            axioms.addAll(generateAxioms(logicGraphVersion));
        }
    }

    public Set<Axiom> generateAxioms(LogicGraph logicGraphVersion) {
        Concept thisConcept = concepts[logicGraphVersion.getConceptSequence()];
        Set<Axiom> axioms = new HashSet<>();
        for (Node setNode : logicGraphVersion.getRoot().getChildren()) {
            AndNode andNode = (AndNode) setNode.getChildren()[0];
            ArrayList<Concept> definition = new ArrayList<>();
            for (Node child : andNode.getChildren()) {
                switch (child.getNodeSemantic()) {
                    case CONCEPT:
                        ConceptNodeWithNids conceptNode = (ConceptNodeWithNids) child;
                        definition.add(concepts[conceptNode.getConceptNid()]);
                        break;
                    case ROLE_SOME:
                        RoleNodeSomeWithNids roleNodeSome = (RoleNodeSomeWithNids) child;
                        definition.add(processRole(roleNodeSome, concepts, roles,
                                neverGroupRoleSequences, roleGroupConceptSequence));
                        break;
                    default:
                        throw new UnsupportedOperationException("Can't handle " + child + " as child of AND");
                }
            }

            switch (setNode.getNodeSemantic()) {
                case SUFFICIENT_SET:
                    // if sufficient set, create a concept inclusion from the axioms to the concept
                    axioms.add(new ConceptInclusion(
                            Factory.createConjunction(definition.toArray(new Concept[definition.size()])),
                            thisConcept
                    ));
                    // No break; here, for sufficient set, need to add the reverse necessary set...
                case NECESSARY_SET:
                    // if necessary set create a concept inclusion from the concept to the axioms
                    axioms.add(new ConceptInclusion(
                            thisConcept,
                            Factory.createConjunction(definition.toArray(new Concept[definition.size()]))
                    ));
                    break;
                default:
                    throw new UnsupportedOperationException("Can't handle " + setNode + " as child of root");
            }
        }
        return axioms;
    }


    private Concept[] getConcepts(Node[] nodes, Concept[] concepts, OpenIntObjectHashMap<Role> roles,
                                  OpenIntHashSet neverGroupRoleSequences, int roleGroupConceptSequence) {
        Concept[] returnValues = new Concept[concepts.length];
        for (int i = 0; i < concepts.length; i++) {
            returnValues[i] = getConcept(nodes[i],
                    concepts, roles, neverGroupRoleSequences, roleGroupConceptSequence);
        }
        return returnValues;
    }

    private Concept getConcept(Node node, Concept[] concepts, OpenIntObjectHashMap<Role> roles,
                               OpenIntHashSet neverGroupRoleSequences, int roleGroupConceptSequence) {
        switch (node.getNodeSemantic()) {
            case ROLE_SOME:
                RoleNodeSomeWithNids roleNodeSome = (RoleNodeSomeWithNids) node;
                return Factory.createExistential(roles.get(roleNodeSome.getTypeConceptNid()),
                        getConcept(roleNodeSome.getOnlyChild(), concepts, roles, neverGroupRoleSequences, roleGroupConceptSequence));
            case CONCEPT:
                ConceptNodeWithNids conceptNode = (ConceptNodeWithNids) node;
                return concepts[conceptNode.getConceptNid()];
            case AND:
                return Factory.createConjunction(getConcepts(node.getChildren(),
                        concepts, roles, neverGroupRoleSequences, roleGroupConceptSequence));
        }
        throw new UnsupportedOperationException("Can't handle " + node + " as child of ROLE_SOME.");
    }

    private Concept processRole(RoleNodeSomeWithNids roleNodeSome, Concept[] concepts, OpenIntObjectHashMap<Role> roles,
                                OpenIntHashSet neverGroupRoleSequences, int roleGroupConceptSequence) {
        // need to handle grouped, and never grouped...
        if (neverGroupRoleSequences.contains(roleNodeSome.getTypeConceptNid())) {
            return Factory.createExistential(roles.get(roleNodeSome.getTypeConceptNid()),
                    getConcept(roleNodeSome.getOnlyChild(), concepts, roles, neverGroupRoleSequences, roleGroupConceptSequence));
        }
        return Factory.createExistential(roles.get(roleGroupConceptSequence), getConcept(roleNodeSome,
                concepts, roles, neverGroupRoleSequences, roleGroupConceptSequence));

    }
}
