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
package dev.ikm.tinkar.provider.search;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.highlight.*;
import org.eclipse.collections.impl.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Searcher {
    private static final Logger LOG = LoggerFactory.getLogger(Searcher.class);
    public static final EntityProxy.Pattern LIDR_RECORD_PATTERN = EntityProxy.Pattern.make(null, UUID.fromString("c3d52f47-0565-5cfb-9b0b-d7501a33b35d"));
    public static final EntityProxy.Pattern DIAGNOSTIC_DEVICE_PATTERN = EntityProxy.Pattern.make(null, UUID.fromString("a507b3c7-eadb-5d54-84c0-c44f3155d0bc"));
    QueryParser parser;
    private final SearcherManager searcherManager;

    public Searcher() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening lucene searcher");
        this.parser = new QueryParser("text", Indexer.analyzer());
        this.searcherManager = new SearcherManager(Indexer.indexReader(), null);
        stopwatch.stop();
        LOG.info("Opened lucene searcher in: " + stopwatch.durationString());
    }

    public PrimitiveDataSearchResult[] search(String queryString, int maxResultSize) throws
            ParseException, IOException, InvalidTokenOffsetsException {
        boolean refreshOutcome = searcherManager.maybeRefresh();
        LOG.info("Index Reader refresh outcome = " + refreshOutcome);
        IndexSearcher indexSearcher = searcherManager.acquire();
        if (queryString != null & !queryString.isEmpty()) {
            Query query = parser.parse(queryString);
            Formatter formatter = new SimpleHTMLFormatter();
            QueryScorer scorer = new QueryScorer(query);
            Highlighter highlighter = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(new NullFragmenter());

            ScoreDoc[] hits = indexSearcher.search(query, maxResultSize).scoreDocs;
            PrimitiveDataSearchResult[] results = new PrimitiveDataSearchResult[hits.length];
            for (int i = 0; i < hits.length; i++) {
                Document hitDoc = indexSearcher.doc(hits[i].doc);
                StoredField nidField = (StoredField) hitDoc.getField(Indexer.NID);
                StoredField patternNidField = (StoredField) hitDoc.getField(Indexer.PATTERN_NID);
                StoredField rcNidField = (StoredField) hitDoc.getField(Indexer.RC_NID);
                StoredField fieldIndexField = (StoredField) hitDoc.getField(Indexer.FIELD_INDEX);
                StoredField textField = (StoredField) hitDoc.getField(Indexer.TEXT_FIELD_NAME);
                String highlightedString = highlighter.getBestFragment(Indexer.analyzer(), Indexer.TEXT_FIELD_NAME, textField.stringValue());

                results[i] = new PrimitiveDataSearchResult(nidField.numericValue().intValue(), rcNidField.numericValue().intValue(),
                        patternNidField.numericValue().intValue(), fieldIndexField.numericValue().intValue(), hits[i].score, highlightedString);
            }
            return results;
        }
        searcherManager.release(indexSearcher);
        return new PrimitiveDataSearchResult[0];
    }

    /**
     * Returns a default navigation calculator with coordinates for
     * inferred navigation, active stamps on development path, & english synonyms
     *
     * @return  NavigationCalculator
     */
    private static NavigationCalculator defaultNavigationCalculator() {
        StampCoordinateRecord stampCoordinateRecord = Coordinates.Stamp.DevelopmentLatestActiveOnly();
        LanguageCoordinateRecord languageCoordinateRecord = Coordinates.Language.UsEnglishRegularName();
        NavigationCoordinateRecord navigationCoordinateRecord = Coordinates.Navigation.inferred().toNavigationCoordinateRecord();
        return NavigationCalculatorWithCache.getCalculator(stampCoordinateRecord, Lists.immutable.of(languageCoordinateRecord), navigationCoordinateRecord);
    }

    /**
     * Returns List of Children PublicIds for the Entity PublicId provided
     * using the default NavigationCalculator from {@link #defaultNavigationCalculator()}
     *
     * @param   parentConceptId PublicId of the parent concept
     * @return  List of PublicIds for the children concepts
     */
    public static List<PublicId> childrenOf(PublicId parentConceptId) {
        return childrenOf(defaultNavigationCalculator(), parentConceptId);
    }

    /**
     * Returns List of Children PublicIds for the Entity PublicId provided
     * using a supplied NavigationCalculator
     * <p>
     * Provided as an ease-of-use method to convert nids provided by the
     * {@link NavigationCalculator#childrenOf(int)} method to PublicIds
     *
     * @param   navCalc NavigationCalculator to calculate children
     * @param   parentConceptId PublicId of the parent concept
     * @return  List of PublicIds for the children concepts
     */
    public static List<PublicId> childrenOf(NavigationCalculator navCalc, PublicId parentConceptId) {
        List<PublicId> childIds = new ArrayList<>();
        int[] childNidList = navCalc.childrenOf(EntityService.get().nidForPublicId(parentConceptId)).toArray();
        for (int childNid : childNidList) {
            EntityService.get().getEntity(childNid).ifPresent((entity) -> childIds.add(entity.publicId()));
        }
        return childIds;
    }

    /**
     * Returns List of descendant PublicIds for the Entity PublicId provided
     * using the default NavigationCalculator from {@link #defaultNavigationCalculator()}
     *
     * @param   ancestorConceptId PublicId of the ancestor concept
     * @return  List of PublicIds for the descendant concepts
     */
    public static List<PublicId> descendantsOf(PublicId ancestorConceptId) {
        return descendantsOf(defaultNavigationCalculator(), ancestorConceptId);
    }

    /**
     * Returns List of descendant PublicIds for the Entity PublicId provided
     * using a supplied NavigationCalculator
     * <p>
     * Provided as an ease-of-use method to convert nids provided by the
     * {@link NavigationCalculator#descendentsOf(int)} method to PublicIds
     *
     * @param   navCalc NavigationCalculator to calculate descendants
     * @param   ancestorConceptId PublicId of the ancestor concept
     * @return  List of PublicIds for the descendant concepts
     */
    public static List<PublicId> descendantsOf(NavigationCalculator navCalc, PublicId ancestorConceptId) {
        List<PublicId> descendantIds = new ArrayList<>();
        int[] descendantNidList = navCalc.descendentsOf(EntityService.get().nidForPublicId(ancestorConceptId)).toArray();
        for (int descendantNid : descendantNidList) {
            EntityService.get().getEntity(descendantNid).ifPresent((entity) -> descendantIds.add(entity.publicId()));
        }
        return descendantIds;
    }

    /**
     * Returns List of Fully Qualified Name (FQN) Strings for the Entity PublicIds provided
     * using the default NavigationCalculator from {@link #defaultNavigationCalculator()}
     *
     * @param   conceptIds List of PublicIds for concepts with FQNs to return
     * @return  List of FQN Strings with indexes matching the supplied List of PublicIds
     */
    public static List<String> descriptionsOf(List<PublicId> conceptIds) {
        return descriptionsOf(defaultNavigationCalculator(), conceptIds);
    }

    /**
     * Returns List of Fully Qualified Name (FQN) Strings for the Entity PublicIds provided
     * using a supplied NavigationCalculator
     * <p>
     * Provided as an ease-of-use method to retrieve FQNs for multiple concepts at once
     * using the {@link NavigationCalculator#getFullyQualifiedNameText(int)} method
     *
     * @param   navCalc NavigationCalculator to calculate FQNs
     * @param   conceptIds List of PublicIds for concepts with FQNs to return
     * @return  List of FQN Strings with indexes matching the supplied List of PublicIds
     */
    public static List<String> descriptionsOf(NavigationCalculator navCalc, List<PublicId> conceptIds) {
        List<String> names = new ArrayList<>();
        for (PublicId pid : conceptIds) {
            navCalc.getFullyQualifiedNameText(EntityService.get().nidForPublicId(pid))
                    .ifPresentOrElse(names::add,
                        () -> {
                            LOG.warn("FQN not defined for " + pid.idString());
                            names.add("");
                        }
                    );
        }
        return names;
    }

    /**
     * Returns List of LIDR Record PublicIds for the provided Test Kit Device.
     * <p>
     * Provided as an ease-of-use method to retrieve LIDR Record Ids for a Test Kit Device.
     *
     * @param   testKitId associated Test Kit Device PublicId
     * @return  List of Public Ids given test kit concept and lidr record pattern.
     */
    public static List<PublicId> getLidrRecordSemanticsFromTestKit(PublicId testKitId){
        List<PublicId> lidrRecordSemanticIds = new ArrayList<>();

        EntityService.get().getEntity(testKitId.asUuidArray()).ifPresent((testKitEntity) -> {
            EntityService.get().forEachSemanticForComponentOfPattern(testKitEntity.nid(), DIAGNOSTIC_DEVICE_PATTERN.nid(), diagnosticDeviceSemantic -> {
                EntityService.get().forEachSemanticForComponentOfPattern(diagnosticDeviceSemantic.nid(), LIDR_RECORD_PATTERN.nid(), lidrRecordSemantic -> {
                    lidrRecordSemanticIds.add(lidrRecordSemantic.publicId());
                });
            });
        });

        return lidrRecordSemanticIds;
    }

    /**
     * Returns List of Result Conformance PublicIds for all LIDR Records of the Test Kit Device
     * <p>
     * Provided as an ease-of-use method to retrieve Result Conformances / Constraints for a Test Kit
     *
     * @param   testKitId PublicId of the Test Kit Device
     * @return  List of PublicIds for all Result Conformances / Constraints for the Test Kit Device
     */
    public static List<PublicId> getResultConformanceFromTestKit(PublicId testKitId) {
        List<PublicId> resultConformanceList = new ArrayList<>();

        for (PublicId lidrRecordId : getLidrRecordSemanticsFromTestKit(testKitId)) {
            EntityService.get().getEntity(lidrRecordId.asUuidArray()).ifPresent((lidrRecordEntity) -> {
                SemanticEntityVersion lidrRecordSemanticVersion = (SemanticEntityVersion) lidrRecordEntity.versions().get(0);
                int idxResultConformances = 5;
                ((IntIdSet) lidrRecordSemanticVersion.fieldValues().get(idxResultConformances))
                        .map(PrimitiveData::publicId)
                        .forEach(resultConformanceList::add);
            });
        }
        return resultConformanceList;
    }

}
