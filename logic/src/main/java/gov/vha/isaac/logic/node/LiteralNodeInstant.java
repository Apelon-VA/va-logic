package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import gov.vha.isaac.ochre.util.UuidT5Generator;

/**
 * Created by kec on 12/9/14.
 * @deprecated moved to ochre model project
 */
@Deprecated
public class LiteralNodeInstant extends LiteralNode {

    Instant literalValue;

    public LiteralNodeInstant(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
        literalValue = Instant.ofEpochSecond(dataInputStream.readLong());
    }

    public LiteralNodeInstant(LogicGraph logicGraphVersion, Instant literalValue) {
        super(logicGraphVersion);
        this.literalValue = literalValue;
    }

    @Override
    protected void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        super.writeData(dataOutput, dataTarget);
        dataOutput.writeLong(literalValue.getEpochSecond());
    }

    @Override
    public String toString() {
        return "LiteralNodeInstant[" + getNodeIndex() + "]:" + literalValue + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        LiteralNodeInstant that = (LiteralNodeInstant) o;

        return literalValue.equals(that.literalValue);
    }
        @Override
    protected UUID initNodeUuid() {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        literalValue.toString());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
     }

    @Override
    protected int compareFields(Node o) {
        LiteralNodeInstant that = (LiteralNodeInstant) o;
        return this.literalValue.compareTo(that.literalValue);
    }
    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + literalValue.hashCode();
        return result;
    }

    public Instant getLiteralValue() {
        return literalValue;
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.LITERAL_INSTANT;
    }
}