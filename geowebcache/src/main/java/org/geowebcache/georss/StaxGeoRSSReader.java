/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.georss;

import java.io.IOException;
import java.io.Reader;

import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

class StaxGeoRSSReader implements GeoRSSReader {

    private static final String ATOM_NSURI = "http://www.w3.org/2005/Atom";

    private static final String GEORSS_NSURI = "http://www.georss.org/georss";

    private static final String GML_NSURI = "http://www.opengis.net/gml";

    private final XMLStreamReader reader;

    public StaxGeoRSSReader(final Reader feed) throws XMLStreamException, FactoryConfigurationError {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        reader = factory.createXMLStreamReader(feed);

        int tag = reader.nextTag();
        QName name = reader.getName();

        if (!(ATOM_NSURI.equals(name.getNamespaceURI()) || "feed".equals(name.getLocalPart()))) {
            throw new IllegalArgumentException("Document is not a GeoRSS feed. Root element: "
                    + name);
        }
    }

    public Entry nextEntry() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

}
