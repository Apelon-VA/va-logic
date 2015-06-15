package gov.vha.isaac.logic;

import gov.vha.isaac.logic.node.internal.RoleNodeAllWithNids;
import gov.vha.isaac.logic.node.internal.FeatureNodeWithNids;
import gov.vha.isaac.logic.node.internal.RoleNodeSomeWithNids;
import gov.vha.isaac.logic.node.internal.TemplateNodeWithNids;
import gov.vha.isaac.logic.node.internal.ConceptNodeWithNids;
import gov.vha.isaac.logic.node.*;
import gov.vha.isaac.logic.node.external.ConceptNodeWithUuids;
import gov.vha.isaac.logic.node.external.FeatureNodeWithUuids;
import gov.vha.isaac.logic.node.external.RoleNodeAllWithUuids;
import gov.vha.isaac.logic.node.external.RoleNodeSomeWithUuids;
import gov.vha.isaac.logic.node.external.TemplateNodeWithUuids;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.DataSource;
import gov.vha.isaac.ochre.api.DataTarget;
import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.SubstitutionFieldSpecification;
import gov.vha.isaac.ochre.api.tree.TreeNodeVisitData;
import gov.vha.isaac.ochre.collections.ConceptSequenceSet;
import org.apache.mahout.math.map.OpenIntObjectHashMap;
import org.ihtsdo.otf.tcc.api.conattr.ConceptAttributeVersionBI;
import org.ihtsdo.otf.tcc.api.contradiction.ContradictionException;
import org.ihtsdo.otf.tcc.api.relationship.RelationshipVersionBI;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import org.apache.mahout.math.list.IntArrayList;
import org.ihtsdo.otf.tcc.api.concept.ConceptVersionBI;
import org.ihtsdo.otf.tcc.api.relationship.RelAssertionType;

/**
 * Created by kec on 12/6/14. 
 * 
 * TODO need version of Pack that uses UUIDs for
 * change sets 
 * 
 * TODO need unique way of identifying data columns for
 * substitution: Use enumerations for now 
 * 
 * TODO Standard refset for never grouped roles 
 * 
 * TODO Standard refset for right identities
 * @deprecated moved to ochre model project
 */
public class LogicGraph implements LogicalExpression {

    private static IdentifierService idService;
    private static IdentifierService getIdentifierService() {
        if (idService == null) {
            idService = LookupService.getService(IdentifierService.class);
        }
        return idService;
    }

    private static final NodeSemantic[] NODE_SEMANTICS = NodeSemantic.values();
    
    private static final EnumSet<NodeSemantic> meaningfulNodeSemantics
            = EnumSet.of(NodeSemantic.CONCEPT, NodeSemantic.SUBSTITUTION_CONCEPT);

    protected static int isaNid = 0;
    
    transient int conceptSequence = -1;

    ArrayList<Node> nodes = new ArrayList<>();

    public LogicGraph() {
    }
    
    public LogicGraph(byte[][] nodeDataArray, DataSource dataSource, int conceptSequence) {
        this(nodeDataArray, dataSource);
        this.conceptSequence = conceptSequence;
    }

