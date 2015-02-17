package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

/**
 * Created by kec on 12/9/14.
 */
public class RootNode extends ConnectorNode {

    public RootNode(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    public RootNode(LogicGraph logicGraphVersion, ConnectorNode... children) {
        super(logicGraphVersion, children);
    }

    @Override
    protected void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        writeData(dataOutput, dataTarget);
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.DEFINITION_ROOT;
    }

    @Override
    protected UUID initNodeUuid() {
        return getNodeSemantic().getSemanticUuid();
    }

    @Override
    public String toString() {
        return "RootNode[" + getNodeIndex() + "]:" + super.toString();
    }
}
