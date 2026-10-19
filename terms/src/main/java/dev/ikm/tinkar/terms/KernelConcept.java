package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.id.PublicId;

import java.util.UUID;

/**
 * The Kernel bindings, generated apart and committed where they are used, of the knowledge set {@code d890e06f-ec35-429a-b541-d0ead19695e2} — DO NOT EDIT.
 * A component's identity is {@code T5(setUuid, fullyQualifiedNameAtBirth)} unless the ledger
 * adopted an established one; regenerate from the ledger.
 */
public enum KernelConcept implements ConceptEnumerationFacade<KernelConcept> {

    /**
     * Uninitialized Component (SOLOR)
     */
    UNINITIALIZED_COMPONENT("Uninitialized Component (SOLOR)", "55f74246-0a25-57ac-9473-a788d08fb656"),

    /**
     * Language
     */
    LANGUAGE("Language", "f56fa231-10f9-5e7f-a86d-a1d61b5b56e3"),

    /**
     * Irish language (SOLOR)
     */
    IRISH_LANGUAGE("Irish language (SOLOR)", "58e82fc4-1492-5cf8-8997-43800360bbd6"),

    /**
     * English Language
     */
    ENGLISH_LANGUAGE("English Language", "02018e5a-46ba-5297-92f1-6931b9f98a12,06d905ea-c647-3af9-bfe5-2514e135b558,45021920-9567-11e5-8994-feff819cdc9f"),

    /**
     * Czech language (SOLOR)
     */
    CZECH_LANGUAGE("Czech language (SOLOR)", "33aa2d26-0541-557c-b796-904cbf245101"),

    /**
     * Danish language (SOLOR)
     */
    DANISH_LANGUAGE("Danish language (SOLOR)", "987681fb-f3ef-595d-90e2-067baf2bc71f,45021f10-9567-11e5-8994-feff819cdc9f,7e462e33-6d94-38ae-a044-492a857a6853"),

    /**
     * French Language (SOLOR)
     */
    FRENCH_LANGUAGE("French Language (SOLOR)", "8b23e636-a0bd-30fb-b8e2-1f77eaa3a87e,01707e47-5f6d-555e-80af-3c1ffb297eaa,45021dbc-9567-11e5-8994-feff819cdc9f"),

    /**
     * German Language (SOLOR)
     */
    GERMAN_LANGUAGE("German Language (SOLOR)", "5f144b18-76a8-5c7e-8480-55a5030d707f"),

    /**
     * Italian Language (SOLOR)
     */
    ITALIAN_LANGUAGE("Italian Language (SOLOR)", "bdd59458-381a-5818-8577-60525f11ac6c"),

    /**
     * Chinese language (SOLOR)
     */
    CHINESE_LANGUAGE("Chinese language (SOLOR)", "aacbc859-e9a0-5e01-b6a9-9a255a47b0c9,ba2efe6b-fe56-3d91-ae0f-3b389628f74c,45022532-9567-11e5-8994-feff819cdc9f"),

    /**
     * Spanish language
     */
    SPANISH_LANGUAGE("Spanish language", "0fcf44fb-d0a7-3a67-bc9f-eb3065ed3c8e,45021c36-9567-11e5-8994-feff819cdc9f"),

    /**
     * Dutch language (SOLOR)
     */
    DUTCH_LANGUAGE("Dutch language (SOLOR)", "21d11bd1-3dab-5034-9625-81b9ae2bd8e7,45022280-9567-11e5-8994-feff819cdc9f,674ad858-0224-3f90-bcf0-bc4cab753d2d"),

    /**
     * Author
     */
    USER("Author", "f7495b58-6630-3499-a44e-2052b5fcf06c"),

    /**
     * KOMET user (SOLOR)
     */
    KOMET_USER("KOMET user (SOLOR)", "61c1a544-2acf-58cd-8cc0-9ac581d4227e"),

    /**
     * Identifier Value (SOLOR)
     */
    IDENTIFIER_VALUE("Identifier Value (SOLOR)", "b32dd26b-c3fc-487e-987e-16ace71a0d0f"),

