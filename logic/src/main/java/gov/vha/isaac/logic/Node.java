package gov.vha.isaac.logic;

import gov.vha.isaac.ochre.api.DataTarget;

/**
 * Created by kec on 12/9/14.
 */
public interface Node {

    NodeSemantic getNodeSemantic();

    Node[] getChildren();

    byte[] getBytes(DataTarget dataTarget);

    short getNodeIndex();

    void setNodeIndex(short nodeIndex);

    void addChildren(Node... children);
}