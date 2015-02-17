package gov.vha.isaac.logic.node;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.SubstitutionEnum;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Created by kec on 12/12/14.
 */
public abstract class SubstitutionNodeLiteral extends SubstitutionNode {

    public SubstitutionNodeLiteral(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
    }


    public SubstitutionNodeLiteral(LogicGraph logicGraphVersion, SubstitutionEnum substitutionEnum) {
        super(logicGraphVersion, substitutionEnum);
    }
}
