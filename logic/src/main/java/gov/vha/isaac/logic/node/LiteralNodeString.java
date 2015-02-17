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
public class LiteralNodeString extends LiteralNode {

    String literalValue;

    public LiteralNodeString(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
        literalValue = dataInputStream.readUTF();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        LiteralNodeString that = (LiteralNodeString) o;

        return literalValue.equals(that.literalValue);
    }
    
    @Override
    protected int compareFields(Node o) {
        LiteralNodeString that = (LiteralNodeString) o;
        return this.literalValue.compareTo(that.literalValue);
    }
    @Override
    protected UUID initNodeUuid() {
        if (getIsaacDb().isPresent()) {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        literalValue);
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
        }
        return null;
     }
    
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + literalValue.hashCode();
        return result;
    }

    public LiteralNodeString(LogicGraph logicGraphVersion, String literalValue) {
        super(logicGraphVersion);
        this.literalValue = literalValue;
    }

    @Override
    protected void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        super.writeData(dataOutput, dataTarget);
        dataOutput.writeUTF(literalValue);
    }

    @Override
    public String toString() {
        return "LiteralNodeString[" + getNodeIndex() + "]:" + literalValue +  super.toString();
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.LITERAL_STRING;
    }

    public String getLiteralValue() {
        return literalValue;
    }

}