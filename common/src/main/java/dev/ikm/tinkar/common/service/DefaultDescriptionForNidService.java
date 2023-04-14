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
    default List<Optional<String>> optionalTextList(IntIdCollection nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> optionalTextList(int... nids) {
        List<Optional<String>> textList = new ArrayList<>(nids.length);
        for (int nid : nids) {
            textList.add(textOptional(nid));
        }
        return textList;
    }

    default Optional<String> textOptional(int nid) {
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
    String textFast(int nid);

    default List<Optional<String>> optionalTextList(IntList nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> optionalTextList(IntSet nids) {
        return optionalTextList(nids.toArray());
    }

    default List<String> textList(int... nids) {
        List<String> textList = new ArrayList<>(nids.length);
        for (int nid : nids) {
            textList.add(text(nid));
        }
        return textList;
    }

    default String text(int nid) {
        String textFast = textFast(nid);
        if (textFast == null) {
            textFast = "<" + nid + ">";
        }
        return textFast;
    }

    default List<Optional<String>> textList(IntIdCollection nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> textList(IntList nids) {
        return optionalTextList(nids.toArray());
    }

    default List<Optional<String>> textList(IntSet nids) {
        return optionalTextList(nids.toArray());
    }

}
