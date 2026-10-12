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
package dev.ikm.tinkar.importer.rxnorm;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One class of the RxNorm OWL file and what its block says, read the way IKM's pipeline reads it:
 * annotations by pattern, and the first {@code EquivalentClasses} or {@code SubClassOf} axiom by
 * balanced parentheses.
 */
final class RxnormClass {

    static final String RXNORM_IRI = "http://mor.nlm.nih.gov/RXNORM/";
    static final String SNOMED_IRI = "http://snomed.info/id/";

    private static final Pattern IRI = Pattern.compile("<([^>]+)>");
    private static final Pattern HTTP_IRI = Pattern.compile("<(http://[^>]+)>");
    private static final Pattern DATA_HAS_VALUE = Pattern.compile(
            "DataHasValue\\(:[\\[](.*?)[\\]]\\s+\"([^\"]*)\"\\^\\^xsd:([^\\)]+)\\)|DataHasValue\\(<(http://[^>]+)>\\s+\"([^\"]*)\"\\^\\^xsd:([^\\)]+)\\)");

    final String id;
    String rdfsLabel = "";
    String rxnormName = "";
    String rxnormSynonym = "";
    String prescribableSynonym = "";
    String snomedCtId = "";
    String rxCui = "";
    String vuid = "";
    final Map<String, String> ndcEndDates = new LinkedHashMap<>();
    String qualitativeDistinction = "";
    String quantity = "";
    String schedule = "";
    String humanDrug = "";
    String vetDrug = "";
    final List<String> tallmanSynonyms = new ArrayList<>();
    String equivalentClasses = "";
    String subClassOf = "";

    private RxnormClass(String iri) {
        this.id = iri.startsWith(RXNORM_IRI) ? iri.substring(RXNORM_IRI.length()) : iri.substring(SNOMED_IRI.length());
    }

    /**
     * Every class in an RxNorm OWL file, in file order.
     *
     * @param owl the file's text
     * @return the classes
     */
    static List<RxnormClass> read(String owl) {
        List<RxnormClass> classes = new ArrayList<>();
        String[] blocks = owl.split("# Class: ");
        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            Matcher iri = IRI.matcher(block);
            if (iri.find() && (iri.group(1).startsWith(RXNORM_IRI) || iri.group(1).startsWith(SNOMED_IRI))) {
                RxnormClass rxClass = new RxnormClass(iri.group(1));
                rxClass.annotations(block);
                rxClass.equivalentClasses = balanced(block, "EquivalentClasses(");
                rxClass.subClassOf = balanced(block, "SubClassOf(");
                classes.add(rxClass);
            }
        }
        return classes;
    }

    /** Only a label: a class RxNorm names but says nothing else of. */
    boolean labelOnly() {
        return !rdfsLabel.isEmpty() && rxnormName.isEmpty() && rxnormSynonym.isEmpty() && prescribableSynonym.isEmpty()
                && snomedCtId.isEmpty() && rxCui.isEmpty() && vuid.isEmpty() && ndcEndDates.isEmpty();
    }

    private void annotations(String block) {
        rxnormName = first(block, ":RxNorm_Name <[^>]+> \"([^\"]*)\"");
        rxnormSynonym = first(block, ":RxNorm_Synonym <[^>]+> \"([^\"]*)\"");
        prescribableSynonym = first(block, ":Prescribable_Synonym <[^>]+> \"([^\"]*)\"");
        snomedCtId = first(block, "oboInOwl:hasDbXref <[^>]+> \"SNOMEDCT:\\s*([^\"]*)\"");
        rxCui = first(block, "oboInOwl:hasDbXref <[^>]+> \"RxCUI:\\s*([^\"]*)\"");
        vuid = first(block, "oboInOwl:hasDbXref <[^>]+> \"VUID:\\s*([^\"]*)\"");
        Matcher ndc = Pattern.compile("AnnotationAssertion\\(Annotation\\(:endDate \"(\\d+)\"\\) Annotation\\(:startDate \"\\d+\"\\) :ndc <[^>]+> \"([^\"]*)\"\\)").matcher(block);
        while (ndc.find()) {
            ndcEndDates.put(ndc.group(2), ndc.group(1));
        }
        qualitativeDistinction = first(block, ":QUALITATIVE_DISTINCTION <[^>]+> \"([^\"]*)\"");
        quantity = first(block, ":QUANTITY <[^>]+> \"([^\"]*)\"");
        schedule = first(block, ":SCHEDULE <[^>]+> \"([^\"]*)\"");
        humanDrug = first(block, ":HUMAN_DRUG <[^>]+> \"([^\"]*)\"");
        vetDrug = first(block, ":VET_DRUG <[^>]+> \"([^\"]*)\"");
        Matcher tallman = Pattern.compile(":Tallman_Synonym <[^>]+> \"([^\"]*)\"").matcher(block);
        while (tallman.find()) {
            tallmanSynonyms.add(tallman.group(1));
        }
        rdfsLabel = first(block, "rdfs:label <[^>]+> \"([^\"]*)\"");
    }

    private static String first(String block, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(block);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String balanced(String block, String opening) {
        int start = block.indexOf(opening);
        if (start == -1) {
            return "";
        }
        int depth = 1;
        int end = start + opening.length();
        while (depth > 0 && end < block.length()) {
            char c = block.charAt(end++);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        return depth == 0 ? block.substring(start, end) : "";
    }

    /**
     * The UUID of the concept an RxNorm or SNOMED CT IRI's local name stands for in an axiom: an
     * RxNorm class, or a SNOMED CT form ({@code -FS}), is RxNorm's own concept; any other SNOMED CT
     * id is the SNOMED CT concept.
     */
    static UUID axiomConcept(UUID namespace, String iri) {
        if (iri.startsWith(SNOMED_IRI)) {
            String id = iri.substring(SNOMED_IRI.length());
            return UuidT5Generator.get(namespace, id.endsWith("-FS") ? id + "rxnorm" : id);
        }
        return UuidT5Generator.get(namespace, iri.substring(RXNORM_IRI.length()) + "rxnorm");
    }

    /**
     * An axiom with its RxNorm and SNOMED CT IRIs turned into {@code :[uuid]}, the form the
     * OWL-to-logic transform reads; other IRIs (XSD, OWL) are kept.
     *
     * @param namespace the namespace identities are minted in
     * @param axiom     the axiom as the file has it
     * @return the axiom with public ids
     */
    static String withPublicIds(UUID namespace, String axiom) {
        Matcher iris = HTTP_IRI.matcher(axiom);
        StringBuilder result = new StringBuilder();
        while (iris.find()) {
            String iri = iris.group(1);
            String replacement = iri.startsWith(SNOMED_IRI) || iri.startsWith(RXNORM_IRI)
                    ? ":[" + axiomConcept(namespace, iri) + "]"
                    : "<" + iri + ">";
            iris.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        iris.appendTail(result);
        Matcher values = DATA_HAS_VALUE.matcher(result.toString());
        StringBuilder normalized = new StringBuilder();
        while (values.find()) {
            String replacement;
            if (values.group(1) != null) {
                replacement = "DataHasValue(:[" + values.group(1) + "] \"" + values.group(2) + "\"^^xsd:" + values.group(3) + ")";
            } else if (values.group(4).startsWith(SNOMED_IRI)) {
                replacement = "DataHasValue(:[" + axiomConcept(namespace, values.group(4)) + "] \"" + values.group(5)
                        + "\"^^xsd:" + values.group(6) + ")";
            } else {
                replacement = values.group();
            }
            values.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
        }
        values.appendTail(normalized);
        return normalized.toString();
    }
}
