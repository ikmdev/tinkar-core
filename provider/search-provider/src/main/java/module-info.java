module dev.ikm.tinkar.provider.search {
    requires org.slf4j;
    requires dev.ikm.tinkar.entity;
    requires org.eclipse.collections.api;
    requires org.apache.lucene;
    requires org.apache.lucene.queryparser;
    requires org.apache.lucene.search.highlight;
    requires static transitive com.google.auto.service;

    exports dev.ikm.tinkar.provider.search;
}
