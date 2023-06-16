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
 * <p>Copyright 2021
 */
package org.geowebcache.mbtiles.layer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.ecc.vectortile.VectorTileDecoder;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.config.TestXMLConfigurationSource;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.XMLConfigurationProvider;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.EmptyTileException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.TileJSON;
import org.geowebcache.layer.meta.VectorLayerMetadata;
import org.geowebcache.mime.ApplicationMime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class MBTilesLayerTest {

    private static final double DELTA = 1E-6;

    private static final Set<String> TEST_FIELDS =
            new HashSet<>(
                    Arrays.asList(
                            "_zoom", "_row", "_col", "ID", "Costant", "Zeroval", "IntVal",
                            "Double1", "Double2", "Double3"));

    private static final String TEST_POINTS_FILENAME = "manypoints_test.mbtiles";

    public @Rule MockWepAppContextRule extensions = new MockWepAppContextRule();

    @Rule public ExpectedException exception = ExpectedException.none();

    protected TileLayerConfiguration config;

    @Before
    public void setUpTestUnit() throws Exception {
        config = getConfig();
    }

    @Rule public TemporaryFolder temp = new TemporaryFolder();
    protected File configDir;
    protected File configFile;

    protected TileLayerConfiguration getConfig() throws Exception {
        makeConfigFile();
        return getConfig(extensions);
    }

    private void makeConfigFile() throws Exception {
        if (configFile == null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");

            File lakesFile = temp.newFile("world_lakes.mbtiles");
            URL source = MBTilesLayerTest.class.getResource("world_lakes.mbtiles");
            Files.copy(
                    Paths.get(source.toURI()),
                    lakesFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);

            File pointsFile = temp.newFile(TEST_POINTS_FILENAME);
            source = MBTilesLayerTest.class.getResource(TEST_POINTS_FILENAME);
            Files.copy(
                    Paths.get(source.toURI()),
                    pointsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);

            source = MBTilesLayerTest.class.getResource("geowebcache.xml");
            try (Stream<String> lines = Files.lines(Paths.get(source.toURI()))) {
                List<String> replaced =
                        lines.map(
                                        line -> {
                                            return line.replace(
                                                            "world_lakes.mbtiles",
                                                            lakesFile.getAbsolutePath())
                                                    .replace(
                                                            TEST_POINTS_FILENAME,
                                                            pointsFile.getAbsolutePath())
                                                    .replace("\\", "\\\\");
                                        })
                                .collect(Collectors.toList());
                Files.write(configFile.toPath(), replaced);
            }
        }
    }

    TestXMLConfigurationSource configSource = new TestXMLConfigurationSource();

    private TileLayerConfiguration getConfig(MockWepAppContextRule extensions) throws Exception {
        extensions.addBean(
                "MBTilesLayerConfigProvider",
                new MBTilesLayerXMLConfigurationProvider(),
                XMLConfigurationProvider.class);
        return configSource.create(extensions, configDir);
    }

    @Test
    public void testLayerDefaultValues() {
        Optional<TileLayer> layer = config.getLayer("World_Lakes");
        TileLayer worldLakesLayer = layer.get();
        assertTrue(worldLakesLayer instanceof MBTilesLayer);
        MBTilesLayer mbTilesLayer = (MBTilesLayer) worldLakesLayer;
        assertEquals(256, mbTilesLayer.getTileSize());
    }

    @Test
    public void testLayerConfigValues() throws GeoWebCacheException, IOException {
        Optional<TileLayer> layer = config.getLayer("testName");
        TileLayer testLayer = layer.get();
        assertTrue(testLayer instanceof MBTilesLayer);
        MBTilesLayer mbTilesLayer = (MBTilesLayer) testLayer;
        assertEquals(256, mbTilesLayer.getTileSize());
        LayerMetaInformation metaInformation = mbTilesLayer.getMetaInformation();
        assertNotNull(metaInformation);
        assertEquals("TestDescription", metaInformation.getDescription());
        assertEquals("TestTitle", metaInformation.getTitle());
        Set<String> gridsubsets = mbTilesLayer.getGridSubsets();
        assertTrue(gridsubsets.contains("EPSG:900913"));
        GridSubset subset = mbTilesLayer.getGridSubset("EPSG:900913");
        assertEquals(6, subset.getZoomStop());
        assertEquals(0, subset.getZoomStart());
        assertEquals(ApplicationMime.mapboxVector, mbTilesLayer.getMimeTypes().get(0));

        ConveyorTile conveyorTile =
                new ConveyorTile(
                        null,
                        testLayer.getId(),
                        subset.getName(),
                        new long[] {4L, 4L, 3L},
                        ApplicationMime.mapboxVector,
                        null,
                        null,
                        null);

        conveyorTile = testLayer.getTile(conveyorTile);
        VectorTileDecoder decoder = new VectorTileDecoder();
        byte[] byteArray = null;
        try (InputStream inputStream = conveyorTile.getBlob().getInputStream()) {
            byteArray = ByteStreams.toByteArray(inputStream);
        }

        VectorTileDecoder.FeatureIterable decoded = decoder.decode(byteArray);
        List<VectorTileDecoder.Feature> features = decoded.asList();
        assertEquals(433, features.size());
        VectorTileDecoder.Feature feature = features.get(0);
        assertEquals("manypoints_test", feature.getLayerName());
        Map<String, Object> attribs = feature.getAttributes();
        Set<String> keys = attribs.keySet();
        Set<String> expectedKeys = new HashSet<>(TEST_FIELDS);
        expectedKeys.removeAll(keys);
        assertTrue(expectedKeys.isEmpty());
        assertEquals(3L, attribs.get("_zoom"));
        assertEquals(4L, attribs.get("_row"));
        assertEquals(4L, attribs.get("_col"));
        assertEquals(25180626L, attribs.get("ID"));
        assertEquals(37L, attribs.get("IntVal"));
        assertEquals(3.25f, (Float) attribs.get("Double3"), DELTA);
    }

    @Test
    public void testTileJson() {
        MBTilesLayer mbTilesLayer = (MBTilesLayer) config.getLayer("testName").get();
        assertTrue(mbTilesLayer.supportsTileJSON());
        TileJSON tileJson = mbTilesLayer.getTileJSON();
        assertEquals("testName", tileJson.getName());
        assertEquals(0, tileJson.getMinZoom().intValue());
        assertEquals(6, tileJson.getMaxZoom().intValue());
        assertArrayEquals(
                new double[] {38.221435, 38.856820, 41.495361, 40.763901},
                tileJson.getBounds(),
                DELTA);
        assertArrayEquals(new double[] {41.495361, 38.856820, 6}, tileJson.getCenter(), DELTA);

        List<VectorLayerMetadata> layers = tileJson.getLayers();
        assertEquals(1, layers.size());
        VectorLayerMetadata pointLayer = layers.get(0);
        assertEquals("manypoints_test", pointLayer.getId());
        assertEquals(0, pointLayer.getMinZoom().intValue());
        assertEquals(6, pointLayer.getMaxZoom().intValue());

        Map<String, String> fields = pointLayer.getFields();
        Set<String> keys = fields.keySet();

        for (String key : TEST_FIELDS) {
            assertEquals("Number", fields.get(key));
        }

        keys.removeAll(TEST_FIELDS);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void testOutsideRange() throws GeoWebCacheException, IOException {
        MBTilesLayer testLayer = (MBTilesLayer) config.getLayer("testName").get();
        ConveyorTile conveyorTile =
                new ConveyorTile(
                        null,
                        testLayer.getId(),
                        "EPSG:900913",
                        new long[] {38L, 42L, 6L},
                        ApplicationMime.mapboxVector,
                        null,
                        null,
                        null);
        exception.expect(OutsideCoverageException.class);
        exception.expectMessage(
                "Coverage [minx,miny,maxx,maxy] is [38, 39, 39, 39, 6], index [x,y,z] is [38, 42, 6]");
        testLayer.getTile(conveyorTile);
    }

    @Test
    public void testEmptyTile() throws GeoWebCacheException, IOException {
        try {
            MBTilesLayer testLayer = (MBTilesLayer) config.getLayer("testName").get();
            ConveyorTile conveyorTile =
                    new ConveyorTile(
                            null,
                            testLayer.getId(),
                            "EPSG:900913",
                            new long[] {38L, 39L, 6L},
                            ApplicationMime.mapboxVector,
                            null,
                            null,
                            null);
            testLayer.getTile(conveyorTile);
        } catch (EmptyTileException e) {
            assertEquals(ApplicationMime.mapboxVector, e.getMime());
        }
    }
}
