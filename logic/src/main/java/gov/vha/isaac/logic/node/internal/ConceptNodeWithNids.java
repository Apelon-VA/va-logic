package gov.vha.isaac.logic.node.internal;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.node.AbstractNode;
import gov.vha.isaac.logic.node.external.ConceptNodeWithUuids;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ihtsdo.otf.tcc.api.uuid.UuidT5Generator;

/**
 * Created by kec on 12/10/14.
 */
public final class ConceptNodeWithNids extends AbstractNode {

    int conceptNid;

    public ConceptNodeWithNids(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
        conceptNid = dataInputStream.readInt();
    }

    public ConceptNodeWithNids(LogicGraph logicGraphVersion, int conceptNid) {
        super(logicGraphVersion);
        this.conceptNid = conceptNid;

    }
   public ConceptNodeWithNids(ConceptNodeWithUuids externalForm) {
       super(externalForm);
        try {
            this.conceptNid = getIsaacDb().get().getNidForUuids(externalForm.getConceptUuid());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    
    public int getConceptNid() {
        return conceptNid;
    }

    @Override
    public void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        switch (dataTarget) {
            case EXTERNAL:
                ConceptNodeWithUuids externalForm = new ConceptNodeWithUuids(this);
                externalForm.writeNodeData(dataOutput, dataTarget);
                break;
            case INTERNAL:
                super.writeData(dataOutput, dataTarget);
                dataOutput.writeInt(conceptNid);
                break;
            default: throw new UnsupportedOperationException("Can't handle dataTarget: " + dataTarget);
        }
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.CONCEPT;
    }

    @Override
    protected UUID initNodeUuid() {
        if (getIsaacDb().isPresent()) {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        getIsaacDb().get().getUuidPrimordialForNid(conceptNid).toString());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
     }
        return null;
     }
    
    

    @Override
    public AbstractNode[] getChildren() {
        return new AbstractNode[0];
    }

    @Override
    public final void addChildren(Node... children) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "ConceptNode[" + getNodeIndex() + "]: \"" + getConceptChronicleText(conceptNid) + "\"" + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ConceptNodeWithNids that = (ConceptNodeWithNids) o;

        return conceptNid == that.conceptNid;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + conceptNid;
        return result;
    }
    

    @Override
    protected int compareFields(Node o) {
        return conceptNid - ((ConceptNodeWithNids) o).getConceptNid();
    }
    
}