package org.hl7.tinkar.coordinate.view;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampCoordinate;

import java.util.ArrayList;
import java.util.List;
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

    ViewCoordinateRecord toViewCoordinateRecord();

    StampCoordinate stampCoordinate();

    <T extends LanguageCoordinate> Iterable<T> languageCoordinateIterable();

    default <T extends LanguageCoordinate> List<T> languageCoordinates() {
        Iterable<T> languageCoordinateIterable = languageCoordinateIterable();
        if (languageCoordinateIterable instanceof ImmutableList<T> immutableList) {
            return immutableList.castToList();
        }
        if (languageCoordinateIterable instanceof MutableList<T> mutableList) {
            return mutableList;
        }
        if (languageCoordinateIterable instanceof List<T> list) {
            return list;
        }
        List<T> newList = new ArrayList<>();
        languageCoordinateIterable.forEach(languageCoordinate -> newList.add(languageCoordinate));
        return newList;
    }

    LogicCoordinate logicCoordinate();

    NavigationCoordinate navigationCoordinate();

    default String toUserString() {
        StringBuilder sb = new StringBuilder("View: ");
        sb.append("\n").append(navigationCoordinate().toUserString());
        sb.append("\n\nView filter:\n").append(stampCoordinate().toUserString());
        sb.append("\n\nLanguage coordinates:\n");
        for (LanguageCoordinate languageCoordinate: languageCoordinateIterable()) {
            sb.append("  ").append(languageCoordinate.toUserString()).append("\n");
        }
        sb.append("\n\nLogic:\n").append(logicCoordinate().toUserString());
        return sb.toString();
    }

}
