package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.SubstitutionEnum;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.ihtsdo.otf.tcc.api.uuid.UuidT5Generator;

/**
 * Created by kec on 12/10/14.
 */
public class SubstitutionNodeConcept extends SubstitutionNode {

    public SubstitutionNodeConcept(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    public SubstitutionNodeConcept(LogicGraph logicGraphVersion, SubstitutionEnum substitutionEnum) {
        super(logicGraphVersion, substitutionEnum);
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.SUBSTITUTION_CONCEPT;
    }

    @Override
    public String toString() {
        return "SubstitutionNodeConcept[" + getNodeIndex() + "]:" + super.toString();
    }
    @Override
    protected UUID initNodeUuid() {
        if (getIsaacDb().isPresent()) {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        substitutionEnum.name());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
        }
        return null;
     }
}
