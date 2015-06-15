package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.ochre.api.logic.assertions.substitution.SubstitutionFieldSpecification;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Created by kec on 12/12/14.
 * @deprecated moved to ochre model project
 */
@Deprecated
public abstract class SubstitutionNodeLiteral extends SubstitutionNode {

    public SubstitutionNodeLiteral(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }


    public SubstitutionNodeLiteral(LogicGraph logicGraphVersion, SubstitutionFieldSpecification substitutionFieldSpecification) {
        super(logicGraphVersion, substitutionFieldSpecification);
    }
}
