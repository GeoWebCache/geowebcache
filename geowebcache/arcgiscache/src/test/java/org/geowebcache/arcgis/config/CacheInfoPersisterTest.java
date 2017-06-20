package org.geowebcache.arcgis.config;

import com.thoughtworks.xstream.XStream;
import junit.framework.TestCase;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;

public class CacheInfoPersisterTest extends TestCase {

    public void testLoadSpatialReference() {
        String spatialRef = "<SpatialReference xsi:type='typens:ProjectedCoordinateSystem'>" //
                + "  <WKT>PROJCS[&quot;NZGD_2000_New_Zealand_Transverse_Mercator&quot;,GEOGCS[&quot;GCS_NZGD_2000&quot;,DATUM[&quot;D_NZGD_2000&quot;,SPHEROID[&quot;GRS_1980&quot;,6378137.0,298.257222101]],PRIMEM[&quot;Greenwich&quot;,0.0],UNIT[&quot;Degree&quot;,0.0174532925199433]],PROJECTION[&quot;Transverse_Mercator&quot;],PARAMETER[&quot;False_Easting&quot;,1600000.0],PARAMETER[&quot;False_Northing&quot;,10000000.0],PARAMETER[&quot;Central_Meridian&quot;,173.0],PARAMETER[&quot;Scale_Factor&quot;,0.9996],PARAMETER[&quot;Latitude_Of_Origin&quot;,0.0],UNIT[&quot;Meter&quot;,1.0],AUTHORITY[&quot;EPSG&quot;,2193]]</WKT>" //
                + "  <XOrigin>-4020900</XOrigin>" //
                + "  <YOrigin>1900</YOrigin>" //
                + "  <XYScale>450445547.3910538</XYScale>" //
                + "  <ZOrigin>0.5</ZOrigin>" //
                + "  <ZScale>1</ZScale>" //
                + "  <MOrigin>-100000</MOrigin>" //
                + "  <MScale>10000</MScale>" //
                + "  <XYTolerance>0.0037383177570093459</XYTolerance>" //
                + "  <ZTolerance>2</ZTolerance>" //
                + "  <MTolerance>2</MTolerance>" //
                + "  <HighPrecision>true</HighPrecision>" //
                + "  <WKID>2193</WKID>" //
                + "  <LatestWKID>2193</LatestWKID>" //
                + "</SpatialReference>";

        CacheInfoPersister persister = new CacheInfoPersister();
        XStream xs = persister.getConfiguredXStream();
        SpatialReference sr = (SpatialReference) xs.fromXML(new StringReader(spatialRef));

        assertNotNull(sr);
        String wkt = "PROJCS[\"NZGD_2000_New_Zealand_Transverse_Mercator\",GEOGCS[\"GCS_NZGD_2000\",DATUM[\"D_NZGD_2000\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",1600000.0],PARAMETER[\"False_Northing\",10000000.0],PARAMETER[\"Central_Meridian\",173.0],PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0],AUTHORITY[\"EPSG\",2193]]";
        assertEquals(wkt, sr.getWKT());
        assertEquals(-4020900, sr.getXOrigin(), 1e-6);
        assertEquals(1900, sr.getYOrigin(), 1e-6);
        assertEquals(450445547.3910538, sr.getXYScale(), 1e-6);
        assertEquals(0.5, sr.getZOrigin(), 1e-6);
        assertEquals(1, sr.getZScale(), 1e-6);
        assertEquals(10000, sr.getMScale(), 1e-6);
        assertEquals(0.0037383177570093459, sr.getXYTolerance(), 1e-6);
        assertEquals(2, sr.getZTolerance(), 1e-6);
        assertEquals(2, sr.getMTolerance(), 1e-6);
        assertEquals(true, sr.isHighPrecision());
        assertEquals(2193, sr.getWKID());
        assertEquals(2193, sr.getLatestWKID());
    }

