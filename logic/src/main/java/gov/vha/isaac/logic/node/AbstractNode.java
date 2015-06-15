package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import static gov.vha.isaac.logic.node.AbstractNode.conceptService;
import gov.vha.isaac.ochre.api.DataTarget;
import gov.vha.isaac.ochre.api.IdentifierService;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.chronicle.StampedVersion;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.component.concept.ConceptService;

import java.io.*;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Created by kec on 12/10/14.
 * @deprecated moved to ochre model project
 */
public abstract class AbstractNode implements Node, Comparable<Node> {

    protected static final UUID namespaceUuid = UUID.fromString("d64c6d91-a37d-11e4-bcd8-0800200c9a66");

    static ConceptService conceptService = null;
    static IdentifierService identifierService = null;
    protected Optional<IdentifierService> getIdentifierService() {
        if (identifierService == null) {
            identifierService = LookupService.getService(IdentifierService.class);
        }
        return Optional.ofNullable(identifierService);
    } 
 
    protected Optional<ConceptService> getConceptService() {
        if (conceptService == null) {
            conceptService = LookupService.getService(ConceptService.class);
        }
        return Optional.ofNullable(conceptService);
    }

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

    public String getConceptChronicleText(UUID conceptUuid) {

        if (getConceptService().isPresent()) {
            ConceptChronology<? extends StampedVersion> cc =
                    getConceptService().get().getConcept(conceptUuid);
            return cc.toUserString();
        }
        return conceptUuid.toString();
    }
    public String getConceptChronicleText(int conceptNid) {
        if (getConceptService().isPresent()) {
            ConceptChronology<? extends StampedVersion> cc =
                    getConceptService().get().getConcept(conceptNid);
            return cc.toUserString();
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
