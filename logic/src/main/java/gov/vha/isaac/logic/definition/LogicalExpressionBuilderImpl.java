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
package gov.vha.isaac.logic.definition;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.node.AbstractNode;
import gov.vha.isaac.ochre.api.ConceptProxy;
import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder;
import gov.vha.isaac.ochre.api.logic.assertions.AllRole;
import gov.vha.isaac.ochre.api.logic.assertions.Assertion;
import gov.vha.isaac.ochre.api.logic.assertions.ConceptAssertion;
import gov.vha.isaac.ochre.api.logic.assertions.Feature;
import gov.vha.isaac.ochre.api.logic.assertions.NecessarySet;
import gov.vha.isaac.ochre.api.logic.assertions.SomeRole;
import gov.vha.isaac.ochre.api.logic.assertions.SufficientSet;
import gov.vha.isaac.ochre.api.logic.assertions.Template;
import gov.vha.isaac.ochre.api.logic.assertions.connectors.And;
import gov.vha.isaac.ochre.api.logic.assertions.connectors.Connector;
import gov.vha.isaac.ochre.api.logic.assertions.connectors.DisjointWith;
import gov.vha.isaac.ochre.api.logic.assertions.connectors.Or;
import gov.vha.isaac.ochre.api.logic.assertions.literal.BooleanLiteral;
import gov.vha.isaac.ochre.api.logic.assertions.literal.FloatLiteral;
import gov.vha.isaac.ochre.api.logic.assertions.literal.InstantLiteral;
import gov.vha.isaac.ochre.api.logic.assertions.literal.IntegerLiteral;
import gov.vha.isaac.ochre.api.logic.assertions.literal.LiteralAssertion;
import gov.vha.isaac.ochre.api.logic.assertions.literal.StringLiteral;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.BooleanSubstitution;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.ConceptSubstitution;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.FloatSubstitution;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.InstantSubstitution;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.IntegerSubstitution;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.StringSubstitution;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.SubstitutionFieldSpecification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.mahout.math.map.OpenShortObjectHashMap;

/**
 *
 * @author kec
 */
public class LogicalExpressionBuilderImpl implements LogicalExpressionBuilder {
    
    private static IdentifierService identifierService = null;
    private static IdentifierService getIdentifierService() {
        if (identifierService == null) {
            identifierService = LookupService.getService(IdentifierService.class);
        }
        return identifierService;
    }

    private boolean built = false;
    private short nextAxiomId = 0;
    private final Set<GenericAxiom> rootSets = new HashSet<>();
    private final HashMap<GenericAxiom, List<GenericAxiom>> definitionTree = new HashMap<>(20);
    OpenShortObjectHashMap<Object> axiomParameters = new OpenShortObjectHashMap<>(20);

    public LogicalExpressionBuilderImpl() {
    }

    public short getNextAxiomIndex() {
        return nextAxiomId++;
    }

    private List<GenericAxiom> asList(Assertion... assertions) {
        ArrayList<GenericAxiom> list = new ArrayList<>(assertions.length);
        Arrays.stream(assertions).forEach((assertion) -> list.add((GenericAxiom) assertion));
        return list;
    }