    /**
     * Anonymous concept (SOLOR)
     */
    ANONYMOUS_CONCEPT("Anonymous concept (SOLOR)", "f8f936d4-3ac7-5629-9f65-9452608056a1"),

    /**
     * EL++ Inferred terminological axioms
     */
    EL_PLUS_PLUS_INFERRED_TERMINOLOGICAL_AXIOMS("EL++ Inferred terminological axioms", "b6d3be7d-1d7f-5c44-a425-5357f878c212"),

    /**
     * Necessary set
     */
    NECESSARY_SET("Necessary set", "acaa2eba-8364-5493-b24c-b3884d34bb60"),

    /**
     * Inclusion set
     */
    INCLUSION_SET("Inclusion set", "def77c09-e1eb-40f2-931a-e7cf2ce0e597"),

    /**
     * Role group
     */
    ROLE_GROUP("Role group", "a63f4bf2-a040-11e5-8994-feff819cdc9f,f97abff6-a221-57a1-9cd6-e79e723bfe2a,051fbfed-3c40-3130-8c09-889cb7b7b5b6"),

    /**
     * Role
     */
    ROLE("Role", "46ae9325-dd24-5008-8fda-80cf1f0977c7"),

    /**
     * Role type
     */
    ROLE_TYPE("Role type", "76320274-be2a-5ba0-b3e8-e6d2e383ee6a"),

    /**
     * Role operator
     */
    ROLE_OPERATOR("Role operator", "f9860cb8-a7c7-5743-9d7c-ffc6e8a24a0f"),

    /**
     * Existential restriction
     */
    EXISTENTIAL_RESTRICTION("Existential restriction", "91e9080f-78f6-5d23-891d-f5b6e77995c8"),

    /**
     * Universal Restriction
     */
    UNIVERSAL_RESTRICTION("Universal Restriction", "fc18c082-c6ad-52d2-b568-cc9568ace6c9"),

    /**
     * Sufficient set
     */
    SUFFICIENT_SET("Sufficient set", "8aa48cfd-485b-5140-beb9-0d122f7812d9"),

    /**
     * EL++ Stated terminological axioms
     */
    EL_PLUS_PLUS_STATED_TERMINOLOGICAL_AXIOMS("EL++ Stated terminological axioms", "1412bd09-eb0c-5107-9756-10c1c417d385"),

    /**
     * EL++ logic profile (SOLOR)
     */
    EL_PLUS_PLUS_PROFILE("EL++ logic profile (SOLOR)", "1f201e12-960e-11e5-8994-feff819cdc9f"),

    /**
     * Concrete value operator (SOLOR)
     */
    CONCRETE_DOMAIN_OPERATOR("Concrete value operator (SOLOR)", "843b0b55-8785-5544-93f6-581da9cf1ff3"),

    /**
     * Equal to (SOLOR)
     */
    EQUAL_TO("Equal to (SOLOR)", "5c9b5844-1434-5111-83d5-cb7cb0be12d9"),

    /**
     * Primordial state
     */
    PRIMORDIAL_STATE("Primordial state", "b17bde5d-98ed-5416-97cf-2d837d75159d"),

    /**
     * Canceled state
     */
    CANCELED_STATE("Canceled state", "b42c1948-7645-5da8-a888-de6ec020ab98"),

    /**
     * Active state
     */
    ACTIVE_STATE("Active state", "09f12001-0e4f-51e2-9852-44862a4a0db4"),

    /**
     * Inactive state
     */
    INACTIVE_STATE("Inactive state", "03004053-c23e-5206-8514-fb551dd328f4"),

    /**
     * Withdrawn state
     */
    WITHDRAWN_STATE("Withdrawn state", "35fd4750-6e43-5fa3-ba7f-f2ad376052bc"),

    /**
     * Description type
     */
    DESCRIPTION_TYPE("Description type", "ad0c19e8-2ccc-59c1-8b7e-c56c03aca8eb"),

    /**
     * Property set (SOLOR)
     */
    PROPERTY_SET("Property set (SOLOR)", "e273b5c0-c012-5e53-990c-aec5c2cb33a7"),

