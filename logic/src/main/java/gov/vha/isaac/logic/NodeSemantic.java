package gov.vha.isaac.logic;

import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import java.util.UUID;
import org.ihtsdo.otf.tcc.api.spec.ConceptSpec;

/**
 * Created by kec on 12/6/14.
 */
public enum NodeSemantic {
    NECESSARY_SET(IsaacMetadataAuxiliaryBinding.NECESSARY_SET),
    SUFFICIENT_SET(IsaacMetadataAuxiliaryBinding.SUFFICIENT_SET),

    AND(IsaacMetadataAuxiliaryBinding.AND),
    OR(IsaacMetadataAuxiliaryBinding.OR),
    DISJOINT_WITH(IsaacMetadataAuxiliaryBinding.DISJOINT_WITH),
    DEFINITION_ROOT(IsaacMetadataAuxiliaryBinding.DEFINITION_ROOT),

    ROLE_ALL(IsaacMetadataAuxiliaryBinding.UNIVERSAL_RESTRICTION),
    ROLE_SOME(IsaacMetadataAuxiliaryBinding.EXISTENTIAL_RESTRICTION),

    CONCEPT(IsaacMetadataAuxiliaryBinding.CONCEPT_REFERENCE),

    FEATURE(IsaacMetadataAuxiliaryBinding.FEATURE),

    LITERAL_BOOLEAN(IsaacMetadataAuxiliaryBinding.BOOLEAN_LITERAL),
    LITERAL_FLOAT(IsaacMetadataAuxiliaryBinding.FLOAT_LITERAL),
    LITERAL_INSTANT(IsaacMetadataAuxiliaryBinding.INSTANT_LITERAL),
    LITERAL_INTEGER(IsaacMetadataAuxiliaryBinding.INTEGER_LITERAL),
    LITERAL_STRING(IsaacMetadataAuxiliaryBinding.STRING_LITERAL),


    TEMPLATE(IsaacMetadataAuxiliaryBinding.TEMPLATE_CONCEPT),
    SUBSTITUTION_CONCEPT(IsaacMetadataAuxiliaryBinding.CONCEPT_SUBSTITUTION),
    SUBSTITUTION_BOOLEAN(IsaacMetadataAuxiliaryBinding.BOOLEAN_SUBSTITUTION),
    SUBSTITUTION_FLOAT(IsaacMetadataAuxiliaryBinding.FLOAT_SUBSTITUTION),
    SUBSTITUTION_INSTANT(IsaacMetadataAuxiliaryBinding.INSTANT_SUBSTITUTION),
    SUBSTITUTION_INTEGER(IsaacMetadataAuxiliaryBinding.INTEGER_SUBSTITUTION),
    SUBSTITUTION_STRING(IsaacMetadataAuxiliaryBinding.STRING_SUBSTITUTION)
    ;
    
    UUID semanticUuid;

    private NodeSemantic(ConceptSpec semanticConceptSpec) {
        this.semanticUuid = semanticConceptSpec.getUuids()[0];
    }
    
    

    public UUID getSemanticUuid() {
        return semanticUuid;
    }
}
