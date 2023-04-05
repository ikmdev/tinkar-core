package dev.ikm.tinkar.entity.transfom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityFieldTransform extends AbstractTestProtobufTransform {

    @BeforeAll
    public void init() {
        super.init();
    }
}
