package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.SubstitutionEnum;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by kec on 12/10/14.
 */
public abstract class SubstitutionNode extends AbstractNode {
    private static final SubstitutionEnum[] SubstitutionEnumArray = SubstitutionEnum.values();

    SubstitutionEnum substitutionEnum;

    public SubstitutionNode(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
        substitutionEnum = SubstitutionEnumArray[dataInputStream.readByte()];
    }

    public SubstitutionNode(LogicGraph logicGraphVersion, SubstitutionEnum substitutionEnum) {
        super(logicGraphVersion);
        this.substitutionEnum = substitutionEnum;
    }

    @Override
    protected final void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        super.writeData(dataOutput, dataTarget);
        dataOutput.writeByte(substitutionEnum.ordinal());
    }

    @Override
    public final AbstractNode[] getChildren() {
        return new AbstractNode[0];
    }

    @Override
    public final void addChildren(Node... children) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SubstitutionNode that = (SubstitutionNode) o;

        return substitutionEnum.equals(that.substitutionEnum);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + substitutionEnum.hashCode();
        return result;
    }
    @Override
    protected int compareFields(Node o) {
        SubstitutionNode that = (SubstitutionNode) o;
        return this.substitutionEnum.compareTo(that.substitutionEnum);
    }

    @Override
    public String toString() {
        return " substitutionEnum='" + substitutionEnum + '\''  + super.toString();
    }
}