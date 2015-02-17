package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.*;
import java.util.UUID;
/**
 * Created by kec on 12/10/14.
 */
public class AndNode extends ConnectorNode {

    public AndNode(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }
    public AndNode(LogicGraph logicGraphVersion, AbstractNode... children) {
        super(logicGraphVersion, children);
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.AND;
    }

    @Override
    protected UUID initNodeUuid() {
        return getNodeSemantic().getSemanticUuid();
    }

    @Override
    protected void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        writeData(dataOutput, dataTarget);
    }

    @Override
    public String toString() {
        return "AndNode[" + getNodeIndex() + "]:" + super.toString();
    }
}