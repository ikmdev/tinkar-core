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

import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.terms.PatternFacade;

public abstract class ReasonerServiceBase implements ReasonerService {

	protected ViewCalculator viewCalculator;

	protected PatternFacade statedAxiomPattern;

	protected PatternFacade inferredAxiomPattern;

	@Override
	public ViewCalculator getViewCalculator() {
		return viewCalculator;
	}

	@Override
	public PatternFacade getStatedAxiomPattern() {
		return statedAxiomPattern;
	}

	@Override
	public PatternFacade getInferredAxiomPattern() {
		return inferredAxiomPattern;
	}

	@Override
	public void init(ViewCalculator viewCalculator, PatternFacade statedAxiomPattern,
			PatternFacade inferredAxiomPattern) {
		this.viewCalculator = viewCalculator;
		this.statedAxiomPattern = statedAxiomPattern;
		this.inferredAxiomPattern = inferredAxiomPattern;
	}

	@Override
	public ClassifierResults writeInferredResults(TrackingCallable<?> progressUpdater) {
		InferredResultsWriter nnfw = new InferredResultsWriter(this, progressUpdater);
		return nnfw.write();
	}

	@Override
	public ClassifierResults processResults(boolean reinferAllHierarchy, TrackingCallable<ClassifierResults> callable)
			throws Exception {
		ProcessReasonerResults task = new ProcessReasonerResults(this, reinferAllHierarchy, callable);
		return task.compute();
	}

}
