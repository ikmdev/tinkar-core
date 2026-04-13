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
package dev.ikm.tinkar.common.service;

import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.IntIdCollection;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.set.primitive.IntSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simple description lookup for native identifiers (nids) to user for debugging assistance
 * and Object.toString() use when  Stamp coordinates and language coordinates are not available. Generally this
 * service will provide the first description found irrespective of type, status, language, or dialect.
 */
public interface DefaultDescriptionForNidService {

    /**
     * Returns a list of optional description texts for the given native identifier collection.
     *
     * @param nids the collection of native identifiers to look up
     * @return a list of optional strings, one per nid
     */
    default List<Optional<String>> optionalTextList(IntIdCollection nids) {
        return optionalTextList(nids.toArray());
    }

    /**
     * Returns a list of optional description texts for the given native identifiers.
     *
     * @param nids the native identifiers to look up
     * @return a list of optional strings, one per nid
     */
    default List<Optional<String>> optionalTextList(int... nids) {
        List<Optional<String>> textList = new ArrayList<>(nids.length);
        for (int nid : nids) {
            textList.add(textOptional(nid));
        }
        return textList;
    }

    /**
     * Returns an optional description text for the given native identifier.
     * If {@link #textFast(int)} throws a {@link RuntimeException}, the exception
     * is dispatched to the root alert stream and an empty optional is returned.
     *
     * @param nid the native identifier to look up
     * @return an optional containing the description text, or empty if unavailable
     */
    default Optional<String> textOptional(int nid) {
        try {
            return Optional.ofNullable(textFast(nid));
        } catch (RuntimeException ex) {
            AlertStreams.dispatchToRoot(ex);
            return Optional.empty();
        }
    }

    /**
     * Returns the description text for the given native identifier without
     * wrapping in an {@link Optional}. May throw a {@link RuntimeException}
     * if invoked prior to database initialization. Otherwise, should always
     * return a non-null string.
     *
     * @param nid the native identifier to look up
     * @return the description text, or {@code null} if none is found
     * @throws RuntimeException if the database is not yet initialized
     */
    String textFast(int nid);

    /**
     * Returns a list of optional description texts for the given int list of nids.
     *
     * @param nids the int list of native identifiers to look up
     * @return a list of optional strings, one per nid
     */
    default List<Optional<String>> optionalTextList(IntList nids) {
        return optionalTextList(nids.toArray());
    }

    /**
     * Returns a list of optional description texts for the given int set of nids.
     *
     * @param nids the int set of native identifiers to look up
     * @return a list of optional strings, one per nid
     */
    default List<Optional<String>> optionalTextList(IntSet nids) {
        return optionalTextList(nids.toArray());
    }

    /**
     * Returns a list of non-null description texts for the given native identifiers.
     * If no description is found for a nid, a placeholder string containing the nid
     * value is used instead.
     *
     * @param nids the native identifiers to look up
     * @return a list of description strings, one per nid
     */
    default List<String> textList(int... nids) {
        List<String> textList = new ArrayList<>(nids.length);
        for (int nid : nids) {
            textList.add(text(nid));
        }
        return textList;
    }

    /**
     * Returns the description text for the given native identifier, falling back
     * to a placeholder string of the form {@code "<nid>"} if no description is found.
     *
     * @param nid the native identifier to look up
     * @return the description text, or a placeholder if none is found
     */
    default String text(int nid) {
        String textFast = textFast(nid);
        if (textFast == null) {
            textFast = "<" + nid + ">";
        }
        return textFast;
    }

    /**
     * Returns a list of optional description texts for the given identifier collection.
     *
     * @param nids the collection of native identifiers to look up
     * @return a list of optional strings, one per nid
     */
    default List<Optional<String>> textList(IntIdCollection nids) {
        return optionalTextList(nids.toArray());
    }

    /**
     * Returns a list of optional description texts for the given int list of nids.
     *
     * @param nids the int list of native identifiers to look up
     * @return a list of optional strings, one per nid
     */
    default List<Optional<String>> textList(IntList nids) {
        return optionalTextList(nids.toArray());
    }

    /**
     * Returns a list of optional description texts for the given int set of nids.
     *
     * @param nids the int set of native identifiers to look up
     * @return a list of optional strings, one per nid
     */
    default List<Optional<String>> textList(IntSet nids) {
        return optionalTextList(nids.toArray());
    }

}