    /**
     * Fully qualified name description type
     */
    FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE("Fully qualified name description type", "00791270-77c9-32b6-b34f-d932569bd2bf,5e1fe940-8faf-11db-b606-0800200c9a66"),

    /**
     * Inferred navigation (SOLOR)
     */
    INFERRED_NAVIGATION("Inferred navigation (SOLOR)", "4bc6c333-7fc9-52f1-942d-f8decba19dc2"),

    /**
     * Definition description type
     */
    DEFINITION_DESCRIPTION_TYPE("Definition description type", "700546a3-09c7-3fc2-9eb9-53d318659a09"),

    /**
     * SOLOR overlay module (SOLOR)
     */
    SOLOR_OVERLAY_MODULE("SOLOR overlay module (SOLOR)", "9ecc154c-e490-5cf8-805d-d2865d62aef3,1f2016a6-960e-11e5-8994-feff819cdc9f"),

    /**
     * Feature (SOLOR)
     */
    FEATURE("Feature (SOLOR)", "5e76a88e-794a-5fdd-8eb2-4a9e4b1386b6"),

    /**
     * Regular name description type
     */
    REGULAR_NAME_DESCRIPTION_TYPE("Regular name description type", "8bfba944-3965-3946-9bcb-1e80a5da63a2,d6fad981-7df6-3388-94d8-238cc0465a79"),

    /**
     * SnoRocket classifier (SOLOR)
     */
    SNOROCKET_CLASSIFIER("SnoRocket classifier (SOLOR)", "1f201fac-960e-11e5-8994-feff819cdc9f"),

    /**
     * SOLOR module (SOLOR)
     */
    SOLOR_MODULE("SOLOR module (SOLOR)", "f680c868-f7e5-5d0e-91f2-615eca8f8fd2"),

    /**
     * Integer data type
     */
    INTEGER_FIELD("Integer data type", "ff59c300-9c4e-5e77-a35d-6a133eb3440f"),

    /**
     * Component data type
     */
    COMPONENT_FIELD("Component data type", "fb00d132-fcc3-5cbf-881d-4bcc4b4c91b3"),

    /**
     * String data type
     */
    STRING("String data type", "a46aaf11-b37a-32d6-abdc-707f084ec8f5"),

    /**
     * Concept data type
     */
    CONCEPT_FIELD("Concept data type", "ac8f1f54-c7c6-5fc7-b1a8-ebb04b918557"),

    /**
     * Component Id list data type
     */
    COMPONENT_ID_LIST_FIELD("Component Id list data type", "e553d3f1-63e1-4292-a3a9-af646fe44292"),

    /**
     * DiGraph data type
     */
    DIGRAPH_FIELD("DiGraph data type", "60113dfe-2bad-11eb-adc1-0242ac120002"),

    /**
     * Component Id set data type
     */
    COMPONENT_ID_SET_FIELD("Component Id set data type", "e283af51-2e8f-44fa-9bf1-89a99a7c7631"),

    /**
     * Boolean data type
     */
    BOOLEAN_FIELD("Boolean data type", "d6b9e2cc-31c6-5e80-91b7-7537690aae32"),

    /**
     * Semantic data type
     */
    SEMANTIC_FIELD_TYPE("Semantic data type", "9c3dfc88-51e4-5e51-a59a-88dd580162b7"),

    /**
     * Decimal data type
     */
    DECIMAL_FIELD("Decimal data type", "b413fe94-4ada-4aee-96f9-22be19699d40"),

    /**
     * Array data type
     */
    ARRAY_FIELD("Array data type", "b168ad04-f814-5036-b886-fd4913de88c8"),

    /**
     * Float data type
     */
    FLOAT_FIELD("Float data type", "6efe7087-3e3c-5b45-8109-90d7652b1506"),

    /**
     * Byte array data type
     */
    BYTE_ARRAY_FIELD("Byte array data type", "dbdd8df2-aec3-596b-88fc-7b83b5594a45"),

