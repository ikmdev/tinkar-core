/* 
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributions from 2013-2017 where performed either by US government 
 * employees, or under US Veterans Health Administration contracts. 
 *
 * US Veterans Health Administration contributions by government employees
 * are work of the U.S. Government and are not subject to copyright
 * protection in the United States. Portions contributed by government 
 * employees are USGovWork (17USC §105). Not subject to copyright. 
 * 
 * Contribution by contractors to the US Veterans Health Administration
 * during this period are contractually contributed under the
 * Apache License, Version 2.0.
 *
 * See: https://www.usa.gov/government-works
 * 
 * Contributions prior to 2013:
 *
 * Copyright (C) International Health Terminology Standards Development Organisation.
 * Licensed under the Apache License, Version 2.0.
 *
 */



package org.hl7.tinkar.coordinate.manifold;

//~--- JDK imports ------------------------------------------------------------

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.util.text.NaturalOrder;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.LatestVersion;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.coordinate.edit.Activity;
import org.hl7.tinkar.coordinate.edit.EditCoordinate;
import org.hl7.tinkar.coordinate.language.DefaultDescriptionText;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.PremiseSet;
import org.hl7.tinkar.coordinate.logic.PremiseType;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateImmutable;
import org.hl7.tinkar.coordinate.navigation.VertexSort;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.entity.SemanticEntityVersion;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

//~--- interfaces -------------------------------------------------------------

/**
 * The Interface ManifoldCoordinate.
 *
 * @author kec
 * TODO consider deprecation/deletion and switch to diagraph coordinate.
 */
public interface ManifoldCoordinate {

