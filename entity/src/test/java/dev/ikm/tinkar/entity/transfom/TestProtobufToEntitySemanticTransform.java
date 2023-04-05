package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.component.Semantic;
import dev.ikm.tinkar.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntitySemanticTransform extends AbstractTestProtobufTransform {
    private Semantic mockSemantic;

    @BeforeAll
    public void init() {
        super.init();
        mockSemantic = mock(Semantic.class);
    }

    //TODO - Add unit tests for variations of Semantic Version transformation

    @Test
    @Disabled
    public void semanticChronologyTransformWithZeroVersion(){
//        // Given a PBSemanticChronology with a no Semantic Versions present
//        mockedEntityService.when(() -> Entity.nid(testConcept.publicId())).thenReturn(10);
//
//        PBSemanticChronology pbSemanticChronology = PBSemanticChronology.newBuilder()
//                .setPublicId()
//                .setReferencedComponent()
//                .setPatternForSemantic()
//                .addAllVersions()
//                .build();
//
//        // When we transform PBSemanticChronology
//
//        // Then we will throw a Runtime exception
//        assertThrows(Throwable.class, () -> ProtobufTransformer.transformSemanticChronology(pbSemanticChronology), "Not allowed to have no stamp versions.");
    }
    @Test
    @Disabled
    public void semanticChronologyTransformWithOneVersion(){
        try (MockedStatic<Entity> mockedEntityService = Mockito.mockStatic(Entity.class)) {

        }
    }

    @Test
    @Disabled
    public void semanticChronologyTransformWithTwoVersions(){
        try (MockedStatic<Entity> mockedEntityService = Mockito.mockStatic(Entity.class)) {

        }
    }
}
