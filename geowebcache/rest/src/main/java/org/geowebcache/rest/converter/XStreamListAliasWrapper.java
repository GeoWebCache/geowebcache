/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Torben Barsballe (Boundless), 2018
 */
package org.geowebcache.rest.converter;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.net.URI;
import java.util.Collection;
import org.geowebcache.rest.controller.GWCController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

/**
 * Utility class for construction list responses
 *
 * <p>Wraps a collection of strings representing object names, with additional metadata used to
 * construct a response
 */
public class XStreamListAliasWrapper {

    Collection<String> object;
    String alias;
    Class<? extends Collection> collectionClass;
    Class<? extends GWCController> controllerClass;

    /**
     * @param object Collection of names of GWC info objects
     * @param alias Alias for the XML list entry of each object
     * @param collectionClass Class of the collection (for aliasing)
     * @param controllerClass Class of the controller that contains the correct "${alias}Get" method
     *     that the list should link to.
     */
    public XStreamListAliasWrapper(
            Collection<String> object,
            String alias,
            Class<? extends Collection> collectionClass,
            Class<? extends GWCController> controllerClass) {
        this.object = object;
        this.alias = alias;
        this.collectionClass = collectionClass;
        this.controllerClass = controllerClass;
    }

    /**
     * Creates an XStream converter for the list object
     *
     * @return A new XStream converter for the list of names
     */
    public Converter createConverter() {
        return new Converter() {
            /**
             * @see com.thoughtworks.xstream.converters.ConverterMatcher#canConvert(java.lang.Class)
             */
            @Override
            public boolean canConvert(Class type) {
                return collectionClass.isAssignableFrom(type);
            }

            /**
             * @see com.thoughtworks.xstream.converters.Converter#marshal(java.lang.Object,
             *     com.thoughtworks.xstream.io.HierarchicalStreamWriter,
             *     com.thoughtworks.xstream.converters.MarshallingContext)
             */
            @Override
            public void marshal(
                    Object source, HierarchicalStreamWriter writer, MarshallingContext context) {

                @SuppressWarnings("unchecked")
                Collection<String> entries = (Collection<String>) source;

                for (String name : entries) {

                    writer.startNode(alias);

                    writer.startNode("name");
                    writer.setValue(name);
                    writer.endNode(); // name

                    writer.startNode("atom:link");
                    writer.addAttribute("xmlns:atom", "http://www.w3.org/2005/Atom");
                    writer.addAttribute("rel", "alternate");
                    UriComponents uriComponents =
                            MvcUriComponentsBuilder.fromMethodName(
                                            controllerClass, alias + "Get", name)
                                    .buildAndExpand("");
                    // build URI with URI.normalize() to remove double slashes
                    String normalizedLayerUri =
                            URI.create(uriComponents.encode().toUriString().replace("$", ""))
                                    .normalize()
                                    .toASCIIString();
                    writer.addAttribute("href", normalizedLayerUri + ".xml");
                    writer.addAttribute("type", MediaType.TEXT_XML.toString());

                    writer.endNode();

                    writer.endNode();
                }
            }

            /**
             * @see
             *     com.thoughtworks.xstream.converters.Converter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader,
             *     com.thoughtworks.xstream.converters.UnmarshallingContext)
             */
            @Override
            public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
