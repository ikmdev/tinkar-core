package org.hl7.tinkar.coordinate.manifold;


import java.util.EnumSet;
import java.util.Objects;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.edit.Activity;
import org.hl7.tinkar.coordinate.edit.EditCoordinate;
import org.hl7.tinkar.coordinate.edit.EditCoordinateImmutable;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateImmutable;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateImmutable;
import org.hl7.tinkar.coordinate.logic.PremiseSet;
import org.hl7.tinkar.coordinate.logic.PremiseType;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateImmutable;
import org.hl7.tinkar.coordinate.view.VertexSort;
import org.hl7.tinkar.coordinate.view.VertexSortNone;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.coordinate.stamp.StampFilterRecord;
import org.hl7.tinkar.coordinate.stamp.StateSet;

//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
@AutoService(CachingService.class)
@Deprecated
public abstract class ManifoldCoordinateImmutable implements ManifoldCoordinate, ImmutableCoordinate, /*CommitListener, */ CachingService {

    private static final ConcurrentReferenceHashMap<ManifoldCoordinateImmutable, ManifoldCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int marshalVersion = 6;


    private final StampFilterRecord viewStampFilter;
    private final LanguageCoordinateImmutable languageCoordinate;
    private final VertexSort vertexSort;
    private final StampFilterRecord vertexStampFilter;
    private final NavigationCoordinateImmutable navigationCoordinateImmutable;
    private final LogicCoordinateImmutable logicCoordinateImmutable;
    private final Activity activity;
    private final EditCoordinateImmutable editCoordinate;
    //private transient TaxonomySnapshot digraphSnapshot;
    private transient PremiseSet premiseTypes;

