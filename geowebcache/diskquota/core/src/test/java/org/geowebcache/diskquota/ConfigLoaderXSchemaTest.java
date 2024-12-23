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
 * @author Kevin Smith, Boundless, Copyright 2015
 */
package org.geowebcache.diskquota;

import static org.junit.Assert.assertThrows;

import com.thoughtworks.xstream.XStream;
import org.geowebcache.io.GeoWebCacheXStream;
import org.junit.Ignore;
import org.junit.Test;

public class ConfigLoaderXSchemaTest {

    @Test
    public void testNotAllowNonGWCClass() throws Exception {
        // Check that classes from other packages on the class path can't be serialized
        XStream xs = ConfigLoader.getConfiguredXStream(new GeoWebCacheXStream());
        assertThrows(
                com.thoughtworks.xstream.security.ForbiddenClassException.class,
                () -> xs.fromXML("<" + org.easymock.Capture.class.getCanonicalName() + " />"));
    }

    @Ignore // Need to tighten the XStream permissions to get this to pass
    @Test
    public void testNotAllowNonXMLGWCClass() throws Exception {
        // Check that a class in GWC that shouldn't be serialized to XML can't be
        XStream xs = ConfigLoader.getConfiguredXStream(new GeoWebCacheXStream());
        assertThrows(
                com.thoughtworks.xstream.security.ForbiddenClassException.class,
                () -> xs.fromXML("<" + ConfigLoaderXSchemaTest.class.getCanonicalName() + " />"));
    }
}