    /**
     * DiTree data type
     */
    DITREE_FIELD("DiTree data type", "32f64fc6-5371-11eb-ae93-0242ac130002"),

    /**
     * Axiom Syntax (SOLOR)
     */
    AXIOM_SYNTAX("Axiom Syntax (SOLOR)", "8da1c508-c2a2-4899-b26d-87f8b98a7558"),

    /**
     * Master path
     */
    MASTER_PATH("Master path", "1f20134a-960e-11e5-8994-feff819cdc9f,2faa9260-8fb2-11db-b606-0800200c9a66"),

    /**
     * Development path
     */
    DEVELOPMENT_PATH("Development path", "1f200ca6-960e-11e5-8994-feff819cdc9f"),

    /**
     * Primordial path
     */
    PRIMORDIAL_PATH("Primordial path", "e95b6718-f824-5540-817b-8e79544eb97a"),

    /**
     * Sandbox path
     */
    SANDBOX_PATH("Sandbox path", "80710ea6-983c-5fa0-8908-e479f1f03ea9"),

    /**
     * Feature Type (SOLOR)
     */
    FEATURE_TYPE("Feature Type (SOLOR)", "c9120d8b-1acc-5267-9f33-fa716abdb69d"),

    /**
     * Author for version (SOLOR)
     */
    AUTHOR_FOR_VERSION("Author for version (SOLOR)", "4eb9de0d-7486-5f18-a9b4-82e3432f4103"),

    /**
     * Time for version (SOLOR)
     */
    TIME_FOR_VERSION("Time for version (SOLOR)", "a9b0dfb2-f463-5dae-8ba8-7f2e8385571b"),

    /**
     * Language concept nid for description (SOLOR)
     */
    LANGUAGE_CONCEPT_NID_FOR_DESCRIPTION("Language concept nid for description (SOLOR)", "cd56cceb-8507-5ae5-a928-16079fe6f832"),

    /**
     * Property sequence implication
     */
    PROPERTY_SEQUENCE_IMPLICATION("Property sequence implication", "9a47a5db-42a6-49ee-9083-54bc305a9456"),

    /**
     * Stated premise type (SOLOR)
     */
    STATED_PREMISE_TYPE("Stated premise type (SOLOR)", "3b0dbd3b-2e53-3a30-8576-6c7fa7773060,3fde38f6-e079-3cdc-a819-eda3ec74732d"),

    /**
     * Inferred premise type (SOLOR)
     */
    INFERRED_PREMISE_TYPE("Inferred premise type (SOLOR)", "a4c6bf72-8fb6-11db-b606-0800200c9a66,1290e6ba-48d0-31d2-8d62-e133373c63f5"),

    /**
     * Description acceptability
     */
    DESCRIPTION_ACCEPTABILITY("Description acceptability", "96b61063-0d29-5aea-9652-3f5f328aadc3"),

    /**
     * Preferred (SOLOR)
     */
    PREFERRED("Preferred (SOLOR)", "266f1bc3-3361-39f3-bffe-69db9daea56e"),

    /**
     * Long (SOLOR)
     */
    LONG("Long (SOLOR)", "dea8cdf1-de75-5991-9791-79714e4a964d"),

    /**
     * Literal value (SOLOR)
     */
    LITERAL_VALUE("Literal value (SOLOR)", "208a40a7-e615-5efa-9de0-2e2a5a8488b7"),

    /**
     * Instant literal (SOLOR)
     */
    INSTANT_LITERAL("Instant literal (SOLOR)", "1fbf42e2-42b7-591f-b7fd-ba5de659529e"),

    /**
     * Definition root (SOLOR)
     */
    DEFINITION_ROOT("Definition root (SOLOR)", "e7271c01-6ed4-5240-963f-34d1f24153b0"),

    /**
     * Concept reference (SOLOR)
     */
    CONCEPT_REFERENCE("Concept reference (SOLOR)", "e89148c7-4fe2-52f8-abb9-6a53605d20cb"),

    /**
     * Disjoint with (SOLOR)
     */
    DISJOINT_WITH("Disjoint with (SOLOR)", "f8433993-9a2d-5377-b564-80a45c7b7824"),

