package org.hl7.tinkar.provider.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class Indexer extends EntityProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

    private static final File defaultDataDirectory = new File("target/lucene/");

    private final Path indexPath;
    private final IndexWriter indexWriter;

    public Indexer() throws IOException {
        this.indexPath = defaultDataDirectory.toPath();
        this.indexWriter = Indexer.getIndexWriter(this.indexPath);
    }

    public static IndexWriter getIndexWriter(Path path) throws IOException {
        Directory indexDirectory = FSDirectory.open(path);
        //Create the indexer
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter indexWriter = new IndexWriter(indexDirectory, config);
        return indexWriter;
    }

    public void commit() throws IOException {
        this.indexWriter.commit();
    }
    
    public void close() throws IOException {
        this.indexWriter.close();
    }

    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        Entity entity = EntityFactory.make(bytes);
        if (entity instanceof SemanticEntity semanticEntity) {
            Document document = null;
            for (SemanticEntityVersion version : semanticEntity.versions()) {
                for (Object field : version.fields()) {
                    if (field instanceof String text) {
                        if (document == null) {
                            document = new Document();
                        }
                        document.add(new TextField("text", text, Field.Store.NO));
                    }
                }
            }
            if (document != null) {
                try {
                    this.indexWriter.addDocument(document);
                } catch (IOException e) {
                    LOG.error("Exception writing: " + entity);
                }
            }
        }

    }
}
