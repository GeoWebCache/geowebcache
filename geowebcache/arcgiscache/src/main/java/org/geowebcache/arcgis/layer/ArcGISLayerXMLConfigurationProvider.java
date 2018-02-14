package org.geowebcache.arcgis.layer;

import org.geowebcache.config.Info;
import org.geowebcache.config.XMLConfigurationProvider;

import com.thoughtworks.xstream.XStream;

/**
 * Implementation of the {@link XMLConfigurationProvider} extension point to extend the
 * {@code geowebcache.xml} configuration file with {@code arcgisLayer} layers.
 * 
 * @author Gabriel Roldan
 * 
 */
public class ArcGISLayerXMLConfigurationProvider implements XMLConfigurationProvider {

    public XStream getConfiguredXStream(final XStream xs) {
        xs.alias("arcgisLayer", ArcGISCacheLayer.class);
        //xs.alias("compactCache", org.geowebcache.arcgis.compact.ArcGISCompactCache.class);
        
        return xs;
    }

    @Override
    public boolean canSave(Info i) {
        return i instanceof ArcGISCacheLayer;
    }

}
