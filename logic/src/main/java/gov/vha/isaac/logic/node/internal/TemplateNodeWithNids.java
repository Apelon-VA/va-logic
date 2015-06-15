package gov.vha.isaac.logic.node.internal;

import gov.vha.isaac.logic.LogicGraph;
import gov.vha.isaac.logic.Node;
import gov.vha.isaac.logic.NodeSemantic;
import gov.vha.isaac.logic.node.AbstractNode;
import gov.vha.isaac.logic.node.external.TemplateNodeWithUuids;
import gov.vha.isaac.ochre.api.DataTarget;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import gov.vha.isaac.ochre.util.UuidT5Generator;

/**
 * A node that specifies a template to be substituted in place of this node, and the
 * assemblage concept that will be used to fill template substitution values.
 * Created by kec on 12/10/14.
 * @deprecated moved to ochre model project
 */
@Deprecated
public final class TemplateNodeWithNids extends AbstractNode {

    /**
     * Sequence of the concept that defines the template
     */
    int templateConceptNid;

    /**
     * Sequence of the assemblage concept that provides the substitution values
     * for the template.
     */
    int assemblageConceptNid;

    public TemplateNodeWithNids(LogicGraph logicGraphVersion, DataInputStream dataInputStream) throws IOException {
        super(logicGraphVersion, dataInputStream);
        templateConceptNid = dataInputStream.readInt();
        assemblageConceptNid = dataInputStream.readInt();
    }

    public TemplateNodeWithNids(LogicGraph logicGraphVersion, int templateConceptNid, int assemblageConceptNid) {
        super(logicGraphVersion);
        this.templateConceptNid = templateConceptNid;
        this.assemblageConceptNid = assemblageConceptNid;
    }

    public TemplateNodeWithNids(TemplateNodeWithUuids externalForm) {
        super(externalForm);
        this.templateConceptNid = getIdentifierService().get().getNidForUuids(externalForm.getTemplateConceptUuid());
        this.assemblageConceptNid = getIdentifierService().get().getNidForUuids(externalForm.getAssemblageConceptUuid());
    }

    @Override
    public void writeNodeData(DataOutput dataOutput, DataTarget dataTarget) throws IOException {
        switch (dataTarget) {
            case EXTERNAL:
                TemplateNodeWithUuids externalForm = new TemplateNodeWithUuids(this);
                externalForm.writeNodeData(dataOutput, dataTarget);
                break;
            case INTERNAL:
                super.writeData(dataOutput, dataTarget);
                dataOutput.writeInt(templateConceptNid);
                dataOutput.writeInt(assemblageConceptNid);
                break;
            default: throw new UnsupportedOperationException("Can't handle dataTarget: " + dataTarget);
        }
    }

    @Override
    public NodeSemantic getNodeSemantic() {
        return NodeSemantic.TEMPLATE;
    }

    @Override
    public final AbstractNode[] getChildren() {
        return new AbstractNode[0];
    }

    @Override
    public final void addChildren(Node... children) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "TemplateNode[" + getNodeIndex() + "]: " +
                "assemblage: " + getConceptChronicleText(assemblageConceptNid) +
                ", template: " + getConceptChronicleText(templateConceptNid) +
                super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TemplateNodeWithNids that = (TemplateNodeWithNids) o;

        if (assemblageConceptNid != that.assemblageConceptNid) return false;
        return templateConceptNid == that.templateConceptNid;
    }

    @Override
    protected int compareFields(Node o) {
        TemplateNodeWithNids that = (TemplateNodeWithNids) o;
        if (assemblageConceptNid != that.assemblageConceptNid) {
            return this.assemblageConceptNid - that.assemblageConceptNid;
        }
 
        return this.templateConceptNid - that.templateConceptNid;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + templateConceptNid;
        result = 31 * result + assemblageConceptNid;
        return result;
    }
    @Override
    protected UUID initNodeUuid() {
        if (getConceptService().isPresent()) {
            try {
                return UuidT5Generator.get(getNodeSemantic().getSemanticUuid(), 
                        getIdentifierService().get().getUuidPrimordialForNid(assemblageConceptNid).toString() +
                        getIdentifierService().get().getUuidPrimordialForNid(templateConceptNid).toString());
            } catch (IOException| NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            } 
        }
        return null;
     }

    public int getTemplateConceptNid() {
        return templateConceptNid;
    }

    public int getAssemblageConceptNid() {
        return assemblageConceptNid;
    }

}