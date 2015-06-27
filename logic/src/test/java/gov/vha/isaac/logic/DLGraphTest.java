package gov.vha.isaac.logic;

import gov.vha.isaac.ochre.api.tree.TreeNodeVisitData;
import gov.vha.isaac.ochre.model.logic.LogicalExpressionAbstract;
import gov.vha.isaac.ochre.model.logic.LogicalExpressionOchreImpl;
import gov.vha.isaac.ochre.api.logic.Node;

/**
 * Created by kec on 12/12/14.
 */
public class DLGraphTest {

    
    //@Test Needs to move to integration tests as it is trying to access database...
    public void testCreate() {

        int parentConceptSequence = 1;
        int roleTypeConceptSequence = 2;
        int roleRestrictionConceptSequence = 3;

        int defParentConceptSequence1 = 4;
        int defParentConceptSequence2 = 5;

        LogicalExpressionAbstract lgb = new LogicalExpressionAbstract() {
            @Override
            public void create() {
                Root(
                        NecessarySet(
                                And(Concept(parentConceptSequence),
                                    SomeRole(roleTypeConceptSequence, Concept(roleRestrictionConceptSequence)))),
                        SufficientSet(And(Concept(defParentConceptSequence1), Concept(defParentConceptSequence2))),
                        SufficientSet(And (Feature(defParentConceptSequence1, IntegerLiteral(12))))
                );
            }
        };

        lgb.init();


        LogicalExpressionOchreImpl g = new LogicalExpressionOchreImpl();
        g.getRoot().addChildren(
                g.NecessarySet(
                        g.And(g.Concept(parentConceptSequence),
                                g.SomeRole(roleTypeConceptSequence, g.Concept(roleRestrictionConceptSequence)))),
                g.SufficientSet(g.And(g.Concept(defParentConceptSequence1), g.Concept(defParentConceptSequence2)))
        );

        g.processDepthFirst((Node node, TreeNodeVisitData graphVisitData) -> {
            for (int i = 0; i < graphVisitData.getDistance(node.getNodeIndex()); i++) {
                System.out.print("  ");
            }
            System.out.println(node);
        });
    }


}
