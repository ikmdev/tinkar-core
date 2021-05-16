package org.hl7.tinkar.coordinate.view;

import org.eclipse.collections.api.list.ListIterable;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampFilter;

import java.util.UUID;

public interface ViewCoordinate {

    static UUID getViewUuid(ViewCoordinate viewCalculator) {
        throw new UnsupportedOperationException();
//        ArrayList<UUID> uuidList = new ArrayList<>();
//        uuidList.add(manifoldCoordinate.getEditCoordinate().getEditCoordinateUuid());
//        uuidList.add(manifoldCoordinate.getNavigationCoordinate().getNavigationCoordinateUuid());
//        uuidList.add(manifoldCoordinate.getVertexSort().getVertexSortUUID());
//        uuidList.add(manifoldCoordinate.getVertexStatusSet().getStatusSetUuid());
//        uuidList.add(manifoldCoordinate.getViewStampFilter().getStampFilterUuid());
//        uuidList.add(manifoldCoordinate.getLanguageCoordinate().getLanguageCoordinateUuid());
//        uuidList.add(UuidT5Generator.get(manifoldCoordinate.getCurrentActivity().name()));
//        StringBuilder sb = new StringBuilder(uuidList.toString());
//        return UUID.nameUUIDFromBytes(sb.toString().getBytes());
    }

    StampFilter stampFilter();

    <T extends LanguageCoordinate> ListIterable<T> languageList();

    LogicCoordinate logic();

    NavigationCoordinate navigation();

    default String toUserString() {
        StringBuilder sb = new StringBuilder("View: ");
        sb.append("\n").append(navigation().toUserString());
        sb.append("\n\nView filter:\n").append(stampFilter().toUserString());
        sb.append("\n\nLanguage coordinates:\n");
        for (LanguageCoordinate languageCoordinate: languageList()) {
            sb.append("  ").append(languageCoordinate.toUserString()).append("\n");
        }
        sb.append("\n\nLogic:\n").append(logic().toUserString());
        return sb.toString();
    }

}
