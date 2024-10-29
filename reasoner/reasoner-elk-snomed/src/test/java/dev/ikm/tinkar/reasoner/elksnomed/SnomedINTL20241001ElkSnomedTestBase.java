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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.tinkar.common.service.PrimitiveData;

public class SnomedINTL20241001ElkSnomedTestBase extends ElkSnomedDataBuilderTest {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(SnomedINTL20241001ElkSnomedTestBase.class);

	static {
		stated_count = 394202;
		active_count = 366841;
		inactive_count = 27361;
		test_case = "snomed-us-20241001";
	}

	public static final String db = "SnomedCT_INTL_20241001_SpinedArray-20241018";

	protected String getDir() {
		return "target/data/snomed-test-data-" + getEditionDir() + "-" + getVersion();
	}

	protected String getEdition() {
		return "INT";
	}

	protected String getEditionDir() {
		return "intl";
	}

	protected String getVersion() {
		return "20241001";
	}

	protected Path axioms_file = Paths.get(getDir(),
			"sct2_sRefset_OWLExpressionSnapshot_" + getEdition() + "_" + getVersion() + ".txt");

	protected Path rels_file = Paths.get(getDir(),
			"sct2_Relationship_Snapshot_" + getEdition() + "_" + getVersion() + ".txt");

	protected Path descriptions_file = Paths.get(getDir(),
			"sct2_Description_Snapshot-en_" + getEdition() + "_" + getVersion() + ".txt");

	@BeforeAll
	public static void startPrimitiveData() throws IOException {
		setupPrimitiveData(db);
		PrimitiveData.start();
	}

}
