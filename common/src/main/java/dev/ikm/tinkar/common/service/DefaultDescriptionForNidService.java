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
import dev.ikm.tinkar.common.id.LongIdCollection;
import org.eclipse.collections.api.list.primitive.LongList;
import org.eclipse.collections.api.set.primitive.LongSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simple description lookup for native identifiers (nids) to user for debugging assistance
 * and Object.toString() use when  Stamp coordinates and language coordinates are not available. Generally this
 * service will provide the first description found irrespective of type, status, language, or dialect.
 */
public interface DefaultDescriptionForNidService {
    default List<Optional<String>> optionalTextList(LongIdCollection nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> optionalTextList(long... nids) {
        List<Optional<String>> textList = new ArrayList<>(nids.length);
        for (long nid : nids) {
            textList.add(textOptional(nid));
        }
        return textList;
    }

    default Optional<String> textOptional(long nid) {
        try {
            return Optional.ofNullable(textFast(nid));
        } catch (RuntimeException ex) {
            AlertStreams.dispatchToRoot(ex);
            return Optional.empty();
        }
    }

    /**
     * May throw a RuntimeException if invoked prior to database initialization. Otherwise, should always return
     * a String.
     */
    String textFast(long nid);

    default List<Optional<String>> optionalTextList(LongList nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> optionalTextList(LongSet nids) {
        return optionalTextList(nids.toArray());
    }

    default List<String> textList(long... nids) {
        List<String> textList = new ArrayList<>(nids.length);
        for (long nid : nids) {
            textList.add(text(nid));
        }
        return textList;
    }

    default String text(long nid) {
        String textFast = textFast(nid);
        if (textFast == null) {
            textFast = "<" + nid + ">";
        }
        return textFast;
    }

    default List<Optional<String>> textList(LongIdCollection nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> textList(LongList nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> textList(LongSet nids) {
        return optionalTextList(nids.toArray());
    }

}
