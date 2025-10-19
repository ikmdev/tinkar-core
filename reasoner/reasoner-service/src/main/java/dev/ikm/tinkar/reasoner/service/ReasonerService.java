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

	public void init(ViewCalculator viewCalculator, PatternFacade statedAxiomPattern,
			PatternFacade inferredAxiomPattern);

	public default String getName() {
		return this.getClass().getSimpleName();
	}

	public ViewCalculator getViewCalculator();

	public PatternFacade getStatedAxiomPattern();

	public PatternFacade getInferredAxiomPattern();

	public TrackingCallable<?> getProgressUpdater();

	public void setProgressUpdater(TrackingCallable<?> progressUpdater);

	public void extractData() throws Exception;

	public void loadData() throws Exception;

	public void computeInferences();

	public boolean isIncrementalReady();

	@Deprecated
	public void processIncremental(DiTreeEntity definition, int conceptNid);

	public void processIncremental(SemanticEntityVersion update);

	public void processIncremental(List<Integer> deletes, List<SemanticEntityVersion> updates);

	public void buildNecessaryNormalForm();

	public ClassifierResults writeInferredResults();

	public int getConceptCount();

	public ImmutableIntList getReasonerConceptSet();

	public ImmutableIntSet getEquivalent(int id);

	public ImmutableIntSet getParents(int id);

	public ImmutableIntSet getChildren(int id);

	public LogicalExpression getNecessaryNormalForm(int id);

	@Deprecated
	public ClassifierResults processResults(TrackingCallable<ClassifierResults> trackingCallable,
			boolean reinferAllHierarchy) throws Exception;

}
