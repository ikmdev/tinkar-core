package dev.ikm.tinkar.common.bind.annotations;

import dev.ikm.tinkar.common.bind.annotations.names.FullyQualifiedNames;
import dev.ikm.tinkar.common.bind.annotations.names.RegularNames;
import dev.ikm.tinkar.common.bind.annotations.publicid.PublicIdAnnotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface ConceptAnnotation {
    String name();
    PublicIdAnnotation publicIdAnnotation();
    FullyQualifiedNames fullyQualifiedNames();
    RegularNames regularNames();

}