    private ManifoldCoordinateImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.navigationCoordinateImmutable = null;
        this.vertexSort = null;
        this.vertexStampFilter = null;
        this.viewStampFilter = null;
        this.languageCoordinate = null;
        this.logicCoordinateImmutable = null;
        this.activity = null;
        this.editCoordinate = null;
    }
    
    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    /**
     * @see #make(StampFilter, LanguageCoordinate, VertexSort, StateSet, NavigationCoordinate, LogicCoordinate, Activity, EditCoordinate)
     * for defaults on null values 
     */
    private ManifoldCoordinateImmutable(StampFilterRecord viewStampFilter,
                                        LanguageCoordinateImmutable languageCoordinate,
                                        VertexSort vertexSort,
                                        StateSet vertexStateSet,
                                        NavigationCoordinateImmutable navigationCoordinateImmutable,
                                        LogicCoordinateImmutable logicCoordinateImmutable,
                                        Activity activity,
                                        EditCoordinateImmutable editCoordinate) {
        throw new UnsupportedOperationException();
//
//        this.viewStampFilter = viewStampFilter == null ?
//            Get.configurationService().getGlobalDatastoreConfiguration().getDefaultManifoldCoordinate().getViewStampFilter().toStampFilterImmutable() : viewStampFilter;
//        this.languageCoordinate = languageCoordinate == null ?
//            Get.configurationService().getGlobalDatastoreConfiguration().getDefaultLanguageCoordinate().toLanguageCoordinateImmutable() : languageCoordinate;
//        this.vertexSort = vertexSort == null ? VertexSortNone.SINGLETON : vertexSort;
//        this.vertexStampFilter = vertexStateSet == null ? viewStampFilter : StampFilterImmutable.make(vertexStateSet,
//                viewStampFilter.getStampPosition(),
//                viewStampFilter.getModuleNids(),
//                viewStampFilter.getExcludedModuleNids(),
//                viewStampFilter.getModulePriorityOrder());
//        this.navigationCoordinateImmutable = navigationCoordinateImmutable == null ?
//            Get.configurationService().getGlobalDatastoreConfiguration().getDefaultManifoldCoordinate().toNavigationCoordinateImmutable() : navigationCoordinateImmutable;
//        this.logicCoordinateImmutable = logicCoordinateImmutable == null ?
//            Get.configurationService().getGlobalDatastoreConfiguration().getDefaultLogicCoordinate().toLogicCoordinateImmutable() : logicCoordinateImmutable;
//        this.activity = activity == null ? Activity.DEVELOPING : activity;
//        this.editCoordinate = editCoordinate == null ? Get.configurationService().getGlobalDatastoreConfiguration().getDefaultWriteCoordinate().get().toEditCoordinate() :
//            editCoordinate;
    }

    private ManifoldCoordinateImmutable(DecoderInput in, int objectMarshalVersion) {
        switch (objectMarshalVersion) {
            case marshalVersion:
                throw new UnsupportedOperationException();
//                this.vertexSort = MarshalUtil.unmarshal(in);
//                this.vertexStampFilter = MarshalUtil.unmarshal(in);
//                this.viewStampFilter = MarshalUtil.unmarshal(in);
//                this.languageCoordinate  = MarshalUtil.unmarshal(in);
//                this.navigationCoordinateImmutable = MarshalUtil.unmarshal(in);
//                this.logicCoordinateImmutable = MarshalUtil.unmarshal(in);
//                this.activity = MarshalUtil.unmarshal(in);
//                this.editCoordinate = MarshalUtil.unmarshal(in);
//                break;

            default:
                throw new IllegalStateException("Can't handle marshalVersion: " + objectMarshalVersion);
        }

        // this.digraphSnapshot = Get.taxonomyService().getSnapshot(toDefaultManifold(this));
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        throw new UnsupportedOperationException();
//        out.putInt(marshalVersion);
//        MarshalUtil.marshal(this.vertexSort, out);
//        MarshalUtil.marshal(this.vertexStampFilter, out);
//        MarshalUtil.marshal(this.viewStampFilter, out);
//        MarshalUtil.marshal(this.languageCoordinate, out);
//        MarshalUtil.marshal(this.navigationCoordinateImmutable, out);
//        MarshalUtil.marshal(this.logicCoordinateImmutable, out);
//        MarshalUtil.marshal(this.activity, out);
//        MarshalUtil.marshal(this.editCoordinate, out);
    }

    @Override
    public NavigationCoordinateImmutable getNavigationCoordinate() {
        return this.navigationCoordinateImmutable;
    }

    @Override
    public LogicCoordinateImmutable getLogicCoordinate() {
        return this.logicCoordinateImmutable;
    }

    @Override
    public LanguageCoordinateImmutable getLanguageCoordinate() {
        return this.languageCoordinate;
    }

    @Override
    public EditCoordinate getEditCoordinate() {
        return this.editCoordinate;
    }

    @Decoder
    public static ManifoldCoordinateImmutable decode(DecoderInput in) {
        // Using a static method rather than a constructor eliminates the need for
        // a readResolve method, but allows the implementation to decide how
        // to handle special cases.
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case 1:
            case 3:
            case 4:
            case marshalVersion:
                throw new UnsupportedOperationException();
//
//                return SINGLETONS.computeIfAbsent(new ManifoldCoordinateImmutable(in, objectMarshalVersion),
//                        manifoldCoordinateImmutable -> manifoldCoordinateImmutable);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }
    
    /**
     * 
     * @param viewStampFilter - if null, uses {@link GlobalDatastoreConfiguration#getDefaultManifoldCoordinate()#getViewStampFilter()}
     * @param languageCoordinate - if null, uses {@link GlobalDatastoreConfiguration#getDefaultLanguageCoordinate()}
     * @param vertexSort - if null, does no sorting (using {@link VertexSortNone} )
     * @param vertexStateSet - if null, use the statusSet from the viewStampFilter
     * @param navigationCoordinate - if null, uses {@link GlobalDatastoreConfiguration#getDefaultManifoldCoordinate()#toNavigationCoordinateImmutable()
     * @param logicCoordinate - if null, uses {@link GlobalDatastoreConfiguration#getDefaultLogicCoordinate()}
     * @param activity - if null, uses {@link Activity#DEVELOPING}
     * @param editCoordinate - if null, uses the {@link GlobalDatastoreConfiguration#getDefaultWriteCoordinate()} converted to an edit coordinate
     * @return
     */
    public static ManifoldCoordinateImmutable make(StampFilter viewStampFilter,
                                                   LanguageCoordinate languageCoordinate,
                                                   VertexSort vertexSort,
                                                   StateSet vertexStateSet,
                                                   NavigationCoordinate navigationCoordinate,
                                                   LogicCoordinate logicCoordinate,
                                                   Activity activity,
                                                   EditCoordinate editCoordinate) {
        throw new UnsupportedOperationException();
//         return SINGLETONS.computeIfAbsent(new ManifoldCoordinateImmutable(viewStampFilter.toStampFilterImmutable(),
//                 languageCoordinate == null ? null : languageCoordinate.toLanguageCoordinateImmutable(),
//                 vertexSort,
//                         vertexStateSet,
//                 navigationCoordinate == null ? null : navigationCoordinate.toNavigationCoordinateImmutable(),
//                 logicCoordinate == null ? null : logicCoordinate.toLogicCoordinateImmutable(),
//                 activity,
//                 editCoordinate == null ? null : editCoordinate.toEditCoordinateImmutable()),
//                        manifoldCoordinateImmutable -> manifoldCoordinateImmutable);
    }

    /**
     * @param stampFilter
     * @param languageCoordinate - optional - uses default if not provided
     * @return
     */
    public static ManifoldCoordinateImmutable makeStated(StampFilter stampFilter, LanguageCoordinate languageCoordinate) {
        throw new UnsupportedOperationException();
//        return SINGLETONS.computeIfAbsent(new ManifoldCoordinateImmutable(stampFilter.toStampFilterImmutable(),
//                        languageCoordinate == null ?
//                                Get.configurationService().getGlobalDatastoreConfiguration().getDefaultLanguageCoordinate().toLanguageCoordinateImmutable() :
//                            languageCoordinate.toLanguageCoordinateImmutable(),
//                        VertexSortNaturalOrder.SINGLETON,
//                        stampFilter.getAllowedStates(), NavigationCoordinateImmutable.makeStated(),
//                        Get.configurationService().getGlobalDatastoreConfiguration().getDefaultLogicCoordinate().toLogicCoordinateImmutable(),
//                        Activity.DEVELOPING, Get.configurationService().getGlobalDatastoreConfiguration().getDefaultWriteCoordinate().get().toEditCoordinate()),
//                manifoldCoordinateImmutable -> manifoldCoordinateImmutable);
    }

    public static ManifoldCoordinateImmutable makeStated(StampFilter stampFilter, LanguageCoordinate languageCoordinate,
                                                         LogicCoordinate logicCoordinate, Activity activity, EditCoordinate editCoordinate) {
        throw new UnsupportedOperationException();
//        NavigationCoordinateImmutable dci = NavigationCoordinateImmutable.makeStated(logicCoordinate);
//        return SINGLETONS.computeIfAbsent(new ManifoldCoordinateImmutable(stampFilter.toStampFilterImmutable(),
//                        languageCoordinate == null ? null : languageCoordinate.toLanguageCoordinateImmutable(),
//                        VertexSortNaturalOrder.SINGLETON,
//                        stampFilter.getAllowedStates(), dci,
//                        Coordinates.Logic.ElPlusPlus(),
//                        activity,
//                        editCoordinate == null ? null : editCoordinate.toEditCoordinateImmutable()),
//                manifoldCoordinateImmutable -> manifoldCoordinateImmutable);
    }

    public static ManifoldCoordinateImmutable makeInferred(StampFilter stampFilter,
                                                           LanguageCoordinate languageCoordinate,
                                                           LogicCoordinate logicCoordinate,
                                                           Activity activity, EditCoordinate editCoordinate) {
        throw new UnsupportedOperationException();
//        NavigationCoordinateImmutable nci = NavigationCoordinateImmutable.makeInferred(logicCoordinate);
//        return SINGLETONS.computeIfAbsent(new ManifoldCoordinateImmutable(stampFilter.toStampFilterImmutable(),
//                        languageCoordinate == null ? null : languageCoordinate.toLanguageCoordinateImmutable(),
//                        VertexSortNaturalOrder.SINGLETON,
//                        stampFilter.getAllowedStates(),
//                        nci,
//                        Coordinates.Logic.ElPlusPlus(),
//                        activity,
//                        editCoordinate == null ? null : editCoordinate.toEditCoordinateImmutable()),
//                manifoldCoordinateImmutable -> manifoldCoordinateImmutable);
    }

    @Override
    public ManifoldCoordinateImmutable toManifoldCoordinateImmutable() {
        return this;
    }

    @Override
    public Activity getCurrentActivity() {
        return activity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManifoldCoordinateImmutable that)) return false;
        return this.navigationCoordinateImmutable.equals(that.navigationCoordinateImmutable) &&
                this.vertexSort.equals(that.vertexSort) &&
                this.vertexStampFilter.equals(that.vertexStampFilter) &&
                this.viewStampFilter.equals(that.viewStampFilter) &&
                this.languageCoordinate.equals(that.languageCoordinate) &&
                this.navigationCoordinateImmutable.equals(that.navigationCoordinateImmutable) &&
                this.activity == that.activity;
    }

    @Override
    public PremiseSet getPremiseTypes() {
        if (this.premiseTypes == null) {
            EnumSet<PremiseType> premiseTypeEnumSet = EnumSet.noneOf(PremiseType.class);
            if (getNavigationCoordinate().getNavigationConceptNids().contains(getLogicCoordinate().getInferredAxiomsPatternNid())) {
                premiseTypeEnumSet.add(PremiseType.INFERRED);
            }
            if (getNavigationCoordinate().getNavigationConceptNids().contains(getLogicCoordinate().getStatedAxiomsPatternNid())) {
                premiseTypeEnumSet.add(PremiseType.STATED);
            }
            this.premiseTypes = PremiseSet.of(premiseTypeEnumSet);
        }
        return this.premiseTypes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getManifoldCoordinateUuid());
    }

    @Override
    public VertexSort getVertexSort() {
        return this.vertexSort;
    }

    @Override
    public StateSet getVertexStatusSet() {
        return this.vertexStampFilter.allowedStates();
    }

    @Override
    public StampFilterRecord getViewStampFilter() {
        return this.viewStampFilter;
    }

    @Override
    public StampFilter getVertexStampFilter() {
        return this.vertexStampFilter;
    }

    @Override
    public String toString() {
        return "ManifoldCoordinateImmutable{" + this.activity.toUserString() + ",\n  " +
                this.getNavigationCoordinate().toUserString() +
                ",\n  sort: " + this.vertexSort.getVertexSortName() +
                ",\n  view filter: " + this.viewStampFilter +
                ", \n vertex filter: " + this.vertexStampFilter +
                ", \n language:" + this.languageCoordinate +
                ", \n logic:" + this.logicCoordinateImmutable +
                ",\n current activity=" + getCurrentActivity() +
                ",\n edit=" + getEditCoordinate() +
                ",\n uuid=" + getManifoldCoordinateUuid() + '}';
    }