    static UUID getManifoldCoordinateUuid(ManifoldCoordinate manifoldCoordinate) {
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

    default String toUserString() {
        StringBuilder sb = new StringBuilder("Manifold coordinate: ");
        sb.append("\nActivity: ").append(getCurrentActivity().toUserString());
        sb.append("\n").append(getNavigationCoordinate().toUserString());
        sb.append("\n\nView filter:\n").append(getViewStampFilter().toUserString());
        sb.append("\n\nLanguage coordinate:\n").append(getLanguageCoordinate().toUserString());
        sb.append("\n\nVertex filter:\n").append(getVertexStatusSet().toUserString());
        sb.append("\n\nSort:\n").append(getVertexSort().getVertexSortName());
        sb.append("\n\nLogic:\n").append(getLogicCoordinate().toUserString());
        sb.append("\n\nEdit:\n").append(getEditCoordinate().toUserString());
        return sb.toString();
    }

    EditCoordinate getEditCoordinate();

    //TaxonomySnapshot getNavigationSnapshot();

    ManifoldCoordinateImmutable toManifoldCoordinateImmutable();

    default UUID getManifoldCoordinateUuid() {
        return getManifoldCoordinateUuid(this);
    }

    VertexSort getVertexSort();

    default int getAuthorNidForChanges() {
        return getEditCoordinate().getAuthorNidForChanges();
    }

    default int getPathNidForFilter() {
        return getViewStampFilter().getPathNidForFilter();
    }

    default int getPathNidForChanges() {
        return getPathNidForFilter();
    }

    default int[] sortVertexes(int[] vertexConceptNids) {
        return getVertexSort().sortVertexes(vertexConceptNids, toManifoldCoordinateImmutable());
    }

    /**
     * The coordinate that controls most aspects of the view. In some cases, the language stamp filter may provide
     * different status values, for example to allow display of retired descriptions or of retired concepts when pointed
     * to by active relationships in the view.
     *
     * This filter is used on the edges (relationships) in navigation operations, while {@link #getVertexStatusSet()}
     * is used on the vertexes (concepts) themselves.
     *
     * @return The view stamp filter,
     */
    StampFilter getViewStampFilter();

    /**
     * In most cases, this coordinate will be the equal to the coordinate returned by {@link #getViewStampFilter()},
     * But, it may be a different, depending on the construction - for example, a use case like returning inactive
     * vertexes (concepts) linked by active edges (relationships).
     *
     * This status set vertexes (source and destination concepts)
     * in navigation operations, while {@link #getViewStampFilter()} is used
     * on the edges (relationships) themselves.
     *
     * @return The vertex stamp filter,
     */
    StateSet getVertexStatusSet();

    /**
     * All fields the same as the view stamp filter, except for the status set.
     * Having a vertex and view stamp filter allows for active relationships to point
     * to inactive concepts, as might be the case when you want to navigate retired concepts,
     * or concepts considered equivalent to retired concepts.
     * @return the filter to use for retrieving vertexes
     */
    StampFilter getVertexStampFilter();

    default LatestVersion<SemanticEntityVersion> getDescription(
            ConceptEntity concept) {
        return this.getLanguageCoordinate().getDescription(concept.nid(), this.getViewStampFilter());
    }

    default Optional<String> getDescriptionText(int conceptNid) {
        getLanguageCoordinate().getDescriptionText(conceptNid, this.getViewStampFilter());
        LatestVersion<SemanticEntityVersion> latestVersion = getDescription(conceptNid);
        if (latestVersion.isPresent()) {
            throw new UnsupportedOperationException();
            //return Optional.of(latestVersion.get().getText());
        }
        return Optional.empty();
    }


    default Optional<String> getDescriptionText(ConceptEntity concept) {
        return getDescriptionText(concept.nid());
    }

    default LatestVersion<SemanticEntityVersion> getDescription(
            int conceptNid) {
        return this.getLanguageCoordinate().getDescription(conceptNid, this.getViewStampFilter());
    }


    default LatestVersion<SemanticEntityVersion> getDescription(
            List<SemanticEntity> descriptionList) {
        return this.getLanguageCoordinate().getDescription(descriptionList, this.getViewStampFilter());
    }

    PremiseSet getPremiseTypes();

    default NavigationCoordinateImmutable toNavigationCoordinateImmutable() {
        return getNavigationCoordinate().toNavigationCoordinateImmutable();
    }

    NavigationCoordinate getNavigationCoordinate();

    LogicCoordinate getLogicCoordinate();

    LanguageCoordinate getLanguageCoordinate();

    default Optional<String> getFullyQualifiedName(int nid, StampFilter filter) {
        return this.getLanguageCoordinate().getFullyQualifiedNameText(nid, filter);
    }
//
//    default Optional<LogicalExpression> getStatedLogicalExpression(int conceptNid) {
//        return getLogicalExpression(conceptNid, PremiseType.STATED);
//    }

//    default Optional<LogicalExpression> getStatedLogicalExpression(Concept concept) {
//        return getLogicalExpression(concept.nid(), PremiseType.STATED);
//    }

//    default Optional<LogicalExpression> getLogicalExpression(Concept concept, PremiseType premiseType) {
//        return this.getLogicalExpression(concept.nid(), premiseType);
//    }

//    default LatestVersion<LogicGraphVersion> getStatedLogicalDefinition(int conceptNid) {
//        return this.getLogicalDefinition(conceptNid, PremiseType.STATED);
//    }

//    default LatestVersion<LogicGraphVersion> getStatedLogicalDefinition(Concept concept) {
//        return this.getLogicalDefinition(concept.nid(), PremiseType.STATED);
//    }

//    default LatestVersion<LogicGraphVersion> getLogicalDefinition(Concept concept, PremiseType premiseType) {
//        return this.getLogicalDefinition(concept.nid(), premiseType);
//    }

//    default LatestVersion<LogicGraphVersion> getLogicalDefinition(int conceptNid, PremiseType premiseType) {
//        return this.getLogicCoordinate().getLogicGraphVersion(conceptNid, premiseType, this.getViewStampFilter());
//    }


    default Optional<String> getFullyQualifiedName(int nid) {
        return this.getLanguageCoordinate().getFullyQualifiedNameText(nid, this.getViewStampFilter());
    }

    default String getVertexLabel(int vertexConceptNid) {
        return getVertexSort().getVertexLabel(vertexConceptNid,
                getLanguageCoordinate().toLanguageCoordinateImmutable(),
                getViewStampFilter().toStampFilterImmutable());
    }

//    default String getVertexLabel(Concept vertexConcept) {
//        return getVertexLabel(vertexConcept.nid());
//    }

    default ImmutableList<String> getPreferredDescriptionTextList(int[] nidArray) {
        MutableList<String> results = Lists.mutable.empty();
        for (int nid: nidArray) {
            results.add(getPreferredDescriptionText(nid));
        }
        return results.toImmutable();
    }

    default ImmutableList<String> getPreferredDescriptionTextList(Collection<ConceptEntity> conceptCollection) {
        MutableList<String> results = Lists.mutable.empty();
        for (ConceptEntity concept: conceptCollection) {
            results.add(getPreferredDescriptionText(concept));
        }
        return results.toImmutable();
    }

    default ImmutableList<String> getFullyQualifiedNameTextList(int[] nidArray) {
        MutableList<String> results = Lists.mutable.empty();
        for (int nid: nidArray) {
            results.add(getFullyQualifiedDescriptionText(nid));
        }
        return results.toImmutable();
    }

    default ImmutableList<String> getFullyQualifiedNameTextList(Collection<ConceptEntity> conceptCollection) {
        MutableList<String> results = Lists.mutable.empty();
        for (ConceptEntity concept: conceptCollection) {
            results.add(getFullyQualifiedDescriptionText(concept));
        }
        return results.toImmutable();
    }


    default String getPreferredDescriptionText(int conceptNid) {
        try {
            return getLanguageCoordinate().getRegularDescriptionText(conceptNid, getViewStampFilter())
                    .orElse("No desc for: " + DefaultDescriptionText.get(conceptNid));
        } catch (NoSuchElementException ex) {
            return ex.getLocalizedMessage();
        }
    }

    default String getPreferredDescriptionText(ConceptEntity concept) {
        return getPreferredDescriptionText(concept.nid());
    }

    default String getFullyQualifiedDescriptionText(int conceptNid) {
        return getLanguageCoordinate().getFullyQualifiedNameText(conceptNid, getViewStampFilter())
                .orElse("No desc for: " + DefaultDescriptionText.get(conceptNid));
    }

    default String getFullyQualifiedDescriptionText(ConceptEntity concept) {
        return getFullyQualifiedDescriptionText(concept.nid());
    }

    default LatestVersion<SemanticEntityVersion> getFullyQualifiedDescription(int conceptNid) {
        return getLanguageCoordinate().getFullyQualifiedDescription(conceptNid, getViewStampFilter());
    }

    default LatestVersion<SemanticEntityVersion> getFullyQualifiedDescription(ConceptEntity concept) {
        return getFullyQualifiedDescription(concept.nid());
    }


    default LatestVersion<SemanticEntityVersion> getPreferredDescription(int conceptNid) {
        return getLanguageCoordinate().getRegularDescription(conceptNid, getViewStampFilter());
    }

    default LatestVersion<SemanticEntityVersion> getPreferredDescription(ConceptEntity concept) {
        return getPreferredDescription(concept.nid());
    }


    default OptionalInt getAcceptabilityNid(int descriptionNid, int dialectAssemblageNid) {
        throw new UnsupportedOperationException();
//        ImmutableIntSet acceptabilityChronologyNids = Get.assemblageService().getSemanticNidsForComponentFromAssemblage(descriptionNid, dialectAssemblageNid);
//
//        for (int acceptabilityChronologyNid: acceptabilityChronologyNids.toArray()) {
//            SemanticEntity acceptabilityChronology = Get.assemblageService().getSemanticEntity(acceptabilityChronologyNid);
//            LatestVersion<ComponentNidVersion> latestAcceptability = acceptabilityChronology.getLatestVersion(getViewStampFilter());
//            if (latestAcceptability.isPresent()) {
//                return OptionalInt.of(latestAcceptability.get().getComponentNid());
//            }
//        }
//        return OptionalInt.empty();
    }

//    default LatestVersion<LogicGraphVersion> getStatedLogicGraphVersion(int conceptNid) {
//        return getLogicGraphVersion(conceptNid, PremiseType.STATED);
//    }

//    default LatestVersion<LogicGraphVersion> getInferredLogicGraphVersion(ConceptEntity Concept) {
//        return getLogicGraphVersion(Concept.nid(), PremiseType.INFERRED);
//    }

//    default LatestVersion<LogicGraphVersion> getStatedLogicGraphVersion(ConceptEntity Concept) {
//        return getLogicGraphVersion(Concept.nid(), PremiseType.STATED);
//    }

//    default LatestVersion<LogicGraphVersion> getInferredLogicGraphVersion(int conceptNid) {
//        return getLogicGraphVersion(conceptNid, PremiseType.INFERRED);
//    }

//    default LatestVersion<LogicGraphVersion> getLogicGraphVersion(int conceptNid, PremiseType premiseType) {
//        ConceptChronology concept = Get.concept(conceptNid);
//        return concept.getLogicalDefinition(getViewStampFilter(), premiseType, this.getLogicCoordinate());
//    }

//    default Optional<LogicalExpression> getInferredLogicalExpression(ConceptEntity spec) {
//        return getLogicCoordinate().getInferredLogicalExpression(spec.nid(), this.getViewStampFilter());
//    }

//    default Optional<LogicalExpression> getInferredLogicalExpression(int conceptNid) {
//        return getLogicCoordinate().getLogicalExpression(conceptNid, PremiseType.INFERRED, this.getViewStampFilter());
//    }

//    default String toFqnConceptString(Object object) {
//        return toConceptString(object, this::getFullyQualifiedDescriptionText);
//    }

//    default String toPreferredConceptString(Object object) {
//        return toConceptString(object, this::getPreferredDescriptionText);
//    }

//    default Optional<LogicalExpression> getLogicalExpression(int conceptNid, PremiseType premiseType) {
//        ConceptChronology concept = Get.concept(conceptNid);
//        LatestVersion<LogicGraphVersion> logicalDef = concept.getLogicalDefinition(getViewStampFilter(), premiseType, getLogicCoordinate());
//        if (logicalDef.isPresent()) {
//            return Optional.of(logicalDef.get().getLogicalExpression());
//        }
//        return Optional.empty();
//    }

    default String toConceptString(Object object, Function<Concept,String> toString) {
        StringBuilder sb = new StringBuilder();
        toConceptString(object, toString, sb);
        return sb.toString();
    }

    default void toConceptString(Object object, Function<Concept,String> toString, StringBuilder sb) {
        if (object == null) {
            return;
        }
        if (object instanceof Concept concept) {
            sb.append(toString.apply(concept));
        } else if (object instanceof Collection) {

            if (object instanceof Set set) {
                // a set, so order does not matter. Alphabetic order desirable.
                if (set.isEmpty()) {
                    toConceptString(set.toArray(), toString, sb);
                } else {
                    Object[] conceptSpecs = set.toArray();
                    Arrays.sort(conceptSpecs, (o1, o2) ->
                            NaturalOrder.compareStrings(toString.apply((Concept) o1), toString.apply((Concept) o2)));
                    toConceptString(conceptSpecs, toString, sb);
                }
            } else {
                // not a set, so order matters
                Collection collection = (Collection) object;
                toConceptString(collection.toArray(), toString, sb);
            }
        } else if (object.getClass().isArray()) {
            Object[] a = (Object[]) object;
            final int iMax = a.length - 1;
            if (iMax == -1) {
                sb.append("[]");
            } else {
                sb.append('[');
                int indent = sb.length();
                for (int i = 0; ; i++) {
                    if (i > 0) {
                        sb.append('\u200A');
                    }
                    sb.append(toConceptString(a[i], toString));
                    if (i == iMax) {
                        sb.append(']').toString();
                        return;
                    }
                    if (iMax > 0) {
                        sb.append(",\n");
                        for (int indentIndex = 0; indentIndex < indent; indentIndex++) {
                            sb.append('\u2004'); //
                        }
                    }
                }
            }
        } else if (object instanceof String) {
            throw new UnsupportedOperationException();
//
//            String string = (String) object;
//            if (string.indexOf(ConceptProxy.FIELD_SEPARATOR) > -1) {
//                ConceptProxy conceptProxy = new ConceptProxy(string);
//                sb.append(toConceptString(conceptProxy, toString));
//            } else {
//                sb.append(string);
//            }
        } else if (object instanceof Long) {
            sb.append(DateTimeUtil.format((Long) object));
        } else {
            sb.append(object.toString());
        }
    }

    Activity getCurrentActivity();

//    default int[] getRootNids() {
//        return this.getNavigationSnapshot().getRootNids();
//    }

//    default int[] getChildNids(ConceptEntity parent) {
//        return getChildNids(parent.nid());
//    }
//    default int[] getChildNids(int parentNid) {
//        return this.getVertexSort().sortVertexes(this.getNavigationSnapshot().getTaxonomyChildConceptNids(parentNid),
//                this.toManifoldCoordinateImmutable());
//    }

//    default boolean isChildOf(ConceptEntity child, ConceptEntity parent) {
//        return isChildOf(child.nid(), parent.nid());
//    }
//    default boolean isChildOf(int childNid, int parentNid) {
//        return this.getNavigationSnapshot().isChildOf(childNid, parentNid);
//    }

//    default boolean isLeaf(ConceptEntity concept) {
//        return isLeaf(concept.nid());
//    }
//    default boolean isLeaf(int nid) {
//        return this.getNavigationSnapshot().isLeaf(nid);
//    }

//    default boolean isKindOf(ConceptEntity child, ConceptEntity parent) {
//        return isKindOf(child.nid(), parent.nid());
//    }
//    default boolean isKindOf(int childNid, int parentNid) {
//        return this.getNavigationSnapshot().isKindOf(childNid, parentNid);
//    }

//    default  ImmutableIntSet getKindOfNidSet(ConceptEntity kind) {
//        return getKindOfNidSet(kind.nid());
//    }
//    default ImmutableIntSet getKindOfNidSet(int kindNid) {
//        return this.getNavigationSnapshot().getKindOfConcept(kindNid);
//    }

//    default boolean isDescendentOf(ConceptEntity descendant, ConceptEntity ancestor) {
//        return isDescendentOf(descendant.nid(), ancestor.nid());
//    }
//    default boolean isDescendentOf(int descendantNid, int ancestorNid) {
//        return this.getNavigationSnapshot().isDescendentOf(descendantNid, ancestorNid);
//    }

//    default ImmutableCollection<Edge> getParentEdges(int parentNid) {
//        return this.getNavigationSnapshot().getTaxonomyParentLinks(parentNid);
//    }
//    default ImmutableCollection<Edge> getParentEdges(ConceptEntity parent) {
//        return getParentEdges(parent.nid());
//    }
//
//    default ImmutableCollection<Edge> getChildEdges(ConceptEntity child) {
//        return getChildEdges(child.nid());
//    }
//    default ImmutableCollection<Edge> getChildEdges(int childNid) {
//        return this.getNavigationSnapshot().getTaxonomyChildLinks(childNid);
//    }

//    default ImmutableCollection<Concept> getRoots() {
//        return IntLists.immutable.of(getRootNids()).collect(nid -> Entity.getFast(nid));
//    }

    default String getPathString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getPreferredDescriptionText(getViewStampFilter().getPathNidForFilter()));
        return sb.toString();
    }

    /**
     * Gets the module nid.
     *
     * @return the module nid
     */
    default int getModuleNidForAnalog(Version version) {
        switch (getCurrentActivity()) {
            case DEVELOPING:
//            case PROMOTING:
//                if (version == null || version.getModuleNid() == 0 || version.getModuleNid() == Integer.MIN_VALUE ||
//                        version.getModuleNid() == Integer.MAX_VALUE || version.getModuleNid() == TinkarTerm.UNINITIALIZED_COMPONENT_ID.nid()) {
//                    return getEditCoordinate().getDefaultModuleNid();
//                }
//                return version.getModuleNid();
            case MODULARIZING:
                return getEditCoordinate().getDestinationModuleNid();
            case VIEWING:
                throw new IllegalStateException("Cannot make analog when viewing [1]. ");
            default:
                throw new UnsupportedOperationException(getCurrentActivity().name());
        }
    }

    default Concept getModuleForAnalog(Version version) {
        return Entity.getFast(getModuleNidForAnalog(version));
    }

    /**
     * Gets the path nid.
     *
     * @return the path nid
     */
    default int getPathNidForAnalog() {
        switch (getCurrentActivity()) {
            case DEVELOPING:
            case MODULARIZING:
                return getViewStampFilter().getPathNidForFilter();
            case PROMOTING:
                return getEditCoordinate().getPromotionPathNid();
            case VIEWING:
                throw new IllegalStateException("Cannot make analog when viewing [2]. ");
            default:
                throw new UnsupportedOperationException(getCurrentActivity().name());
        }
    }

    default Concept getPathForAnalog() {
        return Entity.getFast(getPathNidForAnalog());
    }

    public ManifoldCoordinate makeCoordinateAnalog(long classifyTimeInEpochMillis);
    
    public ManifoldCoordinate makeCoordinateAnalog(PremiseType premiseType);
    
    /**
     * @param stampFilter - new stampFilter to use to in the new ManifoldCoordinate, for both the {@link ManifoldCoordinate#getViewStampFilter()} and
     * {@link ManifoldCoordinate#getVertexStampFilter()} 
     * @return a new manifold coordinate
     */
    default ManifoldCoordinate makeCoordinateAnalog(StampFilter stampFilter) {
        return ManifoldCoordinateImmutable.make(stampFilter, this.getLanguageCoordinate(), this.getVertexSort(), stampFilter.getAllowedStates(), this.getNavigationCoordinate(),
                this.getLogicCoordinate(), this.getCurrentActivity(), this.getEditCoordinate());
    }

    default ManifoldCoordinate makeCoordinateAnalog(Instant classifyInstant) {
        return makeCoordinateAnalog(classifyInstant.toEpochMilli());
    }

    /**
     * @see #getWriteCoordinate(Transaction, Version)
     * @param transaction
     * @return
     */
