module dev.ikm.tinkar.provider.search {
    requires org.slf4j;
    requires transitive dev.ikm.tinkar.entity;
    requires org.eclipse.collections.api;
    requires org.apache.lucene.queryparser;
    requires org.apache.lucene.queries;
    requires org.apache.lucene.highlighter;
    requires org.apache.lucene.core;
    requires static transitive com.google.auto.service;

    exports dev.ikm.tinkar.provider.search;
}
