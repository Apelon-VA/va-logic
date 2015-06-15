package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.SubstitutionFieldSpecification;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import gov.vha.isaac.ochre.util.UuidT5Generator;

/**
 * Created by kec on 12/10/14.
 * @deprecated moved to ochre model project
 */
@Deprecated
public class SubstitutionNodeFloat extends SubstitutionNodeLiteral {

    public SubstitutionNodeFloat(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }

    public SubstitutionNodeFloat(LogicGraph logicGraphVersion, SubstitutionFieldSpecification substitutionFieldSpecification) {
        super(logicGraphVersion, substitutionFieldSpecification);
    }

    @Override
    public String toString() {
        return "SubstitutionNodeFloat[" + getNodeIndex() + "]:" + super.toString();
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.SUBSTITUTION_FLOAT;
    }
    @Override
    protected UUID initNodeUuid() {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        substitutionFieldSpecification.toString());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
     }
}
