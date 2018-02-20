/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.junit.Test;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.geowebcache.sqlite.Utils.Tuple.tuple;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public final class MbtilesBlobStoreTest extends TestSupport {

    @Test
    public void testTilePutGetDeleteOperations() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // create the tile that will be stored
        TileObject putTile = TileObject.createCompleteTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null, stringToResource("IMAGE-10-50-5"));
        // storing the tile
        store.put(putTile);
        // create a query tile to get the previous stored tile
        TileObject getTile = TileObject.createQueryTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null);
        // checking if the tile was retrieved
        assertThat(store.get(getTile), is(true));
        // checking if the blob data was updated
        assertThat(getTile.getBlob(), notNullValue());
        assertThat(resourceToString(getTile.getBlob()), is("IMAGE-10-50-5"));
        // sanity check of create time
        assertThat(getTile.getCreated(), greaterThan(System.currentTimeMillis() - 60000));
        // delete and check if the tile was deleted
        assertThat(store.delete(putTile), is(true));
        // query the deleted tile
        getTile = TileObject.createQueryTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null);
        // check that the tile was deleted
        assertThat(store.get(getTile), is(false));
        assertThat(getTile.getBlob(), nullValue());
    }

    @Test
    public void testTileMetadataOperations() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // let's store some metadata
        store.putLayerMetadata("america", "background", "blue");
        store.putLayerMetadata("america", "style", "america-style");
        store.putLayerMetadata("australia", "background", "green");
        store.putLayerMetadata("australia", "style", "australia-style");
        // checking if the metadata was correctly stored
        assertThat(store.getLayerMetadata("america", "background"), is("blue"));
        assertThat(store.getLayerMetadata("america", "style"), is("america-style"));
        assertThat(store.getLayerMetadata("australia", "background"), is("green"));
        assertThat(store.getLayerMetadata("australia", "style"), is("australia-style"));
    }

    @Test
    public void testTileMetadataOperationsWithNull() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // let's store some metadata with null values
        store.putLayerMetadata(null, null, null);
        store.putLayerMetadata("america", "style", null);
        store.putLayerMetadata("australia", null, "green");
        // checking if the metadata was correctly stored
        assertThat(store.getLayerMetadata("america", "style"), nullValue());
        assertThat(store.getLayerMetadata("australia", null), nullValue());
        assertThat(store.getLayerMetadata(null, null), nullValue());
    }

    @Test
    public void testDeleteLayerOperation() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // created database files for different layers
        File asia1 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-0-500.sqlite"));
        File asia2 = createFileInRootDir(Utils.buildPath("grid2", "asia", "image_png", "11", "tiles-100-500.sqlite"));
        File asia3 = createFileInRootDir(Utils.buildPath("grid3", "asia", "image_png", "10", "tiles-0-500.sqlite"));
        File asia4 = createFileInRootDir(Utils.buildPath("grid3", "asia", "image_jpeg", "10", "tiles-0-500.sqlite"));
        File africa1 = createFileInRootDir(Utils.buildPath("grid1", "africa", "image_png", "10", "tiles-0-500.sqlite"));
        File america1 = createFileInRootDir(Utils.buildPath("grid1", "america", "image_png", "10", "tiles-0-500.sqlite"));
        File america2 = createFileInRootDir(Utils.buildPath("grid2", "america", "image_jpeg", "10", "tiles-0-500.sqlite"));
        File europe1 = createFileInRootDir(Utils.buildPath("grid1", "europe", "image_gif", "15", "tiles-4000-500.sqlite"));
        // deleting layer asia and europe
        store.delete("asia");
        store.delete("europe");
        assertThat(asia1.exists(), is(false));
        assertThat(asia2.exists(), is(false));
        assertThat(asia3.exists(), is(false));
        assertThat(asia4.exists(), is(false));
        assertThat(africa1.exists(), is(true));
        assertThat(america1.exists(), is(true));
        assertThat(america2.exists(), is(true));
        assertThat(europe1.exists(), is(false));
    }

    @Test
    public void testDeleteLayerWithGridSetOperation() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // created database files for different layers
        File asia1 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-0-500.sqlite"));
        File asia2 = createFileInRootDir(Utils.buildPath("grid2", "asia", "image_png", "11", "tiles-100-500.sqlite"));
        File asia3 = createFileInRootDir(Utils.buildPath("grid3", "asia", "image_png", "10", "tiles-0-500.sqlite"));
        File asia4 = createFileInRootDir(Utils.buildPath("grid3", "asia", "image_jpeg", "10", "tiles-0-500.sqlite"));
        File africa1 = createFileInRootDir(Utils.buildPath("grid1", "africa", "image_png", "10", "tiles-0-500.sqlite"));
        File america1 = createFileInRootDir(Utils.buildPath("grid1", "america", "image_png", "10", "tiles-0-500.sqlite"));
        File america2 = createFileInRootDir(Utils.buildPath("grid2", "america", "image_jpeg", "10", "tiles-0-500.sqlite"));
        File europe1 = createFileInRootDir(Utils.buildPath("grid1", "europe", "image_gif", "15", "tiles-4000-500.sqlite"));
        // deleting layer asia grid set grid3 and america grid set grid2
        store.deleteByGridsetId("asia", "grid3");
        store.deleteByGridsetId("america", "grid2");
        assertThat(asia1.exists(), is(true));
        assertThat(asia2.exists(), is(true));
        assertThat(asia3.exists(), is(false));
        assertThat(asia4.exists(), is(false));
        assertThat(africa1.exists(), is(true));
        assertThat(america1.exists(), is(true));
        assertThat(america2.exists(), is(false));
        assertThat(europe1.exists(), is(true));
    }

    @Test
    public void testDeleteLayerWithTileRangeEager() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        // activate eager mode
        configuration.setEagerDelete(true);
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // create the tile range
        long[][] rangeBounds = new long[][]{
                {0, 490, 10, 500, 10},
                {800, 950, 1005, 1020, 11}
        };
        TileRange tileRange = new TileRange("asia", "grid1", 10, 11, rangeBounds,
                MimeType.createFromExtension("png"), Collections.emptyMap());
        // created layer database files
        File asia1 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-0-0.sqlite"));
        File asia2 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-0-500.sqlite"));
        File asia3 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-500-0.sqlite"));
        File asia4 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-500-500.sqlite"));
        File asia5 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "11", "tiles-1000-1000.sqlite"));
        // deleting tiles range
        store.delete(tileRange);
        assertThat(asia1.exists(), is(false));
        assertThat(asia2.exists(), is(false));
        assertThat(asia3.exists(), is(true));
        assertThat(asia4.exists(), is(true));
        assertThat(asia5.exists(), is(false));
    }

    @Test
    public void testDeleteLayerWithTileRangeNoEager() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // create the tile range
        long[][] rangeBounds = new long[][]{
                {0, 10, 5, 15, 10},
                {980, 950, 1005, 1020, 11}
        };
        TileRange tileRange = new TileRange("asia", "grid1", 10, 11, rangeBounds,
                MimeType.createFromExtension("png"), Collections.emptyMap());
        // created layer database files
        File asia1 = buildRootFile(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-0-0.sqlite"));
        File asia2 = buildRootFile(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-500-500.sqlite"));
        // create some tiles
        store.put(TileObject.createCompleteTileObject("asia",
                new long[]{3, 12, 10}, "grid1", "image/png", null, stringToResource("IMAGE-10-50-10")));
        store.put(TileObject.createCompleteTileObject("asia",
                new long[]{510, 550, 10}, "grid1", "image/png", null, stringToResource("IMAGE-510-550-10")));
        // deleting tiles range
        store.delete(tileRange);
        assertThat(asia1.exists(), is(true));
        assertThat(asia2.exists(), is(true));
        // check that the correct tiles were deleted
        assertThat(store.get(TileObject.createQueryTileObject("asia",
                new long[]{10, 50, 10}, "grid1", "image/png", null)), is(false));
        assertThat(store.get(TileObject.createQueryTileObject("asia",
                new long[]{510, 550, 10}, "grid1", "image/png", null)), is(true));
    }

    @Test
    public void testLayerExistsOperation() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // created layer database file
        createFileInRootDir(Utils.buildPath("grid1", "europe", "image_png", "10", "tiles-0-0.sqlite"));
        // checking if layer exists
        assertThat(store.layerExists("europe"), is(true));
        assertThat(store.layerExists("asia"), is(false));
    }

    @Test
    public void testRenameOperation() throws Exception {
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        // create layers database files
        File asia1 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-0-0.sqlite"));
        File asia2 = createFileInRootDir(Utils.buildPath("grid1", "asia", "image_png", "10", "tiles-0-500.sqlite"));
        File europe1 = createFileInRootDir(Utils.buildPath("grid1", "europe", "image_png", "10", "tiles-500-0.sqlite"));
        // rename asia layers
        store.rename("asia", "australia");
        assertThat(asia1.exists(), is(false));
        assertThat(asia2.exists(), is(false));
        assertThat(europe1.exists(), is(true));
        assertThat(buildRootFile("grid1", "australia", "image_png", "10", "tiles-0-0.sqlite").exists(), is(true));
        assertThat(buildRootFile("grid1", "australia", "image_png", "10", "tiles-0-500.sqlite").exists(), is(true));
    }

    @Test
    public void testOpeningDatabaseFileWithMbtilesMetadata() throws Exception {
        // create and instantiate mbtiles metadata
        File mbtilesMetadataDirectory = buildRootFile("mbtiles-metadata");
        File mbtilesMetadataFile = new File(mbtilesMetadataDirectory, "europe_asia.properties");
        String mbtilesMetadata = "attribution=some attribution" + System.lineSeparator() +
                "bounds=-180,-90,180,90" + System.lineSeparator() +
                "description=some description" + System.lineSeparator() +
                "maxZoom=10" + System.lineSeparator() +
                "minZoom=0" + System.lineSeparator() +
                "type=base_layer" + System.lineSeparator() +
                "version=1.0" + System.lineSeparator();
        writeToFile(mbtilesMetadataFile, mbtilesMetadata);
        // instantiating the store
        MbtilesInfo configuration = getDefaultConfiguration();
        configuration.setMbtilesMetadataDirectory(mbtilesMetadataDirectory.getPath());
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(configuration);
        MbtilesBlobStore store = new MbtilesBlobStore(configuration, connectionManager);
        addStoresToClean(store);
        // create the tile that will be stored
        TileObject putTile = TileObject.createCompleteTileObject("europe:asia",
                new long[]{5, 5, 5}, "EPSG:4326", "image/png", null, stringToResource("IMAGE-5-5-5"));
        // storing the tile
        store.put(putTile);
        // checking if the file exists
        File file = buildRootFile("EPSG_4326", "europe_asia", "image_png", "5", "tiles-0-0.sqlite");
        assertThat(file.exists(), is(true));
        // checking that the user provided metadata was properly inserted
        connectionManager.executeQuery(file, resultSet -> {
            // extract the metadata values from the result set
            List<Utils.Tuple<String, String>> foundMetadata = new ArrayList<>();
            while(resultSet.next()) {
                foundMetadata.add(tuple(resultSet.getString(1), resultSet.getString(2)));
            }
            // let's see if we have the expected metadata
            assertThat(foundMetadata.size(), is(9));
            // the provided minZoom and maxZoom are ignored (geotools computes the real values)
            assertThat(foundMetadata, containsInAnyOrder(tuple("name", "europe:asia"),
                    tuple("version", "1.0"),
                    tuple("description", "some description"),
                    tuple("attribution", "some attribution"),
                    tuple("type", "base_layer"),
                    tuple("format", "png"),
                    tuple("bounds", "-180.0,-90.0,180.0,90.0"),
                    tuple("minzoom", "5"),
                    tuple("maxzoom", "5")));
            return null;
        }, "SELECT name, value FROM metadata;");
    }
}