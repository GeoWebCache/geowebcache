package org.geowebcache.arcgis.layer;

import com.thoughtworks.xstream.XStream;
import java.util.HashMap;
import java.util.Map;
import org.geowebcache.io.GeoWebCacheXStream;
import org.junit.Assert;
import org.junit.Test;

public class ArcGISLayerXMLConfigurationProviderTest {

    @Test
    public void testGetConfiguredXStream() {

        final Map<String, Class> aliases = new HashMap<>();
        XStream xs = new ArcGISLayerXMLConfigurationProvider().getConfiguredXStream(new GeoWebCacheXStream() {
            @Override
            public void alias(String alias, Class c) {
                aliases.put(alias, c);
            }
        });

        Assert.assertNotNull(xs);
        Assert.assertEquals(ArcGISCacheLayer.class, aliases.get("arcgisLayer"));
    }
}
