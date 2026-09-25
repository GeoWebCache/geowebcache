package org.geowebcache.rest.filter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.MockConfigurationResourceProvider;
import org.geowebcache.config.MockGridSetConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.controller.FilterUpdateController;
import org.geowebcache.rest.controller.RestExceptionHandler;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class FilterUpdateControllerTest {

    private MockMvc mockMvc;

    TileLayerDispatcher tld;

    FilterUpdateController fc;

    @Before
    public void setup() throws GeoWebCacheException {
        setup("tiff");
    }

    private void setup(String fileExtension) throws GeoWebCacheException {

        BoundingBox extent = new BoundingBox(0, 0, 10E6, 10E6);
        boolean alignTopLeft = false;
        int levels = 10;
        Double metersPerUnit = 1.0;
        double pixelSize = 0.0028;
        int tileWidth = 256;
        int tileHeight = 256;
        boolean yCoordinateFirst = false;
        GridSet gridSet = GridSetFactory.createGridSet(
                "EPSG:3395",
                SRS.getSRS("EPSG:3395"),
                extent,
                alignTopLeft,
                levels,
                metersPerUnit,
                pixelSize,
                tileWidth,
                tileHeight,
                yCoordinateFirst);

        GridSetBroker gridSetBroker = new GridSetBroker(MockGridSetConfiguration.withDefaults(gridSet));

        XMLConfiguration xmlConfig = loadXMLConfig(fileExtension);
        xmlConfig.setGridSetBroker(gridSetBroker);
        xmlConfig.afterPropertiesSet();
        LinkedList<TileLayerConfiguration> configList = new LinkedList<>();
        configList.add(xmlConfig);

        tld = new TileLayerDispatcher(gridSetBroker, configList, null);
        fc = new FilterUpdateController();
        fc.setTileLayerDispatcher(tld);
        this.mockMvc = MockMvcBuilders.standaloneSetup(fc)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    /** Loads the 1.2.5 test config, with the given file raster filter extension. */
    private XMLConfiguration loadXMLConfig(String fileExtension) {

        XMLConfiguration xmlConfig = null;
        try (InputStream in = XMLConfiguration.class.getResourceAsStream(
                XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE)) {
            String config = new String(in.readAllBytes(), UTF_8)
                    .replace("<fileExtension>tiff<", "<fileExtension>" + fileExtension + "<");
            xmlConfig = new XMLConfiguration(
                    null,
                    new MockConfigurationResourceProvider(() -> new ByteArrayInputStream(config.getBytes(UTF_8))));
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }

    @Test
    public void testPost() throws Exception {

        String filterXml = "<wmsRasterFilterUpdate>\n"
                + "    <gridSetId>EPSG:4326</gridSetId>\n"
                + "    <zoomStart>0</zoomStart>\n"
                + "    <zoomStop>6</zoomStop>\n"
                + "</wmsRasterFilterUpdate>";

        this.mockMvc
                .perform(post("/rest/filter/testWMSRasterFilter/update/xml")
                        .contentType(MediaType.APPLICATION_ATOM_XML)
                        .contextPath("")
                        .content(filterXml))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    public void testZipUpdateUnsupportedExtension() throws Exception {
        setup("shp");
        postZip("testFileRasterFilter_EPSG:4326_0.shp")
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Unsupported raster file extension: shp")));
    }

    @Test
    public void testZipUpdateUnknownGridSet() throws Exception {
        // EPGS typo, the layer only has EPSG:4326 and EPSG:900913
        postZip("testFileRasterFilter_EPGS:4326_0.tiff")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Unknown grid set EPGS:4326")));
    }

    @Test
    public void testZipUpdateNotARaster() throws Exception {
        // the single byte entry is shorter than the TIFF magic bytes
        postZip("testFileRasterFilter_EPSG:4326_0.tiff")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Uploaded matrix is not a readable tiff image")));
    }

    @Test
    public void testZipUpdateInvalidName() throws Exception {
        // missing zoom level, other filter, not a number, int overflow
        for (String name : new String[] {
            "testFileRasterFilter_EPSG:4326.tiff",
            "otherFilter_EPSG:4326_0.tiff",
            "testFileRasterFilter_EPSG:4326_x.tiff",
            "testFileRasterFilter_EPSG:4326_9999999999.tiff"
        }) {
            postZip(name)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Invalid file name " + name)));
        }
    }

    @Test
    public void testParseNameUnderscoreGridSet() throws Exception {
        String[] parsed = new ZipFilterUpdate(null).parseName("my_filter_Grid_512_3.png", "my_filter");
        assertThat(parsed, arrayContaining("Grid_512", "3"));
    }

    /** Posts a zip update for the file raster filter, with a single entry. */
    private ResultActions postZip(String entryName) throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(zip)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(new byte[] {0});
            zipOutputStream.closeEntry();
        }
        return this.mockMvc.perform(post("/rest/filter/testFileRasterFilter/update/zip")
                .contextPath("")
                .content(zip.toByteArray()));
    }
}
