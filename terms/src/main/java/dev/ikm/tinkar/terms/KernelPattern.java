package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.id.PublicId;

import java.util.UUID;

/**
 * The Kernel bindings, generated apart and committed where they are used, of the knowledge set {@code d890e06f-ec35-429a-b541-d0ead19695e2} — DO NOT EDIT.
 * A component's identity is {@code T5(setUuid, fullyQualifiedNameAtBirth)} unless the ledger
 * adopted an established one; regenerate from the ledger.
 */
public enum KernelPattern implements PatternEnumerationFacade<KernelPattern> {

    /**
     * IKE base model component pattern
     */
    TINKAR_BASE_MODEL_COMPONENT_PATTERN("IKE base model component pattern", "6070f6f5-893d-5144-adce-7d305c391cf9"),

    /**
     * Description Pattern
     */
    DESCRIPTION_PATTERN("Description Pattern", "a4de0039-2625-5842-8a4c-d1ce6aebf021"),

    /**
     * Module origins pattern (SOLOR)
     */
    MODULE_ORIGINS_PATTERN("Module origins pattern (SOLOR)", "536b0ec4-4974-47ae-93a6-ae6c4d169780"),

    /**
     * Identifier Pattern
     */
    IDENTIFIER_PATTERN("Identifier Pattern", "5d60e14b-c410-5172-9559-3c4253278ae2"),

    /**
     * Inferred Navigation Pattern
     */
    INFERRED_NAVIGATION_PATTERN("Inferred Navigation Pattern", "a53cc42d-c07e-5934-96b3-2ede3264474e"),

    /**
     * Stated Navigation Pattern
     */
    STATED_NAVIGATION_PATTERN("Stated Navigation Pattern", "d02957d6-132d-5b3c-adba-505f5778d998"),

    /**
     * EL++ Stated Axioms Pattern
     */
    EL_PLUS_PLUS_STATED_AXIOMS_PATTERN("EL++ Stated Axioms Pattern", "e813eb92-7d07-5035-8d43-e81249f5b36e"),

    /**
     * Version control path pattern
     */
    PATHS_PATTERN("Version control path pattern", "add1db57-72fe-53c8-a528-1614bda20ec6"),

    /**
     * Comment pattern
     */
    COMMENT_PATTERN("Comment pattern", "3734fb0a-4c14-5831-9a61-4743af609e7a"),

    /**
     * EL++ Inferred Axioms Pattern
     */
    EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN("EL++ Inferred Axioms Pattern", "9f011812-15c9-5b1b-85f8-bb262bc1b2a2"),

    /**
     * SOLOR concept assemblage (SOLOR)
     */
    SOLOR_CONCEPT_ASSEMBLAGE("SOLOR concept assemblage (SOLOR)", "d39b3ecd-9a80-5009-a8ac-0b947f95ca7c"),

    /**
     * OWL Axiom Syntax Pattern
     */
    OWL_AXIOM_SYNTAX_PATTERN("OWL Axiom Syntax Pattern", "c0ca180b-aae2-5fa1-9ab7-4a24f2dfe16b"),

    /**
     * Records whether a description is preferred or acceptable in the United States English dialect — one field, that description's acceptability for this dialect.
     */
    US_DIALECT_PATTERN("US Dialect Pattern", "08f9112c-c041-56d3-b89b-63258f070074"),

    /**
     * Komet base model component pattern
     */
    KOMET_BASE_MODEL_COMPONENT_PATTERN("Komet base model component pattern", "bbbbf1fe-00f0-55e0-a19c-6300dbaab9b2"),

    /**
     * GB Dialect Pattern
     */
    GB_DIALECT_PATTERN("GB Dialect Pattern", "561f817a-130e-5e56-984d-910e9991558c"),

    /**
     * Path origins pattern (SOLOR)
     */
    PATH_ORIGINS_PATTERN("Path origins pattern (SOLOR)", "70f89dd5-2cdb-59bb-bbaa-98527513547c"),

    /**
     * The concepts classification found the referenced concept logically equivalent to, never including the concept itself. Written beside the inferred navigation semantic only for a concept with equivalents; when a later classification finds none, the set is empty.
     */
    INFERRED_EQUIVALENCE_PATTERN("Inferred equivalence pattern (IkeFoundation)", "749e039b-33c1-587c-83f0-a72a6e9eabef");

    private final EntityProxy.Pattern proxy;

    KernelPattern(String fullyQualifiedName, String uuids) {
        this.proxy = EntityProxy.Pattern.make(fullyQualifiedName, uuids(uuids));
    }

    @Override
    public EntityProxy.Pattern patternForEnum() {
        return proxy;
    }

    @Override
    public PublicId publicId() {
        return proxy.publicId();
    }

    /**
     * @return the nid, resolved once and cached, as the proxy does
     */
    @Override
    public long nid() {
        return proxy.nid();
    }

    /**
     * @return the fully qualified name at birth, with no store lookup
     */
    @Override
    public String description() {
        return proxy.description();
    }

    /**
     * @return the proxy's text, so logs and debugging read as they did before enums
     */
    @Override
    public String toString() {
        return proxy.toString();
    }

    private static UUID[] uuids(String text) {
        String[] parts = text.split(",");
        UUID[] uuids = new UUID[parts.length];
        for (int i = 0; i < parts.length; i++) {
            uuids[i] = UUID.fromString(parts[i]);
        }
        return uuids;
    }
}
