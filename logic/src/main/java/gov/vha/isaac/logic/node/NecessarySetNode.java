package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

/**
 * Created by kec on 12/10/14.
 */
public class NecessarySetNode extends ConnectorNode {

    public NecessarySetNode(LogicGraph logicGraphVersion, AbstractNode... children) {
        super(logicGraphVersion, children);
    }

    public NecessarySetNode(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    @Override
    protected void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        writeData(dataOutput, dataTarget);
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.NECESSARY_SET;
    }

    @Override
    protected UUID initNodeUuid() {
        return getNodeSemantic().getSemanticUuid();
    }

    @Override
    public String toString() {
        return "NecessarySetNode[" + getNodeIndex() + "]:" + super.toString();
    }
}