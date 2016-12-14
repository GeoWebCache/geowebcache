package org.geowebcache.arcgis.config;

import java.io.Reader;
import java.util.ArrayList;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.io.GeoWebCacheXStream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;

/**
 * Loads {@link CacheInfo} objects from ArcGIS Server tile cache's {@code conf.xml} files.
 * 
 * @author Gabriel Roldan
 * 
 */
public class CacheInfoPersister {

    public CacheInfo load(final Reader reader) {
        XStream xs = getConfiguredXStream();
        CacheInfo ci = (CacheInfo) xs.fromXML(reader);
        return ci;
    }

    XStream getConfiguredXStream() {
        XStream xs = new GeoWebCacheXStream();
        
        // Allow anything that's part of GWC
        // TODO: replace this with a more narrow whitelist
        xs.allowTypesByWildcard(new String[]{"org.geowebcache.**"});
        
        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("SpatialReference", SpatialReference.class);
        xs.alias("TileOrigin", TileOrigin.class);

        xs.alias("TileCacheInfo", TileCacheInfo.class);
        xs.aliasField("SpatialReference", TileCacheInfo.class, "spatialReference");
        xs.aliasField("TileOrigin", TileCacheInfo.class, "tileOrigin");
        xs.aliasField("TileCols", TileCacheInfo.class, "tileCols");
        xs.aliasField("TileRows", TileCacheInfo.class, "tileRows");
        xs.aliasField("LODInfos", TileCacheInfo.class, "lodInfos");
        xs.alias("LODInfos", new ArrayList<LODInfo>().getClass());

        xs.alias("LODInfo", LODInfo.class);
        xs.aliasField("LevelID", LODInfo.class, "levelID");
        xs.aliasField("Scale", LODInfo.class, "scale");
        xs.aliasField("Resolution", LODInfo.class, "resolution");

        xs.alias("TileImageInfo", TileImageInfo.class);
        xs.aliasField("CacheTileFormat", TileImageInfo.class, "cacheTileFormat");
        xs.aliasField("CompressionQuality", TileImageInfo.class, "compressionQuality");
        xs.aliasField("Antialiasing", TileImageInfo.class, "antialiasing");

        xs.alias("CacheStorageInfo", CacheStorageInfo.class);
        xs.aliasField("StorageFormat", CacheStorageInfo.class, "storageFormat");
        xs.aliasField("PacketSize", CacheStorageInfo.class, "packetSize");

        xs.alias("CacheInfo", CacheInfo.class);
        xs.aliasField("TileCacheInfo", CacheInfo.class, "tileCacheInfo");
        xs.aliasField("TileImageInfo", CacheInfo.class, "tileImageInfo");
        xs.aliasField("CacheStorageInfo", CacheInfo.class, "cacheStorageInfo");

        xs.alias("EnvelopeN", EnvelopeN.class);
        xs.aliasField("XMin", EnvelopeN.class, "xmin");
        xs.aliasField("YMin", EnvelopeN.class, "ymin");
        xs.aliasField("XMax", EnvelopeN.class, "xmax");
        xs.aliasField("YMax", EnvelopeN.class, "ymax");
        xs.aliasField("SpatialReference", EnvelopeN.class, "spatialReference");

        return xs;
    }

    public BoundingBox parseLayerBounds(final Reader layerBoundsFile) {

        EnvelopeN envN = (EnvelopeN) getConfiguredXStream().fromXML(layerBoundsFile);

        BoundingBox bbox = new BoundingBox(envN.getXmin(), envN.getYmin(), envN.getXmax(),
                envN.getYmax());

        return bbox;
    }
}
