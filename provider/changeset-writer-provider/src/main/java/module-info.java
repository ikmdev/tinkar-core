import dev.ikm.tinkar.entity.ChangeSetWriterService;
import dev.ikm.tinkar.provider.changeset.ChangeSetWriterProvider;

module dev.ikm.tinkar.provider.changeset {
    requires dev.ikm.tinkar.entity;
    requires dev.ikm.tinkar.schema;
    requires dev.ikm.jpms.eclipse.collections;
    requires dev.ikm.jpms.eclipse.collections.api;

    requires org.slf4j;
    exports dev.ikm.tinkar.provider.changeset;

    provides ChangeSetWriterService with ChangeSetWriterProvider;
}