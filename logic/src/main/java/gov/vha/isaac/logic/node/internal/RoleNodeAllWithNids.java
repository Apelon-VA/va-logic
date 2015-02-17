package gov.vha.isaac.logic.node.internal;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.node.AbstractNode;
import gov.vha.isaac.logic.node.external.FeatureNodeWithUuids;
import gov.vha.isaac.logic.node.external.RoleNodeAllWithUuids;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.ihtsdo.otf.tcc.api.uuid.UuidT5Generator;

/**
 * Created by kec on 12/6/14.
 */
public final class RoleNodeAllWithNids extends TypedNodeWithNids {

    public RoleNodeAllWithNids(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    public RoleNodeAllWithNids(LogicGraph logicGraphVersion, int typeConceptNid, AbstractNode child) {
        super(logicGraphVersion, typeConceptNid, child);
    }

    @Override
    public void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        switch (dataTarget) {
            case EXTERNAL:
                RoleNodeAllWithUuids externalForm = new RoleNodeAllWithUuids(this);
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
        return NodeSemantic.ROLE_ALL;
    }
    
    @Override
    protected UUID initNodeUuid() {
        if (getIsaacDb().isPresent()) {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        getIsaacDb().get().getUuidPrimordialForNid(typeConceptNid).toString());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
        }
        return null;
     }

    @Override
    public String toString() {
        return "RoleNodeAll[" + getNodeIndex() + "]:" + super.toString();
    }
}
