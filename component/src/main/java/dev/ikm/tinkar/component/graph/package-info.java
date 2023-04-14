package dev.ikm.tinkar.component.graph;

/**
 *
 Sets:

 NECESSARY_SET <br/>
 Implement as a connector node, with an identity of "Necessary set"

 SUFFICIENT_SET <br/>
 Implement as a connector node, with an identity of "Sufficient set"

 AND <br/>
 Implement as a connector node, with an identity of "And"

 OR <br/>
 Implement as a connector node, with an identity of "Or"

 DISJOINT_WITH <br/>
 Implement as a connector node, with an identity of "Disjoint with"

 DEFINITION_ROOT <br/>
 Implement as a connector node, with an identity of "Definition root"

 ROLE_ALL <br/>
 Implement as a connector node, with an identity of "Role All", and a vertex property
 with key "role type" that will contain a concept identifying the type

 ROLE_SOME <br/>
 Implement as a connector node, with an identity of "Role some", and a vertex property
 with key "role type" that will contain a concept identifying the type

 CONCEPT <br/>
 Implement as a leaf node, with an identity of the concept

 FEATURE <br/>
 Implement as a connector node, with an identity of "Feature", and a vertex property
 with key "Feature type" that will contain a concept identifying the type, and a
 >, <, =


 Literal
 Implement literal as a leaf node, with an identity of "Literal" and a vertex property
 with key "literal value" that will contain the literal value.

 ? can the key and the

 LITERAL_BOOLEAN <br/>
 LITERAL_DOUBLE <br/>
 LITERAL_INSTANT <br/>
 LITERAL_INTEGER <br/>
 LITERAL_STRING <br/>
 TEMPLATE <br/>

 Substitution

 Implement substitution as a leaf node, with a vertex property
 with key "substitution value" that will contain the substitution value.

 SUBSTITUTION_CONCEPT <br/>
 SUBSTITUTION_BOOLEAN <br/>
 SUBSTITUTION_FLOAT <br/>
 SUBSTITUTION_INSTANT <br/>
 SUBSTITUTION_INTEGER <br/>
 SUBSTITUTION_STRING <br/>


 PROPERTY_SET <br/>


 PROPERTY_PATTERN_IMPLICATION <br/>
 */