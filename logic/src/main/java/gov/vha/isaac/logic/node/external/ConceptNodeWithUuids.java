/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.logic.node.external;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.node.AbstractNode;
import gov.vha.isaac.logic.node.internal.ConceptNodeWithNids;
import gov.vha.isaac.ochre.api.DataTarget;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import gov.vha.isaac.ochre.util.UuidT5Generator;

/**
 *
 * @author kec
 * @deprecated moved to ochre model project
 */
@Deprecated
public class ConceptNodeWithUuids extends AbstractNode {

    UUID conceptUuid;

    public ConceptNodeWithUuids(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
        conceptUuid = new UUID(dataInputStream.readLong(), dataInputStream.readLong());
    }

    public ConceptNodeWithUuids(LogicGraph logicGraphVersion, UUID conceptUuid) {
        super(logicGraphVersion);
        this.conceptUuid = conceptUuid;

    }

    public ConceptNodeWithUuids(ConceptNodeWithNids internalForm) {
        super(internalForm);
        this.conceptUuid = getIdentifierService().get().getUuidPrimordialForNid(internalForm.getConceptNid()).get();

    }

    public UUID getConceptUuid() {
        return conceptUuid;
    }

    @Override
    public void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        super.writeData(dataOutput, dataTarget);
        switch (dataTarget) {
            case EXTERNAL:
                dataOutput.writeLong(conceptUuid.getMostSignificantBits());
                dataOutput.writeLong(conceptUuid.getLeastSignificantBits());
                break;
            case INTERNAL:
                ConceptNodeWithNids internalForm =  new ConceptNodeWithNids(this);
                internalForm.writeNodeData(dataOutput, dataTarget);
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
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        conceptUuid.toString());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
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
        return "ConceptNode[" + getNodeIndex() + "]: \"" + getConceptChronicleText(conceptUuid) + "\"" + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ConceptNodeWithUuids that = (ConceptNodeWithUuids) o;

        return conceptUuid.equals(that.conceptUuid);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + conceptUuid.hashCode();
        return result;
    }
    

    @Override
    protected int compareFields(Node o) {
        return conceptUuid.compareTo(((ConceptNodeWithUuids) o).conceptUuid);
    }
    
}
