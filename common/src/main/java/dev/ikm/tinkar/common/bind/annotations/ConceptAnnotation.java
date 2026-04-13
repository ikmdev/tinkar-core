package dev.ikm.tinkar.common.bind.annotations;

import dev.ikm.tinkar.common.bind.annotations.axioms.ParentConcepts;
import dev.ikm.tinkar.common.bind.annotations.names.FullyQualifiedNames;
import dev.ikm.tinkar.common.bind.annotations.names.RegularNames;
import dev.ikm.tinkar.common.bind.annotations.publicid.PublicIdAnnotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composite annotation that binds a concept's identity, names, and parentage into a single
 * declaration. It may be applied to types or enum fields that implement
 * {@link dev.ikm.tinkar.common.bind.ClassConceptBinding}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface ConceptAnnotation {

    /**
     * The public identifier (one or more UUIDs) for the annotated concept.
     *
     * @return the {@link PublicIdAnnotation} containing the concept's UUIDs
     */
    PublicIdAnnotation publicIdAnnotation();

    /**
     * The fully qualified names for the annotated concept.
     *
     * @return the {@link FullyQualifiedNames} annotation
     */
    FullyQualifiedNames fullyQualifiedNames();

    /**
     * The regular (display) names for the annotated concept.
     *
     * @return the {@link RegularNames} annotation
     */
    RegularNames regularNames();

    /**
     * The parent concepts of the annotated concept. Defaults to an empty set.
     *
     * @return the {@link ParentConcepts} annotation
     */
    ParentConcepts parents() default @ParentConcepts({});

}