//    default WriteCoordinate getWriteCoordinate() {
//        return getWriteCoordinate(null, null);
//    }

    /**
     * @see #getWriteCoordinate(Transaction, Version)
     * @param transaction
     * @return
     */
//    default WriteCoordinate getWriteCoordinate(Transaction transaction) {
//        return getWriteCoordinate(transaction, null);
//    }
    
    /**
     * @see #getWriteCoordinate(Transaction, Version, Status)
     * @param transaction
     * @param version
     * @return
     */
//    default WriteCoordinate getWriteCoordinate(Transaction transaction, Version version) {
//        return getWriteCoordinate(transaction, version, null);
//    }

    /**
     * Return a WriteCoordinate based on {@link #getPathNidForAnalog()}, {@link #getModuleNidForAnalog(Version)}, {@link #getAuthorNidForChanges()}
     * @param transaction - optional - used if supplied
     * @param version - optional - used if supplied in {@link #getModuleForAnalog(Version)}
     * @param status - optional - used if supplied
     * @return the equivalent WriteCoordinate
     */
//    default WriteCoordinate getWriteCoordinate(Transaction transaction, Version version, Status status) {
//        return new WriteCoordinate() {
//            @Override
//            public Optional<Transaction> getTransaction() {
//                return Optional.ofNullable(transaction);
//            }
//
//            @Override
//            public int getPathNid() {
//                return ManifoldCoordinate.this.getPathNidForAnalog();
//            }
//
//            @Override
//            public int getModuleNid() {
//                return ManifoldCoordinate.this.getModuleNidForAnalog(version);
//            }
//
//            @Override
//            public int getAuthorNid() {
//                return ManifoldCoordinate.this.getAuthorNidForChanges();
//            }
//
//            @Override
//            public Status getState() {
//                return status == null ? WriteCoordinate.super.getState() : status;
//            }
//        };
//    }
}
