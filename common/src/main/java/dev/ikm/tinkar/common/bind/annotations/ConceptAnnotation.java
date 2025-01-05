package dev.ikm.tinkar.common.bind.annotations;

import dev.ikm.tinkar.common.bind.annotations.names.FullyQualifiedNames;
import dev.ikm.tinkar.common.bind.annotations.names.RegularNames;
import dev.ikm.tinkar.common.bind.annotations.publicid.PublicIdAnnotation;

public @interface ConceptAnnotation {
    String name();
    PublicIdAnnotation publicIdAnnotation();
    FullyQualifiedNames fullyQualifiedNames();
    RegularNames regularNames();

}
