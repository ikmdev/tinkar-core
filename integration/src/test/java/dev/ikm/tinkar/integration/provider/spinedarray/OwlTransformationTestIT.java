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
package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.ext.lang.owl.Rf2OwlToLogicAxiomTransformer;
import dev.ikm.tinkar.ext.lang.owl.SctOwlUtilities;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OwlTransformationTestIT {
    public static final EntityProxy.Pattern AXIOM_SYNTAX_PATTERN = EntityProxy.Pattern.make("Axiom Syntax Pattern", UUID.fromString("c0ca180b-aae2-5fa1-9ab7-4a24f2dfe16b"));
    File dataStoreFile;
    String controllerName;
    @BeforeEach
    public void startup(){
      dataStoreFile = new File(System.getProperty("user.home") + "/Solor/snomedct-data");
        controllerName = "Open SpinedArrayStore";

        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, dataStoreFile);
        PrimitiveData.selectControllerByName(controllerName);
        PrimitiveData.start();

    }
    @AfterEach
    public  void breakdown(){
        PrimitiveData.stop();
    }

    @Test
    @Disabled
    public void owlParserTest(){
        Transaction owlTransformationTransaction = Transaction.make();

        try {
            new Rf2OwlToLogicAxiomTransformer(
                    owlTransformationTransaction,
                    AXIOM_SYNTAX_PATTERN,
                    TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN).call();
        } catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    @Test
    @Disabled
    public void testOwlExpression(){
        StringBuilder propertyBuilder = new StringBuilder();
        StringBuilder classBuilder = new StringBuilder();
        List<String> owlExpressionsToProcess = getStrings();

        for (String owlExpression : owlExpressionsToProcess) {
            if (owlExpression.toLowerCase().contains("property")) {
                propertyBuilder.append(" ").append(owlExpression);
                if (!owlExpression.toLowerCase().contains("objectpropertychain")) {
                    String tempExpression = owlExpression.toLowerCase().replace("subobjectpropertyof", " subclassof");
                    classBuilder.append(" ").append(tempExpression);
                }
            } else {
                classBuilder.append(" ").append(owlExpression);
            }

        }
        String owlClassExpressionsToProcess = classBuilder.toString();
        String owlPropertyExpressionsToProcess = propertyBuilder.toString();
        try {
            LogicalExpression expression = SctOwlUtilities.sctToLogicalExpression(
                    owlClassExpressionsToProcess,
                    owlPropertyExpressionsToProcess);
            assertNotNull(expression);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> getStrings() {
        String inputString1 =
            "EquivalentClasses(:126885006 ObjectIntersectionOf(:64572001 ObjectSomeValuesFrom(:609096000 ObjectIntersectionOf(ObjectSomeValuesFrom(:116676008 :108369006) ObjectSomeValuesFrom(:363698007 :89837001)))))";
        String inputString2 =
            "EquivalentClasses(:895602001 ObjectIntersectionOf(:763158003 ObjectSomeValuesFrom(:411116001 :385287007) " +
            "ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:127489000 :704226002)) DataHasValue(:1142139005 \"1\"^^xsd:integer)))";
        String inputString3 =
            "EquivalentClasses(:428684004 ObjectIntersectionOf(:763158003 ObjectSomeValuesFrom(:411116001 :447079001) " +
            "ObjectSomeValuesFrom(:609096000 ObjectIntersectionOf(ObjectSomeValuesFrom(:732943007 :386895008) ObjectSomeValuesFrom(:732945000 :258684004) ObjectSomeValuesFrom(:732947008 :732936001)" +
            "ObjectSomeValuesFrom(:762949000 :386895008) DataHasValue(:1142135004 \"12\"^^xsd:decimal) DataHasValue(:1142136003 \"1\"^^xsd:decimal)))" +
            "ObjectSomeValuesFrom(:609096000 ObjectIntersectionOf(ObjectSomeValuesFrom(:732943007 :386897000) ObjectSomeValuesFrom(:732945000 :258684004) ObjectSomeValuesFrom(:732947008 :732936001)" +
            "ObjectSomeValuesFrom(:762949000 :386897000) DataHasValue(:1142135004 \"60\"^^xsd:decimal) DataHasValue(:1142136003 \"1\"^^xsd:decimal)))" +
            "ObjectSomeValuesFrom(:609096000 ObjectIntersectionOf(ObjectSomeValuesFrom(:732943007 :386898005) ObjectSomeValuesFrom(:732945000 :258684004) ObjectSomeValuesFrom(:732947008 :732936001)" +
            "ObjectSomeValuesFrom(:762949000 :386898005) DataHasValue(:1142135004 \"100\"^^xsd:decimal) DataHasValue(:1142136003 \"1\"^^xsd:decimal))))";

        List<String> owlExpressionsToProcess = new ArrayList<>();
        owlExpressionsToProcess.add(inputString1);
        owlExpressionsToProcess.add(inputString2);
        owlExpressionsToProcess.add(inputString3);
        return owlExpressionsToProcess;
    }
}
