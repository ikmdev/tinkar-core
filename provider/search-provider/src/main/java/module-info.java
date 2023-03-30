module dev.ikm.tinkar.provider.search {
    requires org.slf4j;
    requires dev.ikm.tinkar.entity;
    requires org.eclipse.collections.api;
    requires org.apache.lucene;
    requires org.apache.lucene.queryparser;
    requires org.apache.lucene.search.highlight;
    
    exports dev.ikm.tinkar.provider.search;
}
