package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.*;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.ihtsdo.otf.tcc.api.concept.ConceptChronicleBI;
import org.ihtsdo.otf.tcc.api.store.TerminologyStoreDI;
import org.ihtsdo.otf.tcc.lookup.Hk2Looker;

/**
 * Created by kec on 12/10/14.
 */
public abstract class AbstractNode implements Node, Comparable<Node> {

    protected static final UUID namespaceUuid = UUID.fromString("d64c6d91-a37d-11e4-bcd8-0800200c9a66");

    static TerminologyStoreDI isaacDb = null;

    LogicGraph logicGraphVersion;
    private short nodeIndex = Short.MIN_VALUE;
    protected UUID nodeUuid = null;
    

    public AbstractNode(LogicGraph logicGraphVersion) {
        this.logicGraphVersion = logicGraphVersion;
        logicGraphVersion.addNode(this);
    }

    public AbstractNode(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        nodeIndex = dataInputStream.readShort();
        this.logicGraphVersion = logicGraphVersion;
        logicGraphVersion.addNode(this);
    }
    
    protected AbstractNode(AbstractNode anotherNode) {
        this.nodeIndex = anotherNode.nodeIndex;
        this.nodeUuid = anotherNode.nodeUuid;
    }
 
    protected Optional<TerminologyStoreDI> getIsaacDb() {
        if (isaacDb == null) {
            isaacDb = Hk2Looker.getService(TerminologyStoreDI.class);
        }
        return Optional.ofNullable(isaacDb);
    }

    public String getConceptChronicleText(UUID conceptUuid) {

        if (getIsaacDb().isPresent()) {
            try {
                ConceptChronicleBI cc = getIsaacDb().get().getConcept(conceptUuid);
                return cc.toString();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return conceptUuid.toString();
    }
    public String getConceptChronicleText(int conceptNid) {

        if (getIsaacDb().isPresent()) {
            try {
                ConceptChronicleBI cc = getIsaacDb().get().getConcept(conceptNid);
                return cc.toString();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return Integer.toString(conceptNid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return !(o == null || getClass() != o.getClass());
    }

    @Override
    public int hashCode() {
        return (int) nodeIndex;
    }

    @Override
    public byte[] getBytes(DataTarget dataTarget) {
        try {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                DataOutputStream output = new DataOutputStream(outputStream);
                output.writeByte(getNodeSemantic().ordinal());
                output.writeShort(nodeIndex);
                writeNodeData(output, dataTarget);
                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public short getNodeIndex() {
        return nodeIndex;
    }

    @Override
    public void setNodeIndex(short nodeIndex) {
        if (this.nodeIndex == Short.MIN_VALUE) {
            this.nodeIndex = nodeIndex;
        } else if (this.nodeIndex == nodeIndex) {
            // nothing to do...
        } else {
            throw new IllegalStateException("Node index cannot be changed once set. NodeId: "
                    + this.nodeIndex + " attempted: " + nodeIndex);
        }
    }

    protected void writeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
    }

    ;

    protected abstract void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException;

    @Override
    public String toString() {
        return "";
    }

    @Override
    public int compareTo(Node o) {
        if (this.getNodeSemantic() != o.getNodeSemantic()) {
            return this.getNodeSemantic().compareTo(o.getNodeSemantic());
        }
        return compareFields(o);
     }

    protected abstract int compareFields(Node o);

    @Override
    public abstract AbstractNode[] getChildren();
    
    protected UUID getNodeUuid() {
        if (nodeUuid == null) {
            nodeUuid = initNodeUuid();
        }
        return nodeUuid;
    };
    
    public SortedSet<UUID> getNodeUuidSetForDepth(int depth) {
        SortedSet<UUID> uuidSet = new TreeSet<>();
        uuidSet.add(getNodeUuid());
        if (depth > 1) {
            for (AbstractNode child: getChildren()) {
                uuidSet.addAll(child.getNodeUuidSetForDepth(depth - 1));
            }
        }
        return uuidSet;
    }
    
    protected abstract UUID initNodeUuid();
}
