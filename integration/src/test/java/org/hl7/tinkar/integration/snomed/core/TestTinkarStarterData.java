package org.hl7.tinkar.integration.snomed.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.graph.EntityVertex;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestTinkarStarterData extends AbstractSnomedTest {

    private static final Logger LOG = LoggerFactory.getLogger(TestTinkarStarterData.class);
    private static JsonNode snomedStarterData;

    // TODO: populating EntityVertex for spined array from json text
    private static EntityVertex snomedEntity;
    @BeforeAll
    public void setupSuite() throws IOException {
        LOG.info("Loading Json data");
        snomedStarterData = loadJsonFile("snomedct-starter-data-9.json");
        System.out.println(snomedStarterData.toPrettyString());
    }

    @AfterAll
    public void teardownSuite() throws IOException{
        LOG.info("Loading Json data");
    }

    @Test
    @Order(1)
    public void testJsonData() {
        System.out.println("Hello Komet");
        LOG.info(snomedStarterData.get("namespace").textValue());
        LOG.info(UuidT5Generator.get(snomedStarterData.get("STAMP").get("author").asText()).toString());
        Assertions.assertTrue(1==1,"Not true");
    }

}