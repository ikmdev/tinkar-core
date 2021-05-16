package org.hl7.tinkar.terms;

import org.hl7.tinkar.common.util.text.EscapeUtil;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.Pattern;
import org.hl7.tinkar.component.Semantic;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

public class ProxyFactory {
    public static final String CONCEPT_ELEMENT = "concept";
    public static final String SEMANTIC_ELEMENT = "semantic";
    public static final String PATTERN_ELEMENT = "pattern";
    public static final String ENTITY_ELEMENT = "entity";
    public static final String UUIDS_ATTRIBUTE = "uuids";
    public static final String DESCRIPTION_ATTRIBUTE = "desc";

    private static ThreadLocal<DocumentBuilder> documentBuilder = ThreadLocal.withInitial(() -> {
        try {
            return DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    });

    static EntityFacade fromXmlFragment(String xmlString) {
        try {
            Document doc = documentBuilder.get().parse(new InputSource(new StringReader(xmlString)));
            Element element = doc.getDocumentElement();
            Attr uuids = element.getAttributeNode(UUIDS_ATTRIBUTE);
            Attr desc = element.getAttributeNode(DESCRIPTION_ATTRIBUTE);
            return switch (element.getTagName()) {
                case ENTITY_ELEMENT -> EntityProxy.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                case PATTERN_ELEMENT -> PatternProxy.make(desc.getName(), UuidUtil.fromString(uuids.getValue()));
                case SEMANTIC_ELEMENT -> SemanticProxy.make(desc.getName(), UuidUtil.fromString(uuids.getValue()));
                case CONCEPT_ELEMENT -> ConceptProxy.make(desc.getName(), UuidUtil.fromString(uuids.getValue()));
                default -> throw new IllegalStateException("Unexpected value: " + element.getTagName());
            };
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String toXmlFragment(EntityFacade facade) {
        StringBuilder sb = new StringBuilder("<");
        if (facade instanceof Concept) {
           sb.append(CONCEPT_ELEMENT).append(" " +
                   DESCRIPTION_ATTRIBUTE + "=\"");
       } else if (facade instanceof Semantic) {
            sb.append(SEMANTIC_ELEMENT).append(" " +
                    DESCRIPTION_ATTRIBUTE + "=\"");
       } else if (facade instanceof Pattern) {
            sb.append(PATTERN_ELEMENT).append(" " +
                    DESCRIPTION_ATTRIBUTE + "=\"");
       } else {
            sb.append(ENTITY_ELEMENT).append(" " +
                    DESCRIPTION_ATTRIBUTE + "=\"");
       }
        sb.append(EscapeUtil.forXML(facade.description())).append("\" " +
                UUIDS_ATTRIBUTE + "=\"");
        sb.append(UuidUtil.toString(facade.publicId()));
        sb.append("\"/>");
        return sb.toString();
    }
}
