package org.geowebcache.georss;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.util.Date;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.geotools.feature.type.DateUtil;

class GeoRSSParsingUtils {

    public static Date date(final String dateTimeStr) {
        Date dateTime = DateUtil.deserializeDateTime(dateTimeStr);
        return dateTime;
    }

    /**
     * Being at a start element tag, returns its coalesced text value
     * 
     * @param reader
     * @return
     * @throws XMLStreamException
     */
    public static String text(XMLStreamReader reader) throws XMLStreamException {
        reader.require(START_ELEMENT, null, null);
        StringBuilder sb = new StringBuilder();

        while (true) {
            reader.next();
            if (reader.isCharacters() || reader.isWhiteSpace()) {
                sb.append(reader.getText());
            } else if (reader.isEndElement()) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Consumes the current element (given by tagName) until it's end element is fount (assuming
     * there's no nested element called the same)
     * 
     * @param reader
     * @param tagName
     * @throws XMLStreamException
     */
    public static void consume(XMLStreamReader reader, QName tagName) throws XMLStreamException {

        if (reader.getEventType() == END_ELEMENT && tagName.equals(reader.getName())) {
            return;// already consumed
        }

        while (reader.next() != END_DOCUMENT) {
            if (reader.isEndElement() && tagName.equals(reader.getName())) {
                return;
            }
        }
    }

    /**
     * Safely advances until the next tag element (either start or end element) and returns its
     * name, or {@code null} in case the end of document is reached before any tag
     * 
     * @param reader
     * @return
     * @throws XMLStreamException
     */
    public static QName nextTag(XMLStreamReader reader) throws XMLStreamException {

        while (reader.next() != END_DOCUMENT) {
            if (reader.isStartElement() || reader.isEndElement()) {
                return reader.getName();
            }
        }

        return null;
    }
}
