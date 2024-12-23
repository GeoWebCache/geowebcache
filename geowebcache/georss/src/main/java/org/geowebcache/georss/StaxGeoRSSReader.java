/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.georss;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.geowebcache.georss.GML31ParsingUtils.GML.GML_NS_URI;
import static org.geowebcache.georss.GeoRSSParsingUtils.consume;
import static org.geowebcache.georss.GeoRSSParsingUtils.nextTag;
import static org.geowebcache.georss.GeoRSSParsingUtils.text;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.geotools.util.logging.Logging;
import org.geowebcache.georss.GML31ParsingUtils.GML;
import org.locationtech.jts.geom.Geometry;

class StaxGeoRSSReader implements GeoRSSReader {

    private static final Logger LOGGER = Logging.getLogger(StaxGeoRSSReader.class.getName());

    private static final class ATOM {
        public static final String NSURI = "http://www.w3.org/2005/Atom";

        public static final QName feed = new QName(NSURI, "feed");

        public static final QName title = new QName(NSURI, "title");

        public static final QName subtitle = new QName(NSURI, "subtitle");

        public static final QName updated = new QName(NSURI, "updated");

        public static final QName link = new QName(NSURI, "link");

        public static final QName summary = new QName(NSURI, "summary");

        public static final QName author = new QName(NSURI, "author");

        public static final QName name = new QName(NSURI, "name");

        public static final QName email = new QName(NSURI, "email");

        public static final QName id = new QName(NSURI, "id");

        public static final QName entry = new QName(NSURI, "entry");
    }

    private static final class GEORSS {
        public static final String GEORSS_NSURI = "http://www.georss.org/georss";

        public static final QName where = new QName(GEORSS_NSURI, "where");
    }

    private XMLStreamReader reader;

    private final GML31ParsingUtils gmlParser;

    public StaxGeoRSSReader(final Reader feed) throws XMLStreamException, FactoryConfigurationError {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        reader = factory.createXMLStreamReader(feed);

        reader.nextTag();
        reader.require(START_ELEMENT, null, null);
        QName name = reader.getName();

        if (!(ATOM.NSURI.equals(name.getNamespaceURI()) || "feed".equals(name.getLocalPart()))) {
            throw new IllegalArgumentException("Document is not a GeoRSS feed. Root element: " + name);
        }
        findFirstEntry();
        gmlParser = new GML31ParsingUtils();
    }

    private void findFirstEntry() throws XMLStreamException {
        int event;
        while ((event = reader.next()) != END_DOCUMENT) {
            if (event == START_ELEMENT && ATOM.entry.equals(reader.getName())) {
                break;
            }
        }
        if (event == END_DOCUMENT) {
            reader.close();
            reader = null;
        }
    }

    /** @see org.geowebcache.georss.GeoRSSReader#nextEntry() */
    @Override
    public Entry nextEntry() throws IOException {
        if (reader == null) {
            // reached EOF
            return null;
        }
        try {
            return parseEntry();
        } catch (XMLStreamException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }

    private Entry parseEntry() throws XMLStreamException {
        reader.require(START_ELEMENT, ATOM.NSURI, ATOM.entry.getLocalPart());

        Entry entry = new Entry();

        LOGGER.finer("Parsing GeoRSS entry...");

        while (true) {
            reader.next();
            if (reader.isStartElement()) {
                QName name = reader.getName();
                parseEntryMember(reader, name, entry);
            } else if (reader.isEndElement()) {
                if (ATOM.entry.equals(reader.getName())) {
                    break;
                }
            }
        }

        LOGGER.finer("Done parsing GeoRSS entry.");

        reader.require(END_ELEMENT, ATOM.NSURI, ATOM.entry.getLocalPart());

        QName nextTag;
        while ((nextTag = nextTag(reader)) != null) {
            // position parser ready for next entry
            if (reader.isStartElement() && nextTag.equals(ATOM.entry)) {
                break;
            }
        }

        if (END_DOCUMENT == reader.getEventType()) {
            reader.close();
            reader = null;
        }

        return entry;
    }

    private void parseEntryMember(final XMLStreamReader reader, final QName memberName, final Entry entry)
            throws XMLStreamException {

        reader.require(START_ELEMENT, memberName.getNamespaceURI(), memberName.getLocalPart());
        if (ATOM.id.equals(memberName)) {

            String id = text(reader);
            entry.setId(id);
        } else if (ATOM.link.equals(memberName)) {

            String uri = text(reader);
            URI link;
            try {
                link = new URI(uri);
                entry.setLink(link);
            } catch (URISyntaxException e) {
                LOGGER.info("Feed contains illegal 'link' element content:" + uri);
            }
        } else if (ATOM.title.equals(memberName)) {

            String title = text(reader);
            entry.setTitle(title);
        } else if (ATOM.subtitle.equals(memberName)) {

            String subtitle = text(reader);
            entry.setSubtitle(subtitle);
        } else if (ATOM.updated.equals(memberName)) {

            String upd = text(reader);
            if (upd != null && upd.length() > 0) {
                entry.setUpdated(upd);
            }
        } else if (GEORSS.where.equals(memberName)) {

            QName nextTag = nextTag(reader);
            if (reader.isStartElement() && GML_NS_URI.equals(nextTag.getNamespaceURI())) {
                Geometry where = geometry(reader);
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("Got geometry from feed: " + where);
                }
                entry.setWhere(where);
            }
        }

        consume(reader, memberName);

        reader.require(END_ELEMENT, memberName.getNamespaceURI(), memberName.getLocalPart());
    }

    private Geometry geometry(final XMLStreamReader reader) throws XMLStreamException {
        reader.require(START_ELEMENT, GML.GML_NS_URI, null);
        QName name = reader.getName();
        Geometry geometry = gmlParser.parseGeometry(reader);
        reader.require(END_ELEMENT, name.getNamespaceURI(), name.getLocalPart());
        return geometry;
    }
}
