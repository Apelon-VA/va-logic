package gov.vha.isaac.logic.node.internal;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.node.AbstractNode;
import gov.vha.isaac.logic.node.external.RoleNodeSomeWithUuids;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import gov.vha.isaac.ochre.util.UuidT5Generator;

/**
 * Created by kec on 12/10/14.
 * @deprecated moved to ochre model project
 */
@Deprecated
public final class RoleNodeSomeWithNids extends TypedNodeWithNids {

    public RoleNodeSomeWithNids(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    public RoleNodeSomeWithNids(LogicGraph logicGraphVersion, int typeConceptNid, AbstractNode child) {
        super(logicGraphVersion, typeConceptNid, child);
    }

    @Override
    public void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        switch (dataTarget) {
            case EXTERNAL:
                RoleNodeSomeWithUuids externalForm = new RoleNodeSomeWithUuids(this);
                externalForm.writeNodeData(dataOutput, dataTarget);
                break;
            case INTERNAL:
                super.writeData(dataOutput, dataTarget);
                break;
            default: throw new UnsupportedOperationException("Can't handle dataTarget: " + dataTarget);
        }
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.ROLE_SOME;
    }
    

    @Override
    public String toString() {
        return "RoleNodeSome[" + getNodeIndex() + "]:" + super.toString();
    }
}