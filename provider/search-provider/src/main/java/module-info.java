module dev.ikm.tinkar.provider.search {
    requires org.slf4j;
    requires transitive dev.ikm.tinkar.entity;
    requires org.eclipse.collections.api;
    requires dev.ikm.jpms.lucene.luceneuber;
    requires static transitive com.google.auto.service;

    exports dev.ikm.tinkar.provider.search;
}
