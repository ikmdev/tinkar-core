package org.hl7.tinkar.lombok.dto.json;

import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UuidTest {
    @Test
    public void namespaceGetTest() {
        Assertions.assertEquals(UUID.fromString("c76757b0-94c8-5127-8ff1-cafe08580a6a"), UuidT5Generator.get(UuidT5Generator.PATH_ID_FROM_FS_DESC, "a test"));
        Assertions.assertEquals(UUID.fromString("59333431-37a9-55a9-adf1-1046b697be19"), UuidT5Generator.get(UuidT5Generator.REL_GROUP_NAMESPACE, "a test"));
        Assertions.assertEquals(UUID.fromString("b43556c0-b2c4-5d46-8ec2-a1921d852a4d"), UuidT5Generator.get(UuidT5Generator.USER_FULLNAME_NAMESPACE, "a test"));
        Assertions.assertEquals(UUID.fromString("f0cac0d0-4387-54f4-9cfe-71b792889948"), UuidT5Generator.get(UuidT5Generator.TAXONOMY_COORDINATE_NAMESPACE, "a test"));
        Assertions.assertEquals(UUID.fromString("4ce95266-ab53-557e-a96c-6bb07623c765"), UuidT5Generator.get(UuidT5Generator.REL_ADAPTOR_NAMESPACE, "a test"));
        Assertions.assertEquals(UUID.fromString("ad814516-05b5-5ab5-afba-0e36238675d3"), UuidT5Generator.get(UuidT5Generator.AUTHOR_TIME_ID, "a test"));
        Assertions.assertEquals(UUID.fromString("37771a6f-0909-5957-b6db-62583bd7824b"), UuidT5Generator.get(UuidT5Generator.SINGLE_SEMANTIC_FOR_RC_UUID, "a test"));

        UUID testUuid = UUID.randomUUID();
        Assertions.assertEquals(UuidUtil.getUuidFromRawBytes(UuidUtil.getRawBytes(testUuid)), testUuid);

        Assertions.assertThrows(NumberFormatException.class, () -> UuidUtil.getUuidFromRawBytes(new byte[0]));
    }

    @Test
    public void encodingTest() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> UuidT5Generator.getDigest("bogus"));
        Assertions.assertThrows(UnsupportedOperationException.class, () ->
                UuidT5Generator.getUuidWithEncoding(UuidT5Generator.PATH_ID_FROM_FS_DESC, "bogus", "bogus"));
    }

    @Test
    public void nullNameSpaceTest() {
        UUID bogusUuid = UuidT5Generator.get("bogus");
        Assertions.assertEquals(UUID.fromString("40ce4379-f576-5c05-b71c-88f9a371809f"), bogusUuid);

    }
    @Test
    public void getWithConsumerTest() {
        final Map<String, UUID> resultMap = new HashMap<>();
        final String a_test = "a test";
        UuidT5Generator.get(UuidT5Generator.PATH_ID_FROM_FS_DESC, a_test, (s, uuid) -> resultMap.put(s, uuid));
        Assertions.assertEquals(UUID.fromString("c76757b0-94c8-5127-8ff1-cafe08580a6a"), resultMap.get(a_test));
        Assertions.assertEquals(UUID.fromString("c76757b0-94c8-5127-8ff1-cafe08580a6a"),
                UuidT5Generator.get(UuidT5Generator.PATH_ID_FROM_FS_DESC, a_test, null));

    }

    @Test
    public void singleSemanticUuidTest() {
        // Test that the results are order independent.
        UUID[] assemblageIds = new UUID[] { UUID.fromString("c76757b0-94c8-5127-8ff1-cafe08580a6a"), UUID.fromString("59333431-37a9-55a9-adf1-1046b697be19")};
        UUID[] referencedComponentIds = new UUID[] { UUID.fromString("f0cac0d0-4387-54f4-9cfe-71b792889948"), UUID.fromString("37771a6f-0909-5957-b6db-62583bd7824b")};
        UUID result1 = UuidT5Generator.singleSemanticUuid(assemblageIds, referencedComponentIds);
        UUID[] assemblageIds2 = new UUID[] { UUID.fromString("59333431-37a9-55a9-adf1-1046b697be19"), UUID.fromString("c76757b0-94c8-5127-8ff1-cafe08580a6a")};
        UUID[] referencedComponentIds2 = new UUID[] { UUID.fromString("37771a6f-0909-5957-b6db-62583bd7824b"), UUID.fromString("f0cac0d0-4387-54f4-9cfe-71b792889948")};
        UUID result2 = UuidT5Generator.singleSemanticUuid(assemblageIds2, referencedComponentIds2);
        Assertions.assertEquals(result1, result2);
    }

    @Test
    public void uuidTester() {
        Assertions.assertTrue(UuidUtil.isUUID("c76757b0-94c8-5127-8ff1-cafe08580a6a"));
        Assertions.assertFalse(UuidUtil.isUUID(null));
        Assertions.assertFalse(UuidUtil.isUUID("bogus"));
        Assertions.assertFalse(UuidUtil.isUUID("c76757b0X94c8X5127X8ff1Xcafe08580a6a"));

        UUID[] assemblageIds = new UUID[] { UUID.fromString("c76757b0-94c8-5127-8ff1-cafe08580a6a"), UUID.fromString("59333431-37a9-55a9-adf1-1046b697be19")};
        UUID[] assemblageIds2 = new UUID[] { UUID.fromString("59333431-37a9-55a9-adf1-1046b697be19"), UUID.fromString("c76757b0-94c8-5127-8ff1-cafe08580a6a")};
        Assertions.assertEquals(UuidUtil.fromList(assemblageIds), UuidUtil.fromList(assemblageIds2));
    }


}
