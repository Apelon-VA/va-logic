package gov.vha.isaac.logic;

import gov.vha.isaac.ochre.api.graph.GraphVisitData;
import org.junit.Test;

/**
 * Created by kec on 12/12/14.
 */
public class DLGraphTest {

    
    @Test
    public void testCreate() {

        int parentConceptSequence = 1;
        int roleTypeConceptSequence = 2;
        int roleRestrictionConceptSequence = 3;

        int defParentConceptSequence1 = 4;
        int defParentConceptSequence2 = 5;

        LogicGraphBuilder lgb = new LogicGraphBuilder() {
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


        LogicGraph g = new LogicGraph();
        g.getRoot().addChildren(
                g.NecessarySet(
                        g.And(g.Concept(parentConceptSequence),
                                g.SomeRole(roleTypeConceptSequence, g.Concept(roleRestrictionConceptSequence)))),
                g.SufficientSet(g.And(g.Concept(defParentConceptSequence1), g.Concept(defParentConceptSequence2)))
        );

        g.processDepthFirst((Node node, GraphVisitData graphVisitData) -> {
            for (int i = 0; i < graphVisitData.getDistance(node.getNodeIndex()); i++) {
                System.out.print("  ");
            }
            System.out.println(node);
        });
    }


}
