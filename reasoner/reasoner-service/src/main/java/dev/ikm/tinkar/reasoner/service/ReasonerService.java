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
package dev.ikm.tinkar.reasoner.service;

import java.util.List;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;

import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.terms.PatternFacade;

public interface ReasonerService {

	void init(ViewCalculator viewCalculator, PatternFacade statedAxiomPattern,
			PatternFacade inferredAxiomPattern);

	default String getName() {
		return this.getClass().getSimpleName();
	}

	ViewCalculator getViewCalculator();

	PatternFacade getStatedAxiomPattern();

	PatternFacade getInferredAxiomPattern();

	void extractData(TrackingCallable<?> progressTracker) throws Exception;

	void loadData(TrackingCallable<?> progressTracker) throws Exception;

	void computeInferences(TrackingCallable<?> progressTracker);

	default void computeInferences() {
		computeInferences(new TrackingCallable<Object>() {
			@Override
			protected Object compute() throws Exception {
				return null;
			}
		});
	}

	boolean isIncrementalReady();

	@Deprecated
	void processIncremental(DiTreeEntity definition, int conceptNid, TrackingCallable<?> progressUpdater);

	default void processIncremental(DiTreeEntity definition, int conceptNid) {
		processIncremental(definition, conceptNid, new TrackingCallable<Object>() {
			@Override
			protected Object compute() throws Exception {
				return null;
			}
		});
	}

	void processIncremental(SemanticEntityVersion update, TrackingCallable<?> progressUpdater);

	default void processIncremental(SemanticEntityVersion update) {
		processIncremental(update, new TrackingCallable<Object>() {
			@Override
			protected Object compute() throws Exception {
				return null;
			}
		});
	}

	void processIncremental(List<Integer> deletes, List<SemanticEntityVersion> updates, TrackingCallable<?> progressUpdater);

	default void processIncremental(List<Integer> deletes, List<SemanticEntityVersion> updates) {
		this.processIncremental(deletes, updates, new TrackingCallable<Object>() {
			@Override
			protected Object compute() throws Exception {
				return null;
			}
		});
	}

	void buildNecessaryNormalForm(TrackingCallable<?> progressUpdater);

	default void buildNecessaryNormalForm() {
		this.buildNecessaryNormalForm(new TrackingCallable<Object>() {
			@Override
			protected Object compute() throws Exception {
				return null;
			}
		});
	}

	ClassifierResults writeInferredResults(TrackingCallable<?> progressUpdater);
	default ClassifierResults writeInferredResults() {
		return writeInferredResults(new TrackingCallable<Object>() {
			@Override
			protected Object compute() throws Exception {
				return null;
			}
		});
	}

	int getConceptCount();

	ImmutableIntList getReasonerConceptSet();

	ImmutableIntSet getEquivalent(int id);

	ImmutableIntSet getParents(int id);

	ImmutableIntSet getChildren(int id);

	LogicalExpression getNecessaryNormalForm(int id);

	@Deprecated
	ClassifierResults processResults(boolean reinferAllHierarchy, TrackingCallable<ClassifierResults> trackingCallable) throws Exception;

	@Deprecated
	default ClassifierResults processResults(boolean reinferAllHierarchy) throws Exception {
		return processResults(reinferAllHierarchy, new TrackingCallable<ClassifierResults>() {
			@Override
			protected ClassifierResults compute() throws Exception {
				return null;
			}
		});
	}

}
