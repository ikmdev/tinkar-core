package org.hl7.tinkar.coordinate.language;

import java.util.ArrayList;
import java.util.List;

public class DefaultDescriptionText {
    public static String get(int nid) {
        throw new UnsupportedOperationException();
    }
    public static List<String> getList(int... nids) {
        ArrayList<String> strings = new ArrayList<>(nids.length);
        for (int nid: nids) {
            strings.add(get(nid));
        }
        return strings;
    }
}