    /**
     * EL++ AND (SOLOR)
     */
    AND("EL++ AND (SOLOR)", "fa113d51-07d2-587c-8930-0bce207d506d"),

    /**
     * Generic OR (SOLOR)
     */
    OR("Generic OR (SOLOR)", "2c940bcf-22a8-5fc9-b232-580021e758ed"),

    /**
     * Reflexive Feature (SOLOR)
     */
    REFLEXIVE_PROPERTY("Reflexive Feature (SOLOR)", "7e779e4a-61ed-5c4a-aacc-03cf524b7c73"),

    /**
     * Description case significance
     */
    DESCRIPTION_CASE_SIGNIFICANCE("Description case significance", "c3dde9ea-b144-5f49-845a-20cc7d305250,f30b0312-2c85-3e65-8609-2d89f8437d34"),

    /**
     * Description not case sensitive
     */
    DESCRIPTION_NOT_CASE_SENSITIVE("Description not case sensitive", "ecea41a2-f596-3d98-99d1-771b667e55b8"),

    /**
     * Description case sensitive
     */
    DESCRIPTION_CASE_SENSITIVE("Description case sensitive", "0def37bc-7e1b-384b-a6a3-3e3ceee9c52e"),

    /**
     * Description initial character case sensitive (SOLOR)
     */
    DESCRIPTION_INITIAL_CHARACTER_CASE_SENSITIVE("Description initial character case sensitive (SOLOR)", "17915e0d-ed38-3488-a35c-cda966db306a"),

    /**
     * Transitive Feature (SOLOR)
     */
    TRANSITIVE_PROPERTY("Transitive Feature (SOLOR)", "53f866d0-fd61-5c85-a16c-150bd619a0ac"),

    /**
     * Data property set
     */
    DATA_PROPERTY_SET("Data property set", "6b8ed642-de72-4aee-953d-42e5db92c0ab"),

    /**
     * Text for description
     */
    TEXT_FOR_DESCRIPTION("Text for description", "8bdcbe5d-e92e-5c10-845e-b585e6061672"),

    /**
     * Primordial module
     */
    PRIMORDIAL_MODULE("Primordial module", "c2012321-3903-532e-8a5f-b13e4ca46e86"),

    /**
     * Identifier Source
     */
    IDENTIFIER_SOURCE("Identifier Source", "5a87935c-d654-548f-82a2-0c06e3801162"),

    /**
     * Navigation vertex (SOLOR)
     */
    NAVIGATION_VERTEX("Navigation vertex (SOLOR)", "c7f01834-34ca-5f8b-8f80-193fbeb12eae"),

    /**
     * Property Sequence (SOLOR)
     */
    PROPERTY_SEQUENCE("Property Sequence (SOLOR)", "d0d759fd-510f-475a-900e-b1439b4536e1"),

    /**
     * Phenomenon
     */
    PHENOMENON("Phenomenon", "c2e8bc47-3353-5e02-b0d1-2a5916efed4d"),

    /**
     * Relationship destination
     */
    RELATIONSHIP_DESTINATION("Relationship destination", "a3dd69af-355c-54ce-ba13-2902a7ae9551"),

    /**
     * Relationship origin
     */
    RELATIONSHIP_ORIGIN("Relationship origin", "ad22d43b-3ee7-550b-9660-a6e68af347c2"),

    /**
     * Interval role
     */
    INTERVAL_ROLE("Interval role", "ed9d3506-65ad-48ea-bd01-95474fecdbc4"),

    /**
     * Upper Bound Open
     */
    UPPER_BOUND_OPEN("Upper Bound Open", "c20b3b1e-112f-4cb2-b901-4046db844629"),

    /**
     * Interval Role Type
     */
    INTERVAL_ROLE_TYPE("Interval Role Type", "6fa58611-af37-402e-a0c2-6ee1d6068651"),

    /**
     * Unit of Measure
     */
    UNIT_OF_MEASURE("Unit of Measure", "40afdda5-89d6-4b80-8181-1ddd6eb92dc8"),