    @Override
    public NecessarySet necessarySet(Connector... connector) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.NECESSARY_SET, this);
        rootSets.add(axiom);
        addToDefinitionTree(axiom, connector);
        return axiom;
    }

    protected void addToDefinitionTree(GenericAxiom axiom, Assertion... connectors) {
        definitionTree.put(axiom, asList(connectors));
    }

    @Override
    public SufficientSet sufficientSet(Connector... connector) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.SUFFICIENT_SET, this);
        rootSets.add(axiom);
        addToDefinitionTree(axiom, connector);
        return axiom;
    }

    @Override
    public DisjointWith disjointWith(ConceptProxy conceptProxy) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.DISJOINT_WITH, this);
        axiomParameters.put(axiom.getIndex(), conceptProxy);
        return axiom;
    }

    @Override
    public And and(Assertion... assertions) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.AND, this);
        addToDefinitionTree(axiom, assertions);
        return axiom;
    }

    @Override
    public ConceptAssertion conceptAssertion(ConceptProxy conceptProxy) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.CONCEPT, this);
        axiomParameters.put(axiom.getIndex(), conceptProxy);
        return axiom;
    }

    @Override
    public AllRole allRole(ConceptProxy roleTypeProxy, ConceptAssertion roleRestriction) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.ROLE_ALL, this);
        addToDefinitionTree(axiom, roleRestriction);
        axiomParameters.put(axiom.getIndex(), roleTypeProxy);
        return axiom;
    }

    @Override
    public Feature feature(ConceptProxy featureTypeProxy, LiteralAssertion literal) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.FEATURE, this);
        addToDefinitionTree(axiom, literal);
        axiomParameters.put(axiom.getIndex(), featureTypeProxy);
        return axiom;
    }

    @Override
    public SomeRole someRole(ConceptProxy roleTypeProxy, ConceptAssertion roleRestriction) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.ROLE_SOME, this);
        addToDefinitionTree(axiom, roleRestriction);
        axiomParameters.put(axiom.getIndex(), roleTypeProxy);
        return axiom;
    }

    @Override
    public Template template(ConceptProxy templateConcept, ConceptProxy assemblageToPopulateTemplateConcept) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.TEMPLATE, this);
        axiomParameters.put(axiom.getIndex(), new Object[]{templateConcept, assemblageToPopulateTemplateConcept});
        return axiom;
    }

    @Override
    public Or or(Assertion... assertions) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.OR, this);
        addToDefinitionTree(axiom, assertions);
        return axiom;
    }

    @Override
    public BooleanLiteral booleanLiteral(boolean booleanLiteral) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.LITERAL_BOOLEAN, this);
        axiomParameters.put(axiom.getIndex(), booleanLiteral);
        return axiom;
    }

    @Override
    public FloatLiteral floatLiteral(float floatLiteral) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.LITERAL_FLOAT, this);
        axiomParameters.put(axiom.getIndex(), floatLiteral);
        return axiom;
    }

    @Override
    public InstantLiteral instantLiteral(Instant literalValue) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.LITERAL_INSTANT, this);
        axiomParameters.put(axiom.getIndex(), literalValue);
        return axiom;
    }

    @Override
    public IntegerLiteral integerLiteral(int literalValue) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.LITERAL_INTEGER, this);
        axiomParameters.put(axiom.getIndex(), literalValue);
        return axiom;
    }

    @Override
    public StringLiteral stringLiteral(String literalValue) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.LITERAL_STRING, this);
        axiomParameters.put(axiom.getIndex(), literalValue);
        return axiom;
    }

    @Override
    public BooleanSubstitution booleanSubstitution(SubstitutionFieldSpecification fieldSpecification) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.SUBSTITUTION_BOOLEAN, this);
        axiomParameters.put(axiom.getIndex(), fieldSpecification);
        return axiom;
    }

    @Override
    public ConceptSubstitution conceptSubstitution(SubstitutionFieldSpecification fieldSpecification) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.SUBSTITUTION_CONCEPT, this);
        axiomParameters.put(axiom.getIndex(), fieldSpecification);
        return axiom;
    }

    @Override
    public FloatSubstitution floatSubstitution(SubstitutionFieldSpecification fieldSpecification) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.SUBSTITUTION_FLOAT, this);
        axiomParameters.put(axiom.getIndex(), fieldSpecification);
        return axiom;
    }

    @Override
    public InstantSubstitution instantSubstitution(SubstitutionFieldSpecification fieldSpecification) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.SUBSTITUTION_INSTANT, this);
        axiomParameters.put(axiom.getIndex(), fieldSpecification);
        return axiom;
    }

    @Override
    public IntegerSubstitution integerSubstitution(SubstitutionFieldSpecification fieldSpecification) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.SUBSTITUTION_INTEGER, this);
        axiomParameters.put(axiom.getIndex(), fieldSpecification);
        return axiom;
    }

    @Override
    public StringSubstitution stringSubstitution(SubstitutionFieldSpecification fieldSpecification) {
        checkNotBuilt();
        GenericAxiom axiom = new GenericAxiom(NodeSemantic.SUBSTITUTION_STRING, this);
        axiomParameters.put(axiom.getIndex(), fieldSpecification);
        return axiom;
    }

    @Override
    public LogicalExpression build() throws IllegalStateException {
        checkNotBuilt();
        LogicGraph definition = new LogicGraph();
        definition.Root();

        rootSets.forEach((axiom) -> addToDefinition(axiom, definition));

        built = true;
        return definition;
    }

    private void checkNotBuilt() throws IllegalStateException {
        if (built) {
            throw new IllegalStateException("Builder has already built. Builders cannot be reused.");
        }
    }

    private AbstractNode addToDefinition(GenericAxiom axiom, LogicGraph definition) 
        throws IllegalStateException {
        
        AbstractNode newNode;
        switch (axiom.getSemantic()) {
            case NECESSARY_SET:
                newNode = definition.NecessarySet(getChildren(axiom, definition));
                definition.getRoot().addChildren(newNode);
                return newNode;
            case SUFFICIENT_SET:
                newNode = definition.SufficientSet(getChildren(axiom, definition));
                definition.getRoot().addChildren(newNode);
                return newNode;
            case AND:
                return definition.And(getChildren(axiom, definition));
            case OR:
                return definition.Or(getChildren(axiom, definition));
            case FEATURE:
                ConceptProxy featureTypeProxy = (ConceptProxy) axiomParameters.get(axiom.getIndex());
                return definition.Feature(getIdentifierService().getNidForProxy(featureTypeProxy),
                        addToDefinition(definitionTree.get(axiom).get(0), definition));
            case CONCEPT:
                ConceptProxy conceptProxy = (ConceptProxy) axiomParameters.get(axiom.getIndex()); 
                return definition.Concept(getIdentifierService().getConceptSequenceForProxy(conceptProxy));
            case ROLE_ALL:
                ConceptProxy roleTypeProxy = (ConceptProxy) axiomParameters.get(axiom.getIndex());
                return definition.AllRole(getIdentifierService().getNidForProxy(roleTypeProxy), 
                        addToDefinition(definitionTree.get(axiom).get(0), definition));
            case ROLE_SOME:
                roleTypeProxy = (ConceptProxy) axiomParameters.get(axiom.getIndex());
                return definition.SomeRole(getIdentifierService().getNidForProxy(roleTypeProxy), 
                        addToDefinition(definitionTree.get(axiom).get(0), definition));
            case TEMPLATE:
                Object[] params = (Object[]) axiomParameters.get(axiom.getIndex());
                ConceptProxy templateConceptProxy = (ConceptProxy) params[0]; 
                ConceptProxy assemblageToPopulateTemplateConceptProxy = (ConceptProxy) params[1];
                return definition.Template(getIdentifierService().getConceptSequenceForProxy(templateConceptProxy), 
                        getIdentifierService().getConceptSequenceForProxy(assemblageToPopulateTemplateConceptProxy));
            case DISJOINT_WITH:
                ConceptProxy disjointConceptProxy = (ConceptProxy) axiomParameters.get(axiom.getIndex());
                return definition.DisjointWith(definition.Concept(
                        getIdentifierService().getConceptSequenceForProxy(disjointConceptProxy)));
            case LITERAL_BOOLEAN:
                boolean booleanLiteral = (Boolean) axiomParameters.get(axiom.getIndex());
                return definition.BooleanLiteral(booleanLiteral);
            case LITERAL_FLOAT:
                float floatLiteral = (Float) axiomParameters.get(axiom.getIndex());
                return definition.FloatLiteral(floatLiteral);
            case LITERAL_INSTANT:
                Instant instantLiteral = (Instant) axiomParameters.get(axiom.getIndex());
                return definition.InstantLiteral(instantLiteral);
            case LITERAL_INTEGER:
                int integerLiteral = (Integer) axiomParameters.get(axiom.getIndex());
                return definition.IntegerLiteral(integerLiteral);
            case LITERAL_STRING:
                String stringLiteral = (String) axiomParameters.get(axiom.getIndex());
                return definition.StringLiteral(stringLiteral);
            case SUBSTITUTION_BOOLEAN:
                SubstitutionFieldSpecification fieldSpecification = 
                        (SubstitutionFieldSpecification) axiomParameters.get(axiom.getIndex());
                return definition.BooleanSubstitution(fieldSpecification);
            case SUBSTITUTION_CONCEPT:
                fieldSpecification = 
                        (SubstitutionFieldSpecification) axiomParameters.get(axiom.getIndex());
                return definition.ConceptSubstitution(fieldSpecification);
            case SUBSTITUTION_FLOAT:
                fieldSpecification = 
                        (SubstitutionFieldSpecification) axiomParameters.get(axiom.getIndex());
                return definition.FloatSubstitution(fieldSpecification);
            case SUBSTITUTION_INSTANT:
                fieldSpecification = 
                        (SubstitutionFieldSpecification) axiomParameters.get(axiom.getIndex());
                return definition.InstantSubstitution(fieldSpecification);
            case SUBSTITUTION_INTEGER:
                fieldSpecification = 
                        (SubstitutionFieldSpecification) axiomParameters.get(axiom.getIndex());
                return definition.IntegerSubstitution(fieldSpecification);
            case SUBSTITUTION_STRING:
                fieldSpecification = 
                        (SubstitutionFieldSpecification) axiomParameters.get(axiom.getIndex());
                return definition.StringSubstitution(fieldSpecification);
            default:
                throw new UnsupportedOperationException("Can't handle: " + axiom.getSemantic());

        }
    }

    protected AbstractNode[] getChildren(GenericAxiom axiom, LogicGraph definition) {
        List<GenericAxiom> childrenAxioms = definitionTree.get(axiom);
        List<AbstractNode> children = new ArrayList<>(childrenAxioms.size());
        childrenAxioms.forEach((childAxiom) -> children.add(addToDefinition(childAxiom, definition)));
        return children.toArray(new AbstractNode[children.size()]);
    }

}