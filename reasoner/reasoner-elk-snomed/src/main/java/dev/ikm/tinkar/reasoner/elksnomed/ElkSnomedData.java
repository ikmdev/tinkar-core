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

import java.util.stream.LongStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.ConcreteRoleType;
import dev.ikm.elk.snomed.model.RoleType;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElkSnomedData {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedData.class);

	public static long getNid(long sctid) {
		UUID uuid = UuidUtil.fromSNOMED("" + sctid);
		long nid = PrimitiveData.nid(uuid);
		return nid;
	}

	public static OptionalLong tryGetNid(long sctid) {
		UUID uuid = UuidUtil.fromSNOMED("" + sctid);
		try {
			return OptionalLong.of(PrimitiveData.nid(uuid));
		} catch (IllegalStateException e) {
			LOG.warn("No entity for SNOMED SCTID {} (uuid {})", sctid, uuid);
			return OptionalLong.empty();
		}
	}

	private final ConcurrentHashMap<Long, Concept> nidConceptMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<Long, RoleType> nidRoleTypeMap = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<Long, ConcreteRoleType> nidConcreteRoleTypeMap = new ConcurrentHashMap<>();

	private final AtomicInteger activeConceptCount = new AtomicInteger();

	private final AtomicInteger inactiveConceptCount = new AtomicInteger();

	private ImmutableLongList reasonerConceptSet;

	private final Set<ConcreteRoleType> intervalRoleTypes = ConcurrentHashMap.newKeySet();

	public Collection<Concept> getConcepts() {
		return Collections.unmodifiableCollection(nidConceptMap.values());
	}

	public Collection<RoleType> getRoleTypes() {
		return Collections.unmodifiableCollection(nidRoleTypeMap.values());
	}

	public Collection<ConcreteRoleType> getConcreteRoleTypes() {
		return Collections.unmodifiableCollection(nidConcreteRoleTypeMap.values());
	}

	public Concept getConcept(long conceptNid) {
		return nidConceptMap.get(conceptNid);
	}

	public Concept getOrCreateConcept(long conceptNid) {
		return nidConceptMap.computeIfAbsent(conceptNid, Concept::new);
	}

	public Concept deleteConcept(long conceptNid) {
		return nidConceptMap.remove(conceptNid);
	}

	public RoleType getRoleType(long roleNid) {
		return nidRoleTypeMap.get(roleNid);
	}

	public RoleType getOrCreateRoleType(long roleNid) {
		return nidRoleTypeMap.computeIfAbsent(roleNid, RoleType::new);
	}

	public RoleType deleteRoleType(long roleNid) {
		return nidRoleTypeMap.remove(roleNid);
	}

	public ConcreteRoleType getConcreteRoleType(long roleNid) {
		return nidConcreteRoleTypeMap.get(roleNid);
	}

	public ConcreteRoleType getOrCreateConcreteRoleType(long roleNid) {
		return nidConcreteRoleTypeMap.computeIfAbsent(roleNid, ConcreteRoleType::new);
	}

	public ConcreteRoleType deleteConcreteRoleType(long roleNid) {
		return nidConcreteRoleTypeMap.remove(roleNid);
	}

	public int getActiveConceptCount() {
		return activeConceptCount.get();
	}

	public int incrementActiveConceptCount() {
		return activeConceptCount.incrementAndGet();
	}

	public int getInactiveConceptCount() {
		return inactiveConceptCount.get();
	}

	public int incrementInactiveConceptCount() {
		return inactiveConceptCount.incrementAndGet();
	}

	public ImmutableLongList getReasonerConceptSet() {
		return reasonerConceptSet;
	}

	public Set<ConcreteRoleType> getIntervalRoleTypes() {
		return intervalRoleTypes;
	}

	public boolean addIntervalRoleType(ConcreteRoleType roleType) {
		return intervalRoleTypes.add(roleType);
	}

	public void initializeReasonerConceptSet() {
		LongStream conceptNids = nidConceptMap.entrySet().stream().mapToLong(es -> es.getKey()).sorted();
		this.reasonerConceptSet = LongLists.immutable.ofAll(conceptNids);
		// Reset this to handle incremental updates
		this.activeConceptCount.set(reasonerConceptSet.size());
	}

	public void writeConcepts(Path path) throws Exception {
		Files.write(path, nidConceptMap.keySet().stream() //
				.map(key -> PrimitiveData.publicId(key).idString() + "\t" + PrimitiveData.text(key)) //
				.sorted() //
				.toList());
	}

	public void writeRoleTypes(Path path) throws Exception {
		Files.write(path, nidRoleTypeMap.keySet().stream() //
				.map(key -> PrimitiveData.publicId(key).idString() + "\t" + PrimitiveData.text(key)) //
				.sorted() //
				.toList());
	}

}
