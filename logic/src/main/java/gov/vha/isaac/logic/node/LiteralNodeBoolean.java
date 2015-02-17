package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.ihtsdo.otf.tcc.api.uuid.UuidT5Generator;

/**
 * Created by kec on 12/10/14.
 */
public class LiteralNodeBoolean extends LiteralNode {

    boolean literalValue;

    public LiteralNodeBoolean(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
        literalValue = dataInputStream.readBoolean();
    }

    public LiteralNodeBoolean(LogicGraph logicGraphVersion, boolean literalValue) {
        super(logicGraphVersion);
        this.literalValue = literalValue;
    }


    public boolean getLiteralValue() {
        return literalValue;
    }
    @Override
    protected void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        super.writeData(dataOutput, dataTarget);
        dataOutput.writeBoolean(literalValue);
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.LITERAL_BOOLEAN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        LiteralNodeBoolean that = (LiteralNodeBoolean) o;

        return literalValue == that.literalValue;
    }

        
    @Override
    protected UUID initNodeUuid() {
        if (getIsaacDb().isPresent()) {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        Boolean.toString(literalValue));
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
        }
        return null;
     }

    
    @Override
    protected int compareFields(Node o) {
        LiteralNodeBoolean that = (LiteralNodeBoolean) o;
        return Boolean.compare(this.literalValue, that.literalValue);
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (literalValue ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LiteralNodeBoolean[" + getNodeIndex() + "]:" + literalValue + super.toString();
    }
}