    public void testLoadTileOrigin() {
        String tileOrigin = "<TileOrigin xsi:type='typens:PointN'>" //
                + "  <X>-4020900</X>" //
                + "  <Y>19998100</Y>" //
                + "</TileOrigin>";
        CacheInfoPersister persister = new CacheInfoPersister();
        XStream xs = persister.getConfiguredXStream();
        TileOrigin to = (TileOrigin) xs.fromXML(new StringReader(tileOrigin));
        assertNotNull(to);
        assertEquals(-4020900, to.getX(), 1e-6);
        assertEquals(19998100, to.getY(), 1e-6);
    }

    public void testLoadTileCacheInfo() {
        String tileCacheInfo = "<TileCacheInfo xsi:type='typens:TileCacheInfo'>" //
                + "  <SpatialReference xsi:type='typens:ProjectedCoordinateSystem'>" //
                + "  </SpatialReference>" //
                + "  <TileOrigin xsi:type='typens:PointN'>" //
                + "  <X>-4020900</X>" //
                + "  <Y>19998100</Y>" //
                + "  </TileOrigin>" //
                + "  <TileCols>512</TileCols>" //
                + "  <TileRows>512</TileRows>" //
                + "  <DPI>96</DPI>" //
                + "  <PreciseDPI>96</PreciseDPI>" //
                + "  <LODInfos xsi:type='typens:ArrayOfLODInfo'>" //
                + "    <LODInfo xsi:type='typens:LODInfo'>" //
                + "    </LODInfo>" //
                + "  </LODInfos>" //
                + "</TileCacheInfo>";

        CacheInfoPersister persister = new CacheInfoPersister();
        XStream xs = persister.getConfiguredXStream();
        TileCacheInfo tci = (TileCacheInfo) xs.fromXML(new StringReader(tileCacheInfo));
        assertNotNull(tci);
        assertNotNull(tci.getSpatialReference());
        assertNotNull(tci.getTileOrigin());
        assertEquals(512, tci.getTileCols());
        assertEquals(512, tci.getTileRows());
        assertEquals(96, tci.getDPI());
        assertEquals(96, tci.getPreciseDPI());
        assertNotNull(tci.getLodInfos());
        assertEquals(1, tci.getLodInfos().size());
    }

    public void testLoadLODInfo() {
        String lodInfo = "<LODInfo xsi:type='typens:LODInfo'>" //
                + "  <LevelID>10</LevelID>" //
                + "  <Scale>6000000</Scale>" //
                + "  <Resolution>1587.5031750063501</Resolution>" //
                + "</LODInfo>";

        CacheInfoPersister persister = new CacheInfoPersister();
        XStream xs = persister.getConfiguredXStream();
        LODInfo li = (LODInfo) xs.fromXML(new StringReader(lodInfo));
        assertNotNull(li);
        assertEquals(10, li.getLevelID());
        assertEquals(6000000, li.getScale(), 1e-6);
        assertEquals(1587.5031750063501, li.getResolution(), 1e-6);
    }

    public void testLoadTileImageInfo() {
        String tileImageInfo = "<TileImageInfo xsi:type='typens:TileImageInfo'>"//
                + "  <CacheTileFormat>JPEG</CacheTileFormat>"//
                + "  <CompressionQuality>80</CompressionQuality>"//
                + "  <Antialiasing>true</Antialiasing>"//
            + "  <BandCount>1</BandCount>"//
            + "  <LERCError>0</LERCError>"//
                + "</TileImageInfo>";

        CacheInfoPersister persister = new CacheInfoPersister();
        XStream xs = persister.getConfiguredXStream();
        TileImageInfo tii = (TileImageInfo) xs.fromXML(new StringReader(tileImageInfo));
        assertNotNull(tii);
        assertEquals("JPEG", tii.getCacheTileFormat());
        assertEquals(80f, tii.getCompressionQuality(), 1e-6f);
        assertTrue(tii.isAntialiasing());
        assertEquals(1, tii.getBandCount());
        assertEquals(0.0f, tii.getLERCError());
    }

