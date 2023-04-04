package org.hl7.tinkar.integration.snomed.description;

import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.integration.snomed.core.EntityService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSnomedToEntityDescriptionFiles extends TestSnomedToEntity {

    private static final Logger LOG = LoggerFactory.getLogger(TestSnomedToEntityDescriptionFiles.class);

    @BeforeAll
    public void setupSuite() throws IOException {
        entityService = new EntityService();
        LOG.info("Loading Description data");

    }

    @AfterAll
    public void teardownSuite() throws IOException{
        LOG.info("Done Loading Description data");
    }

    @Test
    @Order(1)
    public void testDescriptionFile4() throws IOException {
        System.out.println("Hello Komet");
        List<String> rows = loadSnomedFile("sct2_Description_Full-en_US1000124_20220901_4.txt");

        testAndCompareTransformation(rows);
    }

    @Test
    public void testDescriptionFile5() throws IOException {
        List<String> rows = loadSnomedFile("sct2_Description_Full-en_US1000124_20220901_5.txt");

        testAndCompareTransformation(rows);
    }

    @Test
    public void testDescriptionFile7() throws IOException {
        List<String> rows = loadSnomedFile("sct2_Description_Full-en_US1000124_20220901_7.txt");

        testAndCompareTransformation(rows);
    }

    private void testAndCompareTransformation(List<String> rows) {

        List<SemanticRecord> actualRecords = new ArrayList<>();
        for(String row: rows){
            actualRecords.add(createDescriptionSemantic(row));
        }

        Assertions.assertTrue(1 == 1, "Not true");
        Assertions.assertTrue(actualRecords.size() > 0, "No SemanticRecord list");


        //Loop through created SemanticRecords and compare to expected values
        for (int x = 0; x < actualRecords.size(); x++) {
            SemanticRecord actualRecord = actualRecords.get(x);
            String[] values = rows.get(x).split("\t");
            UUID expectedPatternUUID = UuidT5Generator.get("Description Pattern");//TODO: reference existing concept 'Description Pattern'
            UUID expectedSemanticUUID = UuidT5Generator.get(NAMESPACE, expectedPatternUUID.toString() + values[0]);
            UUID expectedReferencedComponentUUID = UuidT5Generator.get(NAMESPACE, values[4]);

            //Check SemanticRecord values against expected
            Assertions.assertEquals(expectedSemanticUUID.getMostSignificantBits(), actualRecord.mostSignificantBits(),"SemanticRecord most significant bits do not match expected");
            Assertions.assertEquals(expectedSemanticUUID.getLeastSignificantBits(), actualRecord.leastSignificantBits(), "SemanticRecord least significant bits do not match expected");
            Assertions.assertEquals(entityService.get().nidForUuids(expectedSemanticUUID), actualRecord.nid(), "SemanticRecord nid does not match expected");
            Assertions.assertEquals(entityService.get().nidForUuids(expectedPatternUUID), actualRecord.patternNid(), "SemanticRecord most patternNid does not match expected");
            Assertions.assertEquals(entityService.get().nidForUuids(expectedReferencedComponentUUID),actualRecord.referencedComponentNid(), "SemanticRecord referencedComponentNid does not match expected");
            Assertions.assertTrue(actualRecord.versions().size() > 0, "No SemanticRecordVersions");


            //Loop through VersionRecords and compare to expected values
            for (int i = 0; i < actualRecord.versions().size(); i++) {

                SemanticVersionRecord actualVersionRecord = actualRecord.versions().get(0);
                Assertions.assertEquals(actualVersionRecord.stampNid(), entityService.get().nidForUuids(UuidT5Generator.get(NAMESPACE, values[0] + values[1] + values[2] + values[3] + values[4] + values[5] + values[6] + values[7] + values[8])));

                Object[] expectedFields = {
                        new UUID(1l, 2l), //language
                        values[7], //term
                        UuidT5Generator.get(NAMESPACE, values[8]), //case significance id
                        UuidT5Generator.get(NAMESPACE, values[6])}; //type id

                Assertions.assertEquals(expectedFields[0], actualVersionRecord.fieldValues().get(0), "SemanticVersionRecord Language does not match expected");
                Assertions.assertEquals(expectedFields[1], actualVersionRecord.fieldValues().get(1), "SemanticVersionRecord term does not match expected");
                Assertions.assertEquals(expectedFields[2], actualVersionRecord.fieldValues().get(2), "SemanticVersionRecord case significance id does not match expected");
                Assertions.assertEquals(expectedFields[3], actualVersionRecord.fieldValues().get(3), "SemanticVersionRecord type id does not match expected");


            }
        }
    }

}