package org.hl7.tinkar.provider.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class Indexer {
    public static final String NID_POINT = "nidPoint";
    public static final String NID = "nid";
    public static final String RC_NID = "rcNid";
    public static final String PATTERN_NID = "patternNid";
    private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);
    private static final File defaultDataDirectory = new File("target/lucene/");
    private static Directory indexDirectory;
    private static Analyzer analyzer;
    private static IndexWriter indexWriter;
    private final Path indexPath;

    public Indexer() throws IOException {
        Indexer.indexDirectory = new ByteBuffersDirectory();
        Indexer.analyzer = new StandardAnalyzer();
        Indexer.indexWriter = Indexer.getIndexWriter();
        this.indexPath = null;
    }

    private static IndexWriter getIndexWriter() throws IOException {
        //Create the indexer
        IndexWriterConfig config = new IndexWriterConfig(analyzer());
        IndexWriter indexWriter = new IndexWriter(indexDirectory(), config);
        return indexWriter;
    }

    public static Analyzer analyzer() {
        return analyzer;
    }

    public static Directory indexDirectory() {
        return indexDirectory;
    }

    public Indexer(Path indexPath) throws IOException {
        this.indexPath = indexPath;
        Indexer.indexDirectory = FSDirectory.open(this.indexPath);
        Indexer.analyzer = new StandardAnalyzer();
        this.indexWriter = Indexer.getIndexWriter();
    }

    public static DirectoryReader getDirectoryReader() throws IOException {
        return DirectoryReader.open(indexWriter);
    }

    public void commit() throws IOException {
        this.indexWriter.commit();
    }

    public void close() throws IOException {
        this.indexWriter.close();
    }

    public void index(Object object) {
        if (object instanceof SemanticEntity semanticEntity) {
            // TODO move field allocation to threadlocal if performance concern?
            IntPoint nidPoint = new IntPoint(NID_POINT, 0);
            StoredField nidField = new StoredField(NID, 0);
            StoredField rcNidField = new StoredField(RC_NID, 0);
            StoredField patternNidField = new StoredField(PATTERN_NID, 0);

            Document document = new Document();
            nidPoint.setIntValue(semanticEntity.nid());
            nidField.setIntValue(semanticEntity.nid());
            rcNidField.setIntValue(semanticEntity.referencedComponentNid());
            patternNidField.setIntValue(semanticEntity.patternNid());

            document.add(nidPoint);
            document.add(nidField);
            document.add(rcNidField);
            document.add(patternNidField);
            for (SemanticEntityVersion version : semanticEntity.versions()) {
                for (Object field : version.fields()) {
                    if (field instanceof String text) {
                        document.add(new TextField("text", text, Field.Store.NO));
                    }
                }
            }
            try {
                this.indexWriter.addDocument(document);
            } catch (IOException e) {
                LOG.error("Exception writing: " + object);
            }
        }

    }
}