    public LogicGraph(byte[][] nodeDataArray, DataSource dataSource) {
        try {
            nodes = new ArrayList<>(nodeDataArray.length);
            for (byte[] nodeDataArray1 : nodeDataArray) {
                DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(nodeDataArray1));
                byte nodeSemanticIndex = dataInputStream.readByte();
                NodeSemantic nodeSemantic = NODE_SEMANTICS[nodeSemanticIndex];
                switch (nodeSemantic) {
                    case DEFINITION_ROOT:
                        Root(dataInputStream);
                        break;
                    case NECESSARY_SET:
                        NecessarySet(dataInputStream);
                        break;
                    case SUFFICIENT_SET:
                        SufficientSet(dataInputStream);
                        break;
                    case AND:
                        And(dataInputStream);
                        break;
                    case OR:
                        Or(dataInputStream);
                        break;
                    case DISJOINT_WITH:
                        DisjointWith(dataInputStream);
                        break;
                    case ROLE_ALL:
                        switch (dataSource) {
                            case EXTERNAL:
                                AllRoleWithUuids(dataInputStream);
                            case INTERNAL:
                                AllRole(dataInputStream);
                                break;
                            default:
                                throw new UnsupportedOperationException("Can't handle: " + dataSource);
                        }

                        break;
                    case ROLE_SOME:
                        switch (dataSource) {
                            case EXTERNAL:
                                SomeRoleWithUuids(dataInputStream);
                            case INTERNAL:
                                SomeRole(dataInputStream);
                                break;
                            default:
                                throw new UnsupportedOperationException("Can't handle: " + dataSource);
                        }
                        
                        break;
                    case FEATURE:
                        switch (dataSource) {
                            case EXTERNAL:
                                FeatureWithUuids(dataInputStream);
                            case INTERNAL:
                                Feature(dataInputStream);
                                break;
                            default:
                                throw new UnsupportedOperationException("Can't handle: " + dataSource);
                        }
                        
                        break;
                    case LITERAL_BOOLEAN:
                        BooleanLiteral(dataInputStream);
                        break;
                    case LITERAL_FLOAT:
                        FloatLiteral(dataInputStream);
                        break;
                    case LITERAL_INSTANT:
                        InstantLiteral(dataInputStream);
                        break;
                    case LITERAL_INTEGER:
                        IntegerLiteral(dataInputStream);
                        break;
                    case LITERAL_STRING:
                        StringLiteral(dataInputStream);
                        break;
                    case CONCEPT:
                        switch (dataSource) {
                            case EXTERNAL:
                                ConceptWithUuids(dataInputStream);
                            case INTERNAL:
                                Concept(dataInputStream);
                                break;
                            default:
                                throw new UnsupportedOperationException("Can't handle: " + dataSource);
                        }
                        
                        break;
                    case TEMPLATE:
                        switch (dataSource) {
                            case EXTERNAL:
                                TemplateWithUuids(dataInputStream);
                            case INTERNAL:
                                Template(dataInputStream);
                                break;
                            default:
                                throw new UnsupportedOperationException("Can't handle: " + dataSource);
                        }
                        
                        break;
                    case SUBSTITUTION_BOOLEAN:
                        BooleanSubstitution(dataInputStream);
                        break;
                    case SUBSTITUTION_CONCEPT:
                        ConceptSubstitution(dataInputStream);
                        break;
                    case SUBSTITUTION_FLOAT:
                        FloatSubstitution(dataInputStream);
                        break;
                    case SUBSTITUTION_INSTANT:
                        InstantSubstitution(dataInputStream);
                        break;
                    case SUBSTITUTION_INTEGER:
                        IntegerSubstitution(dataInputStream);
                        break;
                    case SUBSTITUTION_STRING:
                        StringSubstitution(dataInputStream);
                        break;
                    default:
                        throw new UnsupportedOperationException("Can't handle: " + nodeSemantic);
                }
            }
            nodes.trimToSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public LogicGraph(ConceptVersionBI conceptVersion,
            ConceptSequenceSet roleConceptSequences,
            ConceptSequenceSet featureConceptSequences,
            ConceptSequenceSet neverRoleGroupConceptSequences,
            int roleGroupNid) throws IOException, ContradictionException {
        setupIsa();
        RelAssertionType relAssertionType = conceptVersion.getViewCoordinate().getRelationshipAssertionType();
        ConceptAttributeVersionBI cav = conceptVersion.getConceptAttributes().getVersion(conceptVersion.getViewCoordinate());
        if (cav == null) {
            throw new IllegalStateException("No attributes on concept: \n"
                    + conceptVersion.toLongString());
        }
        if (!cav.isActive()) {
            conceptVersion.isActive();
            cav.isActive();
            throw new IllegalStateException("Trying to create logic graph on inactive concept: \n"
                    + conceptVersion.toLongString());
        }
        RootNode root = getRoot();
        AndNode andNode = And();
        if (cav.isDefined()) {
            root.addChildren(SufficientSet(andNode));
        } else {
            root.addChildren(NecessarySet(andNode));
        }
        OpenIntObjectHashMap<List<RelationshipVersionBI>> relGroupMap = new OpenIntObjectHashMap<>();
        for (RelationshipVersionBI relVersion : conceptVersion.getRelationshipsOutgoingActive()) {
            if ((relAssertionType == RelAssertionType.STATED && relVersion.isStated())
                    || (relAssertionType == RelAssertionType.INFERRED && relVersion.isInferred())) {
                if (relVersion.getGroup() == 0) {
                    int typeNid = relVersion.getTypeNid();
                    int typeSequence = getIdentifierService().getConceptSequence(typeNid);
                    if (isaNid == typeNid) {
                        andNode.addChildren(Concept(relVersion.getDestinationNid()));
                    } else if (roleConceptSequences.contains(typeSequence)) {
                        if (neverRoleGroupConceptSequences.contains(typeSequence)) {
                            andNode.addChildren(SomeRole(relVersion.getTypeNid(), Concept(relVersion.getDestinationNid())));
                        } else {
                            andNode.addChildren(SomeRole(roleGroupNid,
                                    SomeRole(relVersion.getTypeNid(), Concept(relVersion.getDestinationNid()))));
                        }
                    }
                } else {
                    if (!relGroupMap.containsKey(relVersion.getGroup())) {
                        relGroupMap.put(relVersion.getGroup(), new ArrayList<>());
                    }
                    relGroupMap.get(relVersion.getGroup()).add(relVersion);
                }
            }
        }
        // process rel groups here...
        relGroupMap.forEachPair((group, relsInGroupList) -> {
            AndNode roleGroupAndNode = And();
            andNode.addChildren(SomeRole(roleGroupNid, roleGroupAndNode));
            relsInGroupList.stream().forEach((rel) -> {
                roleGroupAndNode.addChildren(SomeRole(rel.getTypeNid(), Concept(rel.getDestinationNid())));
            });
            return true;
        });
    }

    private static void setupIsa() {
        if (isaNid == 0) {
            isaNid = IsaacMetadataAuxiliaryBinding.IS_A.getNid();
        }
    }

    public boolean isMeaningful() {
        return nodes.stream().anyMatch((node) -> (meaningfulNodeSemantics.contains(node.getNodeSemantic())));
    }

    public int getConceptSequence() {
        return conceptSequence;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public final RootNode getRoot() {
        if (nodes.isEmpty()) {
            return Root();
        }
        return (RootNode) nodes.get(0);
    }

    public Node getNode(int nodeIndex) {
        return nodes.get(nodeIndex);
    }

    @Override
    public byte[][] getData(DataTarget dataTarget) {
        return pack(dataTarget);
    }

    public byte[][] pack(DataTarget dataTarget) {
        init();
        byte[][] byteArrayArray = new byte[nodes.size()][];
        for (int index = 0; index < byteArrayArray.length; index++) {
            byteArrayArray[index] = nodes.get(index).getBytes(dataTarget);
        }
        return byteArrayArray;
    }

    protected void init() {
        nodes.trimToSize();
    }

    public void addNode(Node node) {
        node.setNodeIndex((short) nodes.size());
        nodes.add(node);
    }

    public void processDepthFirst(BiConsumer<Node, TreeNodeVisitData> consumer) {
        init();
        TreeNodeVisitData graphVisitData = new TreeNodeVisitData(nodes.size());
        depthFirstVisit(consumer, getRoot(), graphVisitData, 0);
    }

    private void depthFirstVisit(BiConsumer<Node, TreeNodeVisitData> consumer, Node node,
            TreeNodeVisitData graphVisitData, int depth) {

        if (depth > 100) {
            System.out.println("Depth limit exceeded for node: " + node);
            return;
        }
        graphVisitData.startNodeVisit(node.getNodeIndex(), depth);
        if (consumer != null) {
            consumer.accept(node, graphVisitData);
        }
        for (Node child : node.getChildren()) {
            depthFirstVisit(consumer, child, graphVisitData, depth + 1);
        }
        graphVisitData.endNodeVisit(node.getNodeIndex());
    }

    public final NecessarySetNode NecessarySet(AbstractNode... children) {
        return new NecessarySetNode(this, children);
    }
    public final NecessarySetNode NecessarySet(DataInputStream dataInputStream) throws IOException {
        return new NecessarySetNode(this, dataInputStream);
    }
    public final SufficientSetNode SufficientSet(AbstractNode... children) {
        return new SufficientSetNode(this, children);
    }
    public final SufficientSetNode SufficientSet(DataInputStream dataInputStream) throws IOException {
        return new SufficientSetNode(this, dataInputStream);
    }
    public final AndNode And(AbstractNode... children) {
        return new AndNode(this, children);
    }
    public final AndNode And(DataInputStream dataInputStream) throws IOException {
        return new AndNode(this, dataInputStream);
    }

    public OrNode Or(AbstractNode... children) {
        return new OrNode(this, children);
    }
    public final OrNode Or(DataInputStream dataInputStream) throws IOException {
        return new OrNode(this, dataInputStream);
    }


    public RootNode Root(ConnectorNode... children) {
        return new RootNode(this, children);
    }

    public final RootNode Root(DataInputStream dataInputStream) throws IOException {
        return new RootNode(this, dataInputStream);
    }

    public DisjointWithNode DisjointWith(AbstractNode... children) {
        return new DisjointWithNode(this, children);
    }
    public final DisjointWithNode DisjointWith(DataInputStream dataInputStream) throws IOException {
        return new DisjointWithNode(this, dataInputStream);
    }

    public RoleNodeAllWithNids AllRole(int typeNid, AbstractNode restriction) {
        return new RoleNodeAllWithNids(this, typeNid, restriction);
    }
    public final RoleNodeAllWithNids AllRole(DataInputStream dataInputStream) throws IOException {
        return new RoleNodeAllWithNids(this, dataInputStream);
    }

    public final RoleNodeAllWithUuids AllRoleWithUuids(DataInputStream dataInputStream) throws IOException {
        return new RoleNodeAllWithUuids(this, dataInputStream);
    }


    public final RoleNodeSomeWithNids SomeRole(int typeNid, AbstractNode restriction) {
        return new RoleNodeSomeWithNids(this, typeNid, restriction);
    }
    public final RoleNodeSomeWithNids SomeRole(DataInputStream dataInputStream) throws IOException {
        return new RoleNodeSomeWithNids(this, dataInputStream);
    }

    public final RoleNodeSomeWithUuids SomeRoleWithUuids(DataInputStream dataInputStream) throws IOException {
        return new RoleNodeSomeWithUuids(this, dataInputStream);
    }

    public FeatureNodeWithNids Feature(int typeNid, AbstractNode literal) {
        // check for LiteralNode or SubstitutionNodeLiteral
        if ((literal instanceof LiteralNode) || (
                literal instanceof SubstitutionNodeLiteral)) {
        return new FeatureNodeWithNids(this, typeNid, literal);
        }
        throw new IllegalStateException(
                "Node must be of type LiteralNode or SubstitutionNodeLiteral. Found: " + 
                        literal);
    }
    public final FeatureNodeWithNids Feature(DataInputStream dataInputStream) throws IOException {
        return new FeatureNodeWithNids(this, dataInputStream);
    }

    public final FeatureNodeWithUuids FeatureWithUuids(DataInputStream dataInputStream) throws IOException {
        return new FeatureNodeWithUuids(this, dataInputStream);
    }

    public LiteralNodeBoolean BooleanLiteral(boolean literalValue) {
        return new LiteralNodeBoolean(this, literalValue);
    }
    public final LiteralNodeBoolean BooleanLiteral(DataInputStream dataInputStream) throws IOException {
        return new LiteralNodeBoolean(this, dataInputStream);
    }

    public LiteralNodeFloat FloatLiteral(float literalValue) {
        return new LiteralNodeFloat(this, literalValue);
    }
    public final LiteralNodeFloat FloatLiteral(DataInputStream dataInputStream) throws IOException {
        return new LiteralNodeFloat(this, dataInputStream);
    }


    public LiteralNodeInstant InstantLiteral(Instant literalValue) {
        return new LiteralNodeInstant(this, literalValue);
    }
    public final LiteralNodeInstant InstantLiteral(DataInputStream dataInputStream) throws IOException {
        return new LiteralNodeInstant(this, dataInputStream);
    }

    public LiteralNodeInteger IntegerLiteral(int literalValue) {
        return new LiteralNodeInteger(this, literalValue);
    }
    public final LiteralNodeInteger IntegerLiteral(DataInputStream dataInputStream) throws IOException {
        return new LiteralNodeInteger(this, dataInputStream);
    }

    public LiteralNodeString StringLiteral(String literalValue) {
        return new LiteralNodeString(this, literalValue);
    }
    public final LiteralNodeString StringLiteral(DataInputStream dataInputStream) throws IOException {
        return new LiteralNodeString(this, dataInputStream);
    }


    public final ConceptNodeWithNids Concept(int conceptSequence) {
        return new ConceptNodeWithNids(this, conceptSequence);
    }
    public final ConceptNodeWithNids Concept(DataInputStream dataInputStream) throws IOException {
        return new ConceptNodeWithNids(this, dataInputStream);
    }


    public final ConceptNodeWithUuids ConceptWithUuids(DataInputStream dataInputStream) throws IOException {
        return new ConceptNodeWithUuids(this, dataInputStream);
    }


    public TemplateNodeWithNids Template(int templateConceptSequence, int assemblageConceptSequence) {
        return new TemplateNodeWithNids(this, templateConceptSequence, assemblageConceptSequence);
    }
    public final TemplateNodeWithNids Template(DataInputStream dataInputStream) throws IOException {
        return new TemplateNodeWithNids(this, dataInputStream);
    }

    public final TemplateNodeWithUuids TemplateWithUuids(DataInputStream dataInputStream) throws IOException {
        return new TemplateNodeWithUuids(this, dataInputStream);
    }

    public SubstitutionNodeBoolean BooleanSubstitution(SubstitutionFieldSpecification substitutionFieldSpecification) {
        return new SubstitutionNodeBoolean(this, substitutionFieldSpecification);
    }
    public final SubstitutionNodeBoolean BooleanSubstitution(DataInputStream dataInputStream) throws IOException {
        return new SubstitutionNodeBoolean(this, dataInputStream);
    }


    public SubstitutionNodeConcept ConceptSubstitution(SubstitutionFieldSpecification substitutionFieldSpecification) {
        return new SubstitutionNodeConcept(this, substitutionFieldSpecification);
    }
    
    public final SubstitutionNodeConcept ConceptSubstitution(DataInputStream dataInputStream) throws IOException {
        return new SubstitutionNodeConcept(this, dataInputStream);
    }


    public SubstitutionNodeFloat FloatSubstitution(SubstitutionFieldSpecification substitutionFieldSpecification) {
        return new SubstitutionNodeFloat(this, substitutionFieldSpecification);
    }

    public final SubstitutionNodeFloat FloatSubstitution(DataInputStream dataInputStream) throws IOException {
        return new SubstitutionNodeFloat(this, dataInputStream);
    }

    public SubstitutionNodeInstant InstantSubstitution(SubstitutionFieldSpecification substitutionFieldSpecification) {
        return new SubstitutionNodeInstant(this, substitutionFieldSpecification);
    }

    public final SubstitutionNodeInstant InstantSubstitution(DataInputStream dataInputStream) throws IOException {
        return new SubstitutionNodeInstant(this, dataInputStream);
    }

    public SubstitutionNodeInteger IntegerSubstitution(SubstitutionFieldSpecification substitutionFieldSpecification) {
        return new SubstitutionNodeInteger(this, substitutionFieldSpecification);
    }

    public final SubstitutionNodeInteger IntegerSubstitution(DataInputStream dataInputStream) throws IOException {
        return new SubstitutionNodeInteger(this, dataInputStream);
    }

    public SubstitutionNodeString StringSubstitution(SubstitutionFieldSpecification substitutionFieldSpecification) {
        return new SubstitutionNodeString(this, substitutionFieldSpecification);
    }

    public final SubstitutionNodeString StringSubstitution(DataInputStream dataInputStream) throws IOException {
        return new SubstitutionNodeString(this, dataInputStream);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        processDepthFirst((Node node, TreeNodeVisitData graphVisitData) -> {
            for (int i = 0; i < graphVisitData.getDistance(node.getNodeIndex()); i++) {
                builder.append("    ");
            }
            builder.append(node);
            builder.append("\n");
        });
        builder.append(" \n\n");
        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LogicGraph other = (LogicGraph) obj;
        if (this.nodes == other.nodes) {
            return true;
        }

        if (this.nodes != null) {
            if (this.nodes.size() != other.nodes.size()) {
                return false;
            }
            TreeNodeVisitData graphVisitData = new TreeNodeVisitData(nodes.size());
            depthFirstVisit(null, getRoot(), graphVisitData, 0);

            return graphsEqual(this.getRoot(), other.getRoot(), 0, graphVisitData.getMaxDepth());
        }
        return true;
    }

    public int[] maximalCommonSubgraph(LogicGraph another) {
        TreeNodeVisitData graphVisitData = new TreeNodeVisitData(nodes.size());
        depthFirstVisit(null, getRoot(), graphVisitData, 0);
        int[] solution = new int[this.nodes.size()];
        Arrays.fill(solution, -1);
        maximalCommonSubgraph(this.getRoot(), another.getRoot(), 0, graphVisitData.getMaxDepth(), solution);
        return solution;

    }

    /**
     * Needs more work, use 02072094-c72c-3da4-8361-60f450ff6b2f, Insertion of
     * graft into lower eyelid for testing.
     *
     * @param n1
     * @param n2
     * @param depth
     * @param maxDepth
     * @param solution
     */
    private void maximalCommonSubgraph(AbstractNode n1, AbstractNode n2, int depth, int maxDepth, int[] solution) {
        if (n1.equals(n2)) {
            solution[n1.getNodeIndex()] = n2.getNodeIndex();
            int score = scoreSolution(solution);
            AbstractNode[] n1children = n1.getChildren();
            AbstractNode[] n2children = n2.getChildren();
            if (n1children.length == 0 || n2children.length == 0) {
                return;
            }
            if (n1children.length == 1 && n2children.length == 1) {
                maximalCommonSubgraph(n1children[0], n2children[0], depth + 1, maxDepth, solution);
                return;
            }
            HashMap<Set<UUID>, IntArrayList> uuidSetNodeListMap = new HashMap<>();
            int depthToTest = 0;
            while (uuidSetNodeListMap.size() < n1children.length && depthToTest < maxDepth - depth) {
                depthToTest++;
                uuidSetNodeListMap.clear();
                for (AbstractNode child : n1children) {
                    Set<UUID> nodeUuidSetForDepth = child.getNodeUuidSetForDepth(depthToTest);
                    if (!uuidSetNodeListMap.containsKey(nodeUuidSetForDepth)) {
                        IntArrayList nodeList = new IntArrayList();
                        nodeList.add(child.getNodeIndex());
                        uuidSetNodeListMap.put(nodeUuidSetForDepth, nodeList);
                    } else {
                        uuidSetNodeListMap.get(nodeUuidSetForDepth).add(child.getNodeIndex());
                    }
                }
            }

            for (AbstractNode n2Child : n2children) {
                Set<UUID> nodeUuidSetForDepth = n2Child.getNodeUuidSetForDepth(depthToTest);
                IntArrayList possibleMatches = uuidSetNodeListMap.get(nodeUuidSetForDepth);
                int[] bestSolution = Arrays.copyOf(solution, solution.length);
                int bestSolutionIndex = -1;
                if (possibleMatches != null) {
                    for (int possibleMatchIndex : possibleMatches.elements()) {
                        int[] testSolution = Arrays.copyOf(solution, solution.length);
                        maximalCommonSubgraph((AbstractNode) this.nodes.get(possibleMatchIndex), n2Child, depth + 1, maxDepth, testSolution);
                        if (scoreSolution(testSolution) > scoreSolution(bestSolution)) {
                            bestSolution = testSolution;
                            bestSolutionIndex = possibleMatchIndex;
                        }
                    }
                    if (bestSolutionIndex != -1 && bestSolution[bestSolutionIndex] != -1) {
                        possibleMatches.delete(bestSolution[bestSolutionIndex]);
                        System.arraycopy(bestSolution, 0, solution, 0, solution.length);
                    }
                }
            }
        }
    }

    private int scoreSolution(int[] solution) {
        int score = 0;
        for (int solutionIndex : solution) {
            if (solutionIndex != -1) {
                score++;
            }
        }
        return score;
    }

    private boolean graphsEqual(AbstractNode g1, AbstractNode g2, int depth, int maxDepth) {
        if (g1.equals(g2)) {
            AbstractNode[] g1children = g1.getChildren();
            AbstractNode[] g2children = g2.getChildren();
            if (g1children.length != g2children.length) {
                return false;
            }
            if (g1children.length == 0) {
                return true;
            }

            HashMap<Set<UUID>, IntArrayList> uuidSetNodeListMap = new HashMap<>();
            int depthToTest = 0;
            while (uuidSetNodeListMap.size() < g1children.length && depthToTest < maxDepth - depth) {
                depthToTest++;
                uuidSetNodeListMap.clear();
                for (AbstractNode child : g1children) {
                    Set<UUID> nodeUuidSetForDepth = child.getNodeUuidSetForDepth(depthToTest);
                    if (!uuidSetNodeListMap.containsKey(nodeUuidSetForDepth)) {
                        IntArrayList nodeList = new IntArrayList();
                        nodeList.add(child.getNodeIndex());
                        uuidSetNodeListMap.put(nodeUuidSetForDepth, nodeList);
                    } else {
                        uuidSetNodeListMap.get(nodeUuidSetForDepth).add(child.getNodeIndex());
                    }

                }
            }
            // need to try all combinations
            for (AbstractNode g2Child : g2children) {
                Set<UUID> nodeUuidSetForDepth = g2Child.getNodeUuidSetForDepth(depthToTest);
                IntArrayList possibleMatches = uuidSetNodeListMap.get(nodeUuidSetForDepth);
                if (possibleMatches == null) {
                    return false;
                }
                int match = -1;
                for (int possibleMatchIndex : possibleMatches.elements()) {
                    if (graphsEqual((AbstractNode) this.nodes.get(possibleMatchIndex), g2Child, depth + 1, maxDepth)) {
                        match = possibleMatchIndex;
                        break;
                    }
                }
                if (match == -1) {
                    return false;
                }
                possibleMatches.delete(match);
            }
            return true;
        }
        return false;
    }

}
