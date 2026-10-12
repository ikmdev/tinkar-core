/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.common.id.LongIdCollection;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongFunction;

/**
 * The text form of a tree — the stated or inferred axioms of a concept, for one — in which the
 * caller decides how each component is named ({@code IKE-Network/ike-issues#1177}).
 *
 * <p>It is the form for text that leaves the process: a service response, the assistant's
 * conversation, an exported document. {@link DiTreeEntity#toString()} is not: it takes each
 * name from the store's default description service and writes {@code <nid>} when that has
 * none, and it writes an id list with the nid of every element. A nid is local to one store,
 * so text that is kept, or read against another store, must not hold one.
 *
 * <p>The layout follows the one {@code toString()} writes: one line per vertex, indented by
 * depth, with the vertex's index, the indexes of its successors, and its meaning, followed by
 * one bulleted line per property. The indexes number the vertices of this one tree; they are
 * not identifiers of components. Properties are ordered by name, so the same tree reads the
 * same in every store.
 *
 * <p>Every component is named by the function the caller gives: the meaning of a vertex, the
 * key of a property, a component held as a property value, and each element of an id list or
 * set. This class writes nothing else about a component, so the text holds a nid only if that
 * function returns one. A vertex or a tree held as a property value is written the same way. A
 * property value that is none of these — a string, a number, a boolean — is written as its own
 * text.
 *
 * <p>A caller may instead give two functions: one for the words of the tree's structure — the
 * meaning of a vertex and the key of a property, such as {@code Necessary set}, {@code And} or
 * {@code Role type} — and one for the components the tree is about — a component held as a
 * property value, including the one a concept reference vertex holds under its own meaning, and
 * each element of an id list or set. The second may then identify each component beside its
 * name, so a reader can act on the components a definition refers to without a lookup for each,
 * while the structural words stay bare ({@code IKE-Network/ike-issues#1291}).
 *
 * <p>The {@code diagnostic} methods are the form for the message of an exception
 * ({@code IKE-Network/ike-issues#1189}). They name each component as {@link DiagnosticText#name(int)}
 * does: by its description, else its UUID, and by its nid only when the store has no public id
 * for it. They throw nothing, because the tree in such a message is often the malformed one the
 * message is about.
 */
public final class DiTreeText {

    /** Indentation added for each level of depth. */
    private static final String INDENT = "  ";

    /** One property of a vertex, as text: the name of its key and the text of its value. */
    private record Property(String name, String value) {
    }

    private DiTreeText() {
    }

    /**
     * The text of a whole tree, from its root.
     *
     * @param tree   the tree
     * @param nameOf gives the name to write for a component, from its nid in the open store
     * @return the tree, one vertex per line with its properties beneath it
     */
    public static String tree(DiTreeAbstract<? extends EntityVertex> tree, LongFunction<String> nameOf) {
        return tree(tree, nameOf, nameOf);
    }

    /**
     * The text of a whole tree, from its root, naming the words of its structure and the
     * components it holds as values by separate functions.
     *
     * @param tree    the tree
     * @param nameOf  gives the name to write for the meaning of a vertex or the key of a
     *                property, from its nid in the open store
     * @param valueOf gives the text to write for a component held as a value, from its nid in
     *                the open store
     * @return the tree, one vertex per line with its properties beneath it
     */
    public static String tree(DiTreeAbstract<? extends EntityVertex> tree, LongFunction<String> nameOf,
                              LongFunction<String> valueOf) {
        StringBuilder text = new StringBuilder();
        appendVertex(text, tree, tree.root().vertexIndex(), 1, nameOf, valueOf);
        return text.toString();
    }

    /**
     * The text of a whole tree for a diagnostic message, with each component named as
     * {@link DiagnosticText#name(int)} names it.
     *
     * @param tree the tree; may be null or malformed
     * @return the tree, one vertex per line with its properties beneath it; a note in place of
     *         the tree when it cannot be written
     */
    public static String diagnostic(DiTreeAbstract<? extends EntityVertex> tree) {
        if (tree == null) {
            return "no tree";
        }
        try {
            return tree(tree, DiagnosticText::name);
        } catch (RuntimeException malformed) {
            return "a tree that cannot be written (" + malformed.getClass().getSimpleName() + ")";
        }
    }

    /**
     * The text of one vertex for a diagnostic message, with each component named as
     * {@link DiagnosticText#name(int)} names it.
     *
     * @param vertex the vertex; may be null or malformed
     * @return the vertex on one line; a note in place of the vertex when it cannot be written
     */
    public static String diagnostic(EntityVertex vertex) {
        if (vertex == null) {
            return "no vertex";
        }
        try {
            return vertex(vertex, DiagnosticText::name);
        } catch (RuntimeException malformed) {
            return "a vertex that cannot be written (" + malformed.getClass().getSimpleName() + ")";
        }
    }

    /**
     * The text of several vertices for a diagnostic message.
     *
     * @param vertices the vertices; may be null
     * @return the vertices as {@code [first; second]}, each written as
     *         {@link #diagnostic(EntityVertex)} writes one
     */
    public static String diagnostic(Iterable<? extends EntityVertex> vertices) {
        if (vertices == null) {
            return "no vertices";
        }
        StringBuilder text = new StringBuilder("[");
        for (EntityVertex vertex : vertices) {
            if (text.length() > 1) {
                text.append("; ");
            }
            text.append(diagnostic(vertex));
        }
        return text.append(']').toString();
    }

