package org.hl7.tinkar.terms;

import org.hl7.tinkar.common.alert.AlertStreams;
import org.hl7.tinkar.common.util.text.EscapeUtil;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;

public class ProxyFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyFactory.class);
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

    public static <T extends EntityProxy> Optional<T> fromXmlFragmentOptional(String xmlString) {
        if (xmlString.contains("<") && xmlString.contains(">")) {
            try {
                Document doc = documentBuilder.get().parse(new InputSource(new StringReader(xmlString)));
                Element element = doc.getDocumentElement();
                Attr uuids = element.getAttributeNode(UUIDS_ATTRIBUTE);
                Attr desc = element.getAttributeNode(DESCRIPTION_ATTRIBUTE);
                EntityProxy proxy = switch (element.getTagName()) {
                    case ENTITY_ELEMENT -> EntityProxy.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                    case PATTERN_ELEMENT -> EntityProxy.Pattern.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                    case SEMANTIC_ELEMENT -> EntityProxy.Semantic.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                    case CONCEPT_ELEMENT -> EntityProxy.Concept.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                    default -> {
                        IllegalStateException ex = new IllegalStateException("Unexpected value: " + element.getTagName());
                        AlertStreams.dispatchToRoot(ex);
                        yield null;
                    }
                };
                return (Optional<T>) Optional.ofNullable(proxy);
            } catch (SAXException | IOException e) {
                AlertStreams.dispatchToRoot(new Exception("Input string: " + xmlString, e));
            }
        }
        return Optional.empty();
    }

    public static <T extends EntityProxy> T fromFacade(EntityFacade facade) {
        if (facade instanceof EntityProxy proxy) {
            return (T) proxy;
        }
        if (facade instanceof org.hl7.tinkar.component.Concept) {
            return (T) EntityProxy.Concept.make(facade.nid());
        }
        if (facade instanceof org.hl7.tinkar.component.Pattern) {
            return (T) EntityProxy.Pattern.make(facade.nid());
        }
        if (facade instanceof org.hl7.tinkar.component.Semantic) {
            return (T) EntityProxy.Semantic.make(facade.nid());
        }
        throw new UnsupportedOperationException("Can't handle: " + facade);
    }

    public static <T extends EntityProxy> T fromXmlFragment(String xmlString) {
        try {
            Document doc = documentBuilder.get().parse(new InputSource(new StringReader(xmlString)));
            Element element = doc.getDocumentElement();
            Attr uuids = element.getAttributeNode(UUIDS_ATTRIBUTE);
            Attr desc = element.getAttributeNode(DESCRIPTION_ATTRIBUTE);
            return (T) switch (element.getTagName()) {
                case ENTITY_ELEMENT -> EntityProxy.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                case PATTERN_ELEMENT -> EntityProxy.Pattern.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                case SEMANTIC_ELEMENT -> EntityProxy.Semantic.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                case CONCEPT_ELEMENT -> EntityProxy.Concept.make(desc.getValue(), UuidUtil.fromString(uuids.getValue()));
                default -> throw new IllegalStateException("Unexpected value: " + element.getTagName());
            };
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String toXmlFragment(EntityFacade facade) {
        StringBuilder sb = new StringBuilder("<");
        if (facade instanceof org.hl7.tinkar.component.Concept) {
            sb.append(CONCEPT_ELEMENT).append(" " +
                    DESCRIPTION_ATTRIBUTE + "=\"");
        } else if (facade instanceof org.hl7.tinkar.component.Semantic) {
            sb.append(SEMANTIC_ELEMENT).append(" " +
                    DESCRIPTION_ATTRIBUTE + "=\"");
        } else if (facade instanceof org.hl7.tinkar.component.Pattern) {
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
