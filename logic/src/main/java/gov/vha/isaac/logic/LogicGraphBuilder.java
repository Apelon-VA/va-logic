package gov.vha.isaac.logic;

/**
 * Created by kec on 12/11/14.
 */
public abstract class LogicGraphBuilder extends LogicGraph {

    public LogicGraphBuilder() {
        super();
    }

    @Override
    protected final void init() {
        create();
        super.init();
    }

    public abstract void create();

}
