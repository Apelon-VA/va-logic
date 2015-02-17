/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.vha.isaac.logic.node.external;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.node.AbstractNode;
import gov.vha.isaac.logic.node.internal.RoleNodeSomeWithNids;
import gov.vha.isaac.ochre.api.DataTarget;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.ihtsdo.otf.tcc.api.uuid.UuidT5Generator;

/**
 *
 * @author kec
 */
public class RoleNodeSomeWithUuids extends TypedNodeWithUuids {

    public RoleNodeSomeWithUuids(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    public RoleNodeSomeWithUuids(LogicGraph logicGraphVersion, UUID typeConceptUuid, AbstractNode child) {
        super(logicGraphVersion, typeConceptUuid, child);
    }

    public RoleNodeSomeWithUuids(RoleNodeSomeWithNids internalForm) {
        super(internalForm);
    }

    @Override
    public void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        super.writeData(dataOutput, dataTarget);
    }


    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.ROLE_SOME;
    }

    @Override
    protected UUID initNodeUuid() {
        try {
            return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(),
                    typeConceptUuid.toString());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public String toString() {
        return "RoleNodeSome[" + getNodeIndex() + "]:" + super.toString();
    }
}