package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.SubstitutionFieldSpecification;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.ihtsdo.otf.tcc.api.uuid.UuidT5Generator;

/**
 * Created by kec on 12/10/14.
 */
public class SubstitutionNodeBoolean extends SubstitutionNodeLiteral {

    public SubstitutionNodeBoolean(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    public SubstitutionNodeBoolean(LogicGraph logicGraphVersion, SubstitutionFieldSpecification substitutionFieldSpecification) {
        super(logicGraphVersion, substitutionFieldSpecification);
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.SUBSTITUTION_BOOLEAN;
    }
    @Override
    protected UUID initNodeUuid() {
        if (getIsaacDb().isPresent()) {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        substitutionFieldSpecification.toString());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
        }
        return null;
     }

    @Override
    public String toString() {
        return "SubstitutionNodeBoolean[" + getNodeIndex() + "]:" + super.toString();
    }
}