    /**
     * The text of one vertex on its own, on one line: its meaning, the value it holds for that
     * meaning when it has one, and then its other properties.
     *
     * @param vertex the vertex
     * @param nameOf gives the name to write for a component, from its nid in the open store
     * @return the vertex as {@code Meaning}, {@code Meaning: value}, or either followed by
     *         {@code {Property=value, …}}
     */
    public static String vertex(EntityVertex vertex, LongFunction<String> nameOf) {
        return vertex(vertex, nameOf, nameOf);
    }

    /**
     * The text of one vertex on its own, on one line, naming the words of its structure and the
     * components it holds as values by separate functions.
     *
     * @param vertex  the vertex
     * @param nameOf  gives the name to write for the meaning of a vertex or the key of a
     *                property, from its nid in the open store
     * @param valueOf gives the text to write for a component held as a value, from its nid in
     *                the open store
     * @return the vertex as {@code Meaning}, {@code Meaning: value}, or either followed by
     *         {@code {Property=value, …}}
     */
    public static String vertex(EntityVertex vertex, LongFunction<String> nameOf, LongFunction<String> valueOf) {
        StringBuilder text = new StringBuilder();
        appendMeaning(text, vertex, nameOf, valueOf);
        List<Property> properties = properties(vertex, nameOf, valueOf);
        if (properties.isEmpty()) {
            return text.toString();
        }
        text.append(" {");
        for (int i = 0; i < properties.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(properties.get(i).name()).append('=').append(properties.get(i).value());
        }
        return text.append('}').toString();
    }

    /** Appends a vertex's line, its property lines, and then its successors, depth first. */
    private static void appendVertex(StringBuilder text, DiTreeAbstract<? extends EntityVertex> tree, int index,
                                     int depth, LongFunction<String> nameOf, LongFunction<String> valueOf) {
        EntityVertex vertex = tree.vertex(index);
        String indent = INDENT.repeat(depth);
        ImmutableIntList successors = tree.successors(index);

        text.append(indent).append(" [").append(index).append(']');
        if (!successors.isEmpty()) {
            text.append("➞[");
            for (int i = 0; i < successors.size(); i++) {
                if (i > 0) {
                    text.append(',');
                }
                text.append(successors.get(i));
            }
            text.append(']');
        }
        text.append(' ');
        appendMeaning(text, vertex, nameOf, valueOf);
        text.append('\n');

        for (Property property : properties(vertex, nameOf, valueOf)) {
            text.append(indent).append("    •").append(property.name())
                    .append(": ").append(property.value()).append('\n');
        }

        for (int i = 0; i < successors.size(); i++) {
            appendVertex(text, tree, successors.get(i), depth + 1, nameOf, valueOf);
        }
    }

    /**
     * Appends a vertex's meaning, and after it the value the vertex holds under that same
     * meaning when it has one — a concept reference vertex holds the referenced concept this
     * way.
     */
    private static void appendMeaning(StringBuilder text, EntityVertex vertex, LongFunction<String> nameOf,
                                      LongFunction<String> valueOf) {
        long meaningNid = vertex.getMeaningNid();
        text.append(nameOf.apply(meaningNid));
        ImmutableLongObjectMap<Object> properties = vertex.properties();
        if (properties.containsKey(meaningNid)) {
            text.append(": ").append(value(properties.get(meaningNid), nameOf, valueOf));
        }
    }

    /**
     * A vertex's properties other than the one keyed by its own meaning, ordered by name and
     * then by value.
     */
    private static List<Property> properties(EntityVertex vertex, LongFunction<String> nameOf,
                                             LongFunction<String> valueOf) {
        long meaningNid = vertex.getMeaningNid();
        ImmutableLongObjectMap<Object> properties = vertex.properties();
        List<Property> named = new ArrayList<>(properties.size());
        properties.forEachKeyValue((keyNid, value) -> {
            if (keyNid != meaningNid) {
                named.add(new Property(nameOf.apply(keyNid), value(value, nameOf, valueOf)));
            }
        });
        named.sort(Comparator.comparing(Property::name).thenComparing(Property::value));
        return named;
    }

    /**
     * The text of a property value. A component is its text as {@code valueOf} writes it; a list
     * or set of components is the list of those texts; a vertex or a tree held as a value is written as this class writes
     * one; anything else — a string, a number, a boolean — is its own text.
     */
    private static String value(Object value, LongFunction<String> nameOf, LongFunction<String> valueOf) {
        return switch (value) {
            case null -> "";
            case EntityFacade facade -> valueOf.apply(facade.nid());
            case LongIdCollection ids -> names(ids, valueOf);
            case EntityVertex held -> vertex(held, nameOf, valueOf);
            case DiTreeEntity held -> "\n" + tree(held, nameOf, valueOf);
            default -> value.toString();
        };
    }

    /** The names of the components of an id list or set, in its order, as {@code [a, b]}. */
    private static String names(LongIdCollection ids, LongFunction<String> nameOf) {
        StringBuilder text = new StringBuilder("[");
        long[] nids = ids.toArray();
        for (int i = 0; i < nids.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(nameOf.apply(nids[i]));
        }
        return text.append(']').toString();
    }
}
