package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.service.PrimitiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


// TODO: replace or eliminate with solution that uses a default language coordinate obtained as a service?.
public class DefaultDescriptionText {

    static final ConceptProxy DESCRIPTION_PATTERN =
            new ConceptProxy("Description pattern", UUID.fromString("a4de0039-2625-5842-8a4c-d1ce6aebf021"));

    public static Optional<String> getOptional(int nid) {
        return Optional.ofNullable(get(nid));
    }
    public static String get(int nid) {
        int[] semanticNids = PrimitiveData.get().semanticNidsForComponentOfType(nid, DESCRIPTION_PATTERN.nid());
        for (int semanticNid: semanticNids) {
            SemanticEntity descripitonSemantic = Entity.getFast(semanticNid);
            SemanticEntityVersion version = descripitonSemantic.versions().get(0);
            for (Object field: version.fields()) {
                if (field instanceof String stringField) {
                    return stringField;
                }
            }
        }
        return "<" + nid + ">";
    }
    public static List<String> getList(int... nids) {
        ArrayList<String> strings = new ArrayList<>(nids.length);
        for (int nid: nids) {
            strings.add(get(nid));
        }
        return strings;
    }
}