    /**
     * Lower Bound Open
     */
    LOWER_BOUND_OPEN("Lower Bound Open", "a0096ba1-0718-4c03-ad8f-8143c44091e7"),

    /**
     * Interval property set
     */
    INTERVAL_PROPERTY_SET("Interval property set", "9afc988a-3724-4754-8b74-651426472b19"),

    /**
     * Interval Lower Bound
     */
    INTERVAL_LOWER_BOUND("Interval Lower Bound", "52b3e38a-fccb-4779-aa61-4e87abd56419"),

    /**
     * Interval Upper Bound
     */
    INTERVAL_UPPER_BOUND("Interval Upper Bound", "6565f774-ff6c-4882-832f-31ddc462adf7"),

    /**
     * Integrated Knowledge Management (SOLOR)
     */
    ROOT_VERTEX("Integrated Knowledge Management (SOLOR)", "7c21b6c5-cf11-5af9-893b-743f004c97f5"),

    /**
     * The identifier source of SNOMED CT identifiers (SCTIDs): an identifier semantic whose source is this concept carries the SNOMED CT identifier of the component it identifies.
     */
    SCTID("SCTID (SOLOR)", "0418a591-f75b-39ad-be2c-3ab849326da9,87360947-e603-3397-804b-efd0fcc509b9,ab9a0e0a-6359-5462-859c-96c3d4ef2341"),

    /**
     * The concept with no instances, a kind of every concept: the reasoner's bottom, owl:Nothing in OWL. A concept classification finds equivalent to it is unsatisfiable: its definition contradicts itself, as when it is a kind of two disjoint concepts. Stated under the root, so unsatisfiable concepts, which share its place, show at the top of the taxonomy.
     */
    BOTTOM("Nothing (IkeFoundation)", "27c8360e-9654-5a2b-9a7a-aa31e9a63926"),

    /**
     * The author of every registration: the IKE tooling that records what a knowledge base is made of whenever it imports, assembles or exports, in Komet, in a Maven goal, or anywhere else.
     */
    REGISTRAR("IKE Registrar (IkeFoundation)", "2899066e-1896-5511-8da7-3a45c64eee50"),

    /**
     * The module registrations are written in: bookkeeping about what the knowledge base is made of, not knowledge. Content views leave it out.
     */
    REGISTRY_MODULE("IKE Registry module (IkeFoundation)", "60795d0a-0c88-5af2-a6a7-f9ac2ff24359"),

    /**
     * The path every registration is written on, whatever path the content it describes is on, so the accounting of a knowledge base is in one place. Nothing is promoted to or from it.
     */
    REGISTRY_PATH("IKE Registry path (IkeFoundation)", "259c8239-9588-53be-a42b-cb3562eb15ed"),

    /**
     * The module transaction records are written to: bookkeeping about how the knowledge base changed, not knowledge. Default views and published releases leave it out; change sets exchanged between people carry it.
     */
    CHANGE_TRACKING_MODULE("Change tracking module (IkeFoundation)", "a6ef93c4-8e0b-5278-82a3-0fa30c4f1d95"),

    /**
     * The transaction may still write; what it wrote is uncommitted.
     */
    TRANSACTION_OPEN("Transaction open (IkeFoundation)", "ccad0c40-10f4-552d-a610-64a78d7aaa0d"),

    /**
     * The transaction ended by committing what it wrote.
     */
    TRANSACTION_COMMITTED("Transaction committed (IkeFoundation)", "c417eb2f-e971-543e-9eb0-4e175c8c4583"),

    /**
     * The transaction ended by cancelling what it wrote; its versions are withdrawn, its record remains.
     */
    TRANSACTION_CANCELLED("Transaction cancelled (IkeFoundation)", "93693d17-7fa5-57ae-9f25-e9bf3f709b06");

    private final EntityProxy.Concept proxy;

    KernelConcept(String fullyQualifiedName, String uuids) {
        this.proxy = EntityProxy.Concept.make(fullyQualifiedName, uuids(uuids));
    }

    @Override
    public EntityProxy.Concept conceptForEnum() {
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
