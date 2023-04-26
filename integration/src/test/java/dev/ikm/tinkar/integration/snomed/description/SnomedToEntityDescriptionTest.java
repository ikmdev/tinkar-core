package dev.ikm.tinkar.integration.snomed.description;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.integration.snomed.core.MockEntity;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SnomedToEntityDescriptionTest extends SnomedToEntityDescription
{

    @Test
    @DisplayName("Creating a Stamp Chronology with Active State")
    public void testCreateStampChronologyWithActiveState(){
        //Given a row of snomed data
        openSession((mockStaticEntityService, starterData) -> {
            UUID namespaceUuid = SNOMED_CT_NAMESPACE;
            UUID stampUUID = UuidT5Generator.get(namespaceUuid, "101013200201311900000000000207008126813005en900000000000013009Neoplasm of anterior aspect of epiglottis900000000000020002");

            MockEntity.populateMockData(stampUUID.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);

            StampRecord expectedRecord = StampRecordBuilder.builder()
                    .leastSignificantBits(stampUUID.getLeastSignificantBits())
                    .mostSignificantBits(stampUUID.getMostSignificantBits())
                    .nid(MockEntity.getNid(stampUUID))
                    .versions(RecordListBuilder.make().build())
                    .build();

            StampVersionRecord expectedVersionRecord = StampVersionRecordBuilder.builder()
                    .stateNid(MockEntity.getNid(ACTIVE))
                    .chronology(expectedRecord)
                    .time(LocalDate.parse("20020131", DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12,0,0).toInstant(ZoneOffset.UTC).toEpochMilli())
                    .authorNid(MockEntity.getNid(SNOMED_CT_AUTHOR))
                    .moduleNid(MockEntity.getNid(SNOMED_TEXT_MODULE_ID))
                    .pathNid(MockEntity.getNid(DEVELOPMENT_PATH))
                    .build();

            expectedRecord = expectedRecord.withVersions(RecordListBuilder.make().newWith(expectedVersionRecord));

            List<String> rows = loadSnomedFile(this.getClass(),"sct2_Description_Full-en_US1000124_20220901_1.txt");
            Assertions.assertEquals(1, rows.size(),"Read file should only have one row");
            String testValues = rows.get(0);
            
            //When creating Stamp Chronology
            StampRecord actualRecord = createSTAMPChronology(testValues);

            //Then the created Stamp Chronology should match expected values
            Assertions.assertEquals(expectedRecord.leastSignificantBits(), actualRecord.leastSignificantBits(), "StampRecord leastSignificantBits do not match expected");
            Assertions.assertEquals(expectedRecord.mostSignificantBits(), actualRecord.mostSignificantBits(), "StampRecord mostSignificantBits do not match expected");
            Assertions.assertEquals(expectedRecord.nid(), actualRecord.nid(), "StampRecord nid does not match expected");
            Assertions.assertEquals(expectedRecord.versions(), actualRecord.versions(), "StampRecord versions do not match expected");
        });
    }

    @Test
    @DisplayName("Creating a Stamp Chronology with Inactive State")
    public void testCreateStampChronologyWithInactiveState(){
        //Given a row of snomed data
        openSession((mockStaticEntityService, starterData) -> {
            UUID namespaceUuid = SNOMED_CT_NAMESPACE;
            UUID stampUUID = UuidT5Generator.get(namespaceUuid, "157016200707310900000000000207008126869001en900000000000013009Neoplasm of the mesentery900000000000020002");

            MockEntity.populateMockData(stampUUID.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);

            StampRecord expectedRecord = StampRecordBuilder.builder()
                    .leastSignificantBits(stampUUID.getLeastSignificantBits())
                    .mostSignificantBits(stampUUID.getMostSignificantBits())
                    .nid(MockEntity.getNid(stampUUID))
                    .versions(RecordListBuilder.make().build())
                    .build();

            StampVersionRecord expectedVersionRecord = StampVersionRecordBuilder.builder()
                    .stateNid(MockEntity.getNid(INACTIVE))
                    .chronology(expectedRecord)
                    .time(LocalDate.parse("20070731", DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12,0,0).toInstant(ZoneOffset.UTC).toEpochMilli())
                    .authorNid(MockEntity.getNid(SNOMED_CT_AUTHOR))
                    .moduleNid(MockEntity.getNid(SNOMED_TEXT_MODULE_ID))
                    .pathNid(MockEntity.getNid(DEVELOPMENT_PATH))
                    .build();

            expectedRecord = expectedRecord.withVersions(RecordListBuilder.make().newWith(expectedVersionRecord));

            List<String> rows = loadSnomedFile(this.getClass(),"sct2_Description_Full-en_US1000124_20220901_2.txt");
            Assertions.assertEquals(1, rows.size(),"Read file should only have one row");
            String testValues = rows.get(0);

            //When creating Stamp Chronology
            StampRecord actualRecord = createSTAMPChronology(testValues);

            //Then the created Stamp Chronology should match expected values
            Assertions.assertEquals(expectedRecord.leastSignificantBits(),actualRecord.leastSignificantBits(),"StampRecord leastSignificantBits do not match expected");
            Assertions.assertEquals(expectedRecord.mostSignificantBits(),actualRecord.mostSignificantBits(), "StampRecord mostSignificantBits do not match expected");
            Assertions.assertEquals(expectedRecord.nid(),actualRecord.nid(),"StampRecord nid does not match expected");
            Assertions.assertEquals(expectedRecord.versions(),actualRecord.versions(), "StampRecord versions do not match expected");
        });

    }

    @Test
    @DisplayName("Building StampRecord without Most Significant Bits throws IllegalStateException")
    public void testExceptionOnEmptyMostSignificantBits(){
        //Given a StampRecord without most significant bits
        StampRecordBuilder expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .nid(24)
                .versions(RecordListBuilder.make().build());
        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedRecord::build, "Expected IllegalStateException when Most Significant Bits is Zero");

    }
    @Test
    @DisplayName("Building StampRecord without Least Significant Bits throws IllegalStateException")
    public void testExceptionOnEmptyLeastSignificantBits(){
        //Given a StampRecord without least significant bits
        StampRecordBuilder expectedRecord = StampRecordBuilder.builder()
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .nid(24)
                .versions(RecordListBuilder.make().build());
        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedRecord::build, "Expected IllegalStateException when Least Significant Bits is Zero");

    }
    @Test
    @DisplayName("Building StampRecord without Nid throws IllegalStateException")
    public void testExceptionOnEmptyNid(){
        //Given a StampRecord without nid
        StampRecordBuilder expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .versions(RecordListBuilder.make().build());
        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedRecord::build, "Expected IllegalStateException when NID is Zero");
    }

    @Test
    @DisplayName("Building StampRecord without Version list throws NullPointerException")
    public void testExceptionOnNullVersionsList(){
        //Given a StampRecord with null version list
        StampRecordBuilder expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .nid(24);
        //When we build the record
        //Then we expect an exception
        assertThrows(NullPointerException.class, expectedRecord::build, "Expected NullPointerException when NID is Zero");
    }

    @Test
    public void testExceptionOnEmptyStateNid() {
        //Given a StampRecord with StampVersionRecord without nid
        StampRecord expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .nid(24)
                .versions(RecordListBuilder.make().build())
                .build();

        StampVersionRecordBuilder expectedVersionRecord = StampVersionRecordBuilder.builder()
                .chronology(expectedRecord)
                .time(LocalDate.parse("20020131", DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
                .authorNid(6)
                .moduleNid(23)
                .pathNid(5);

        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedVersionRecord::build, "Expected IllegalStateException when State Nid is Zero");
    }

    @Test
    public void testExceptionOnEmptyTime(){
        //Given a StampRecord with StampVersionRecord without time
        StampRecord expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .nid(24)
                .versions(RecordListBuilder.make().build())
                .build();

        StampVersionRecordBuilder expectedVersionRecord = StampVersionRecordBuilder.builder()
                .stateNid(2)
                .chronology(expectedRecord)
                .authorNid(6)
                .moduleNid(23)
                .pathNid(5);

        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedVersionRecord::build, "Expected IllegalStateException when Time is Zero");

    }
    @Test
    public void testExceptionOnEmptyAuthorNid(){
        StampRecord expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .nid(24)
                .versions(RecordListBuilder.make().build())
                .build();

        StampVersionRecordBuilder expectedVersionRecord = StampVersionRecordBuilder.builder()
                .stateNid(2)
                .chronology(expectedRecord)
                .time(LocalDate.parse("20020131", DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
                .moduleNid(23)
                .pathNid(5);

        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedVersionRecord::build, "Expected IllegalStateException when Author Nid is Zero");

    }
    @Test
    public void testExceptionOnEmptyModuleNid(){
        //Given a StampRecord with StampVersionRecord without module nid
        StampRecord expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .nid(24)
                .versions(RecordListBuilder.make().build())
                .build();

        StampVersionRecordBuilder expectedVersionRecord = StampVersionRecordBuilder.builder()
                .stateNid(2)
                .chronology(expectedRecord)
                .time(LocalDate.parse("20020131", DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
                .authorNid(6)
                .pathNid(5);

        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedVersionRecord::build, "Expected IllegalStateException when Module Nid is Zero");

    }
    @Test
    public void testExceptionOnEmptyPathNid(){
        //Given a StampRecord with StampVersionRecord without path nid
        StampRecord expectedRecord = StampRecordBuilder.builder()
                .leastSignificantBits(SNOMED_CT_NAMESPACE.getLeastSignificantBits())
                .mostSignificantBits(SNOMED_CT_NAMESPACE.getMostSignificantBits())
                .nid(24)
                .versions(RecordListBuilder.make().build())
                .build();

        StampVersionRecordBuilder expectedVersionRecord = StampVersionRecordBuilder.builder()
                .stateNid(2)
                .chronology(expectedRecord)
                .time(LocalDate.parse("20020131", DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
                .authorNid(6)
                .moduleNid(23);

        //When we build the record
        //Then we expect an exception
        assertThrows(IllegalStateException.class, expectedVersionRecord::build, "Expected IllegalStateException when Path Nid is Zero");

    }

}
