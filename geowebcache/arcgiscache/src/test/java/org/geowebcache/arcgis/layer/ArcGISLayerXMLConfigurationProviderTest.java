package org.geowebcache.arcgis.layer;

import com.thoughtworks.xstream.XStream;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;
import org.geowebcache.io.GeoWebCacheXStream;

public class ArcGISLayerXMLConfigurationProviderTest extends TestCase {

    @SuppressWarnings("rawtypes")
    public void testGetConfiguredXStream() {

        final Map<String, Class> aliases = new HashMap<String, Class>();
        XStream xs =
                new ArcGISLayerXMLConfigurationProvider()
                        .getConfiguredXStream(
                                new GeoWebCacheXStream() {
                                    @Override
                                    public void alias(String alias, Class c) {
                                        aliases.put(alias, c);
                                    }
                                });

        assertNotNull(xs);
        assertEquals(ArcGISCacheLayer.class, aliases.get("arcgisLayer"));
    }
}
