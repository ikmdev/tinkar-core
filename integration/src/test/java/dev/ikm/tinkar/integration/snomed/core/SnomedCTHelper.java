package dev.ikm.tinkar.integration.snomed.core;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

import static dev.ikm.tinkar.integration.snomed.core.MockEntity.clearCache;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTConstants.*;
import static dev.ikm.tinkar.integration.snomed.core.MockDataType.ENTITYREF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SnomedCTHelper {

    public static void openSession(Consumer<MockedStatic<EntityService>> mockedStaticEntity) {
        clearCache();
        try (MockedStatic<EntityService> mockStaticEntityService = Mockito.mockStatic(EntityService.class)) {
            EntityService entityService = mock(EntityService.class);
            mockStaticEntityService.when(EntityService::get).thenReturn(entityService);
            when(EntityService.get().nidForUuids(any(UUID.class))).thenAnswer((y) -> MockEntity.getNid(y.getArgument(0)));
            mockedStaticEntity.accept(mockStaticEntityService);
        }
    }

    /**
     * This method returns SnomedCTData class after it loads the file
     * @Param Class
     * @Param String
     * @Returns SnomedCTData
     * **/
        public static SnomedCTData loadSnomedFile(Class<?> aClass, String snomedCTDataFile) {
        SnomedCTData snomedCTData = new SnomedCTData();
        try(InputStream inputStream = aClass.getResourceAsStream(snomedCTDataFile);){
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String lineJustFetched;
            int lineCount = 0;
            while ((lineJustFetched = br.readLine()) != null) {
                if(lineCount != 0){
                    snomedCTData.setNamespaceUUID(SNOMED_CT_NAMESPACE_UUID);
                    snomedCTData.load(lineJustFetched);
                }else{
                    snomedCTData.loadHeaders(lineJustFetched);
                }
                lineCount++;
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return snomedCTData;
    }

    public static UUID getPatternTypeUUID(String patternType) {
        UUID patternTypeUUID = UuidT5Generator.get(patternType);
        MockEntity.populateMockData(patternTypeUUID.toString(), ENTITYREF);
        return patternTypeUUID;
    }

    public static UUID getSemanticUUID(UUID patternUUID, String id) {
        UUID semanticUUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE_UUID, String.valueOf(patternUUID) + id);
        MockEntity.populateMockData(semanticUUID.toString(), ENTITYREF);
        return semanticUUID;
    }
    public static UUID getReferenceComponentUUID(UUID patternTypeUUID, String id) {
        UUID referenceComponenetUUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE_UUID, String.valueOf(patternTypeUUID)+id);
        MockEntity.populateMockData(referenceComponenetUUID.toString(), ENTITYREF);
        return referenceComponenetUUID;
    }

}
