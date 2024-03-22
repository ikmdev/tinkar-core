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
package dev.ikm.tinkar.reasoner.elkowl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.owl.SnomedOwlOntology;

public class SolorTest {

	private static final Logger LOG = LoggerFactory.getLogger(SolorTest.class);

	@Test
	public void loadAndClassify() throws Exception {
		Path solor_axioms_file = Paths.get("src", "test", "resources", "solor", "solor-axioms.txt");
		assumeTrue(Files.exists(solor_axioms_file), "No file: " + solor_axioms_file);
		LOG.info("Create ontology");
		SnomedOwlOntology ontology = SnomedOwlOntology.createOntology();
		LOG.info("Read axioms");
		List<String> lines = Files.readAllLines(solor_axioms_file);
		LOG.info("Read axioms: " + lines.size());
		lines.add(0, "Prefix(:=<" + ElkOwlPrefixManager.PREFIX + ">) Ontology(");
		lines.add(")");
		ontology.loadOntology(lines);
		LOG.info("Axioms: " + ontology.getAxioms().size());
		LOG.info("Compute inferences");
		ontology.classify();
		LOG.info("Done");
		ontology.writeOntology(Paths.get("target", "solor.ofn"));
		assertEquals(364192, ontology.getAxioms().size());
	}

}