//    @Override
//    public TaxonomySnapshot getNavigationSnapshot() {
//        if (this.digraphSnapshot == null) {
//            this.digraphSnapshot = Get.taxonomyService().getSnapshot(this);
//            Get.commitService().addCommitListener(this);
//        }
//        return this.digraphSnapshot;
//    }

//    @Override
//    public UUID getListenerUuid() {
//        return this.getManifoldCoordinateUuid();
//    }

//    @Override
//    public void handleCommit(CommitRecord commitRecord) {
//        this.digraphSnapshot = null;
//        Get.commitService().removeCommitListener(this);
//    }

    @Override
    public ManifoldCoordinateImmutable makeCoordinateAnalog(long classifyTimeInEpochMillis) {
        throw new UnsupportedOperationException();
//        return new ManifoldCoordinateImmutable(
//                viewStampFilter.makeCoordinateAnalog(classifyTimeInEpochMillis),
//                languageCoordinate,
//                vertexSort,
//                getVertexStatusSet(),
//                navigationCoordinateImmutable,
//                logicCoordinateImmutable,
//                activity,
//                editCoordinate);
    }
    
    @Override
    public ManifoldCoordinateImmutable makeCoordinateAnalog(PremiseType premiseType) {
        throw new UnsupportedOperationException();
//        return new ManifoldCoordinateImmutable(
//                viewStampFilter,
//                languageCoordinate,
//                vertexSort,
//                getVertexStatusSet(),
//                NavigationCoordinateImmutable.make(premiseType),
//                logicCoordinateImmutable,
//                activity,
//                editCoordinate);
    }
}