    public void testLoadCacheStorageInfo() {
        String cacheStorageInfo = "<CacheStorageInfo xsi:type='typens:CacheStorageInfo'>"//
                + "  <StorageFormat>esriMapCacheStorageModeExploded</StorageFormat>"//
                + "  <PacketSize>10</PacketSize>"//
                + "</CacheStorageInfo>";

        CacheInfoPersister persister = new CacheInfoPersister();
        XStream xs = persister.getConfiguredXStream();
        CacheStorageInfo csi = (CacheStorageInfo) xs.fromXML(new StringReader(cacheStorageInfo));
        assertNotNull(csi);
        assertEquals(CacheStorageInfo.EXPLODED_FORMAT_CODE, csi.getStorageFormat());
        assertEquals(10, csi.getPacketSize());
    }

    public void testLoadEnvelopeN() {
        String envelopeN = "<EnvelopeN xsi:type='typens:EnvelopeN' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:xs='http://www.w3.org/2001/XMLSchema'"//
                + "xmlns:typens='http://www.esri.com/schemas/ArcGIS/10.0'>"//
                + "<XMin>700000</XMin>"//
                + "<YMin>-450000</YMin>"//
                + "<XMax>1588888.8888888895</XMax>"//
                + "<YMax>440000</YMax>"//
                + "</EnvelopeN>";
        CacheInfoPersister persister = new CacheInfoPersister();
        XStream xs = persister.getConfiguredXStream();
        EnvelopeN en = (EnvelopeN) xs.fromXML(new StringReader(envelopeN));
        assertNotNull(en);
        assertEquals(700000, en.getXmin(), 1e-6);
        assertEquals(-450000, en.getYmin(), 1e-6);
        assertEquals(1588888.8888888895, en.getXmax(), 1e-6);
        assertEquals(440000, en.getYmax(), 1e-6);
    }

    public void testLoadCacheInfo_ArcGIS_92() throws Exception {
        URL url = getClass().getResource("/arcgis_09.2_conf.xml");
        CacheInfo ci = load(url);
        assertNotNull(ci);
        assertNotNull(ci.getTileCacheInfo());
        assertNotNull(ci.getTileCacheInfo().getSpatialReference());
        assertNotNull(ci.getTileCacheInfo().getTileOrigin());
        assertNotNull(ci.getTileCacheInfo().getLodInfos());

        assertEquals(18, ci.getTileCacheInfo().getLodInfos().size());

        assertNotNull(ci.getTileImageInfo());
        assertNotNull(ci.getCacheStorageInfo());
    }

    public void testLoadCacheInfo_ArcGIS_93() throws Exception {
        URL url = getClass().getResource("/arcgis_09.3_conf.xml");
        CacheInfo ci = load(url);
        assertNotNull(ci);
        assertNotNull(ci.getTileCacheInfo());
        assertNotNull(ci.getTileCacheInfo().getSpatialReference());
        assertNotNull(ci.getTileCacheInfo().getTileOrigin());
        assertNotNull(ci.getTileCacheInfo().getLodInfos());

        assertEquals(11, ci.getTileCacheInfo().getLodInfos().size());

        assertNotNull(ci.getTileImageInfo());
        assertNotNull(ci.getCacheStorageInfo());
    }

    public void testLoadCacheInfo_ArcGIS_10() throws Exception {
        URL url = getClass().getResource("/arcgis_10.0_conf.xml");
        CacheInfo ci = load(url);
        assertNotNull(ci);
        assertNotNull(ci.getTileCacheInfo());
        assertNotNull(ci.getTileCacheInfo().getSpatialReference());
        assertNotNull(ci.getTileCacheInfo().getTileOrigin());
        assertNotNull(ci.getTileCacheInfo().getLodInfos());

        assertEquals(15, ci.getTileCacheInfo().getLodInfos().size());

        assertNotNull(ci.getTileImageInfo());
        assertNotNull(ci.getCacheStorageInfo());
    }

    private CacheInfo load(final URL url) throws Exception {
        CacheInfoPersister persister = new CacheInfoPersister();
        InputStream stream = url.openStream();
        Reader reader = new InputStreamReader(stream);
        CacheInfo cacheInfo;
        try {
            cacheInfo = persister.load(reader);
        } finally {
            stream.close();
        }
        return cacheInfo;
    }

}
