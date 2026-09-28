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
package dev.ikm.tinkar.reasoner.elksnomed;

import java.util.HashMap;
import java.util.concurrent.CancellationException;
import java.util.List;
import java.util.Set;

import dev.ikm.tinkar.common.service.TrackingCallable;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.NecessaryNormalFormBuilder;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.elk.snomed.SnomedOntologyReasoner;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.Definition;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.ext.lang.owl.OwlElToLogicalExpression;
import dev.ikm.tinkar.reasoner.service.ReasonerServiceBase;
import dev.ikm.tinkar.reasoner.service.UnsupportedReasonerProcessIncremental;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public class ElkSnomedReasonerService extends ReasonerServiceBase {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedReasonerService.class);

	protected ElkSnomedData data;

	protected ElkSnomedDataBuilder builder;

	protected SnomedOntology ontology;

	protected SnomedOntologyReasoner reasoner;

	protected NecessaryNormalFormBuilder nnfb;

	@Override
	public String getName() {
		return "Elk Snomed Reasoner";
	}

	@Override
	public void init(ViewCalculator viewCalculator, PatternFacade statedAxiomPattern,
			PatternFacade inferredAxiomPattern) {
		super.init(viewCalculator, statedAxiomPattern, inferredAxiomPattern);
		this.data = null;
		this.builder = null;
		this.ontology = null;
		this.reasoner = null;
		this.nnfb = null;
	}

	@Override
	public void extractData(TrackingCallable<?> progressTracker) throws Exception {
		data = new ElkSnomedData();
		builder = new ElkSnomedDataBuilder(viewCalculator, statedAxiomPattern, data);
		builder.setProgressUpdater(progressTracker);
		builder.build();
	};

	@Override
	public void loadData(TrackingCallable<?> progressTracker) throws Exception {
		LOG.info("Create ontology");
		ontology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(), List.of());
		LOG.info("Create reasoner");
		// createUninitialized rather than create: this has to hold the reasoner before the
		// classification starts, or there is nothing to interrupt while it runs. It also keeps
		// the pipeline's phases honest — the classification now happens in computeInferences,
		// which is what the caller reports it as.
		reasoner = SnomedOntologyReasoner.createUninitialized(ontology);
	}

	@Override
	public void computeInferences(TrackingCallable<?> progressTracker) {
		LOG.info("Compute inferences");
		try (CancelWatcher ignored = new CancelWatcher(progressTracker, reasoner)) {
			reasoner.computeInferences();
		} catch (RuntimeException e) {
			// An interrupted ELK run throws ElkInterruptedException, which SnomedOntologyReasoner
			// wraps in a plain RuntimeException. When a cancel was requested that is the cancel
			// taking effect, not a failure — report it as one so callers can tell the two apart.
			if (isCancelled(progressTracker)) {
				CancellationException cancelled =
						new CancellationException("Reasoner classification was cancelled");
				cancelled.initCause(e);
				throw cancelled;
			}
			throw e;
		}
		if (isCancelled(progressTracker)) {
			// Covers a cancel that lands just as ELK finishes, after its last interrupt check:
			// the classification completed but was not wanted, so do not go on to write it.
			throw new CancellationException("Reasoner classification was cancelled");
		}
	}

	private static boolean isCancelled(TrackingCallable<?> progressTracker) {
		return progressTracker != null && progressTracker.isCancelled();
	}

	/**
	 * Interrupts the reasoner when the caller cancels.
	 *
	 * <p>A poller rather than a callback because the thread that starts the classification is
	 * blocked inside ELK until it finishes, so it cannot notice a cancel itself, and
	 * {@link TrackingCallable} offers no completion hook to hang one on.
	 *
	 * <p>The poll interval is coarse on purpose: cancelling a classification that runs for
	 * minutes does not need sub-second latency, and a tight loop would burn a core for the
	 * duration of every run, cancelled or not.
	 */
	private static final class CancelWatcher implements AutoCloseable {

		private static final long POLL_INTERVAL_MS = 250L;

		private final Thread watcher;
		private volatile boolean stopped;

		private CancelWatcher(TrackingCallable<?> progressTracker, SnomedOntologyReasoner reasoner) {
			if (progressTracker == null) {
				this.watcher = null;
				return;
			}
			this.watcher = new Thread(() -> {
				while (!stopped) {
					if (progressTracker.isCancelled()) {
						LOG.info("Cancel requested — interrupting the reasoner");
						reasoner.interrupt();
						return;
					}
					try {
						Thread.sleep(POLL_INTERVAL_MS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}, "elk-cancel-watcher");
			this.watcher.setDaemon(true);
			this.watcher.start();
		}

		@Override
		public void close() {
			stopped = true;
			if (watcher != null) {
				watcher.interrupt();
			}
		}
	}

	@Override
	public boolean isIncrementalReady() {
		return reasoner != null;
	}

	@Override
	public void processIncremental(DiTreeEntity definition, int conceptNid, TrackingCallable<?> progressUpdater) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void processIncremental(SemanticEntityVersion update, TrackingCallable<?> progressUpdater) {
		processIncremental(List.of(), List.of(update), new TrackingCallable<Object>() {
			@Override
			protected Object compute() throws Exception {
				return null;
			}
		});
	}

	@Override
	public void processIncremental(List<Integer> deletes, List<SemanticEntityVersion> updates, TrackingCallable<?> progressUpdater) {
		for (int delete : deletes) {
			Concept concept = builder.processDelete(delete);
			if (concept != null) {
				reasoner.processDelete(concept);
			} else {
				LOG.error("No entity for: " + delete + " " + PrimitiveData.text(delete));
			}
		}
		for (SemanticEntityVersion update : updates) {
			Concept concept = builder.processUpdate(update);
			ontology.addConcept(concept);
			reasoner.processUpdate(concept);
		}
		data.initializeReasonerConceptSet();
		try {
			reasoner.flush();
		} catch (Exception ex) {
			LOG.error(ex.getMessage());
			throw new UnsupportedReasonerProcessIncremental(ex);
		}
	}

	@Override
	public void buildNecessaryNormalForm(TrackingCallable<?> progressUpdater) {
		// Convert Set<Long> to MutableLongSet
		MutableLongObjectMap<MutableLongSet> superConcepts = reasoner.getSuperConcepts();
		MutableLongObjectMap<MutableLongSet> superRoleTypes = reasoner.getSuperRoleTypes(false);
		logMissingReasonerConcepts(superConcepts);
		
		nnfb = NecessaryNormalFormBuilder.create(ontology, superConcepts, superRoleTypes, 
			TinkarTerm.ROOT_VERTEX.nid(), 
			(int workDone, int max) -> progressUpdater.updateProgress(workDone, max));
		nnfb.generate();
	}

	private void logMissingReasonerConcepts(MutableLongObjectMap<MutableLongSet> superConcepts) {
		ImmutableIntList conceptSet = data.getReasonerConceptSet();
		int missingCount = 0;
		StringBuilder sample = new StringBuilder();
		int sampleLimit = 10;
		for (int nid : conceptSet.toArray()) {
			if (!superConcepts.containsKey((long) nid)) {
				if (missingCount < sampleLimit) {
					if (sample.length() > 0) {
						sample.append(", ");
					}
					sample.append(nid).append(":").append(PrimitiveData.text(nid));
				}
				missingCount++;
			}
		}
		LOG.info("Reasoner concept set size={}, superConcepts map size={}, missing from map={}",
				conceptSet.size(), superConcepts.size(), missingCount);
		if (missingCount > 0) {
			LOG.info("Sample missing concepts: {}", sample);
		}
	}

	// Helper method to convert boxed collections to primitive
	private MutableLongObjectMap<MutableLongSet> convertToLongMap(HashMap<Long, Set<Long>> source) {
		MutableLongObjectMap<MutableLongSet> result = LongObjectMaps.mutable.withInitialCapacity(source.size());
		source.forEach((key, values) -> {
			MutableLongSet primitiveValues = LongSets.mutable.withInitialCapacity(values.size());
			values.forEach(primitiveValues::add);
			result.put(key, primitiveValues);
		});
		return result;
	}

	@Override
	public int getConceptCount() {
		return data.getActiveConceptCount();
	}

	@Override
	public ImmutableIntList getReasonerConceptSet() {
		return data.getReasonerConceptSet();
	}

	protected ImmutableIntSet toIntSet(MutableLongSet classes) {
		if (classes == null)
			return null;
		MutableIntSet parentNids = IntSets.mutable.withInitialCapacity(classes.size());
		for (long parent : classes.toArray()) {
			parentNids.add((int) parent);
		}
		return parentNids.toImmutable();
	}

	@Override
	public ImmutableIntSet getEquivalent(int id) {
		MutableLongSet eqs = reasoner.getEquivalentConcepts(id);
		return toIntSet(eqs);
	}

	@Override
	public ImmutableIntSet getParents(int id) {
		MutableLongSet supers = reasoner.getSuperConcepts(id);
		return toIntSet(supers);
	}

	@Override
	public ImmutableIntSet getChildren(int id) {
		MutableLongSet subs = reasoner.getSubConcepts(id);
		return toIntSet(subs);
	}

	@Override
	public LogicalExpression getNecessaryNormalForm(int id) {
		Definition def = nnfb.getNecessaryNormalForm(id);
		if (def == null)
			return null;
		OwlElToLogicalExpression leb = new OwlElToLogicalExpression(data.getIntervalRoleTypes());
		try {
			LogicalExpression nnf = leb.build(def);
			return nnf;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
