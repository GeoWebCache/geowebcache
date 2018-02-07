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

import org.apache.commons.io.FileUtils;
import org.geowebcache.storage.TileObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

public final class SqliteBlobStoreTest extends TestSupport {

    @Test
    public void testFileReplaceOperation() throws Exception {
        // instantiating the stores
        File rootDirectoryA = Files.createTempDirectory("gwc-").toFile();
        File rootDirectoryB = Files.createTempDirectory("gwc-").toFile();
        addFilesToDelete(rootDirectoryA, rootDirectoryB);
        MbtilesInfo configurationA = getDefaultConfiguration();
        configurationA.setRootDirectory(rootDirectoryA.getPath());
        MbtilesInfo configurationB = getDefaultConfiguration();
        configurationB.setRootDirectory(rootDirectoryB.getPath());
        MbtilesBlobStore storeA = new MbtilesBlobStore(configurationA);
        MbtilesBlobStore storeB = new MbtilesBlobStore(configurationB);
        addStoresToClean(storeA, storeB);
        // create the tiles that will be stored
        TileObject putTileA = TileObject.createCompleteTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null, stringToResource("IMAGE-10-50-5-A"));
        TileObject putTileB = TileObject.createCompleteTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null, stringToResource("IMAGE-10-50-5-B"));
        // storing the tile
        storeA.put(putTileA);
        storeB.put(putTileB);
        // make sure connections to the created stores a re closed
        storeA.clear();
        storeB.clear();
        // check that database files exists
        String relativePath = Utils.buildPath("EPSG_4326", "africa", "image_png", "5", "tiles-0-0.sqlite");
        File fileA = new File(rootDirectoryA, relativePath);
        File fileB = new File(rootDirectoryB, relativePath);
        assertThat(fileA.exists(), is(true));
        assertThat(fileB.exists(), is(true));
        // replace store A database file with store B database file
        storeA.replace(fileB, relativePath);
        // let's query store A to see if we get store B tile
        TileObject getTile = TileObject.createQueryTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null);
        assertThat(storeA.get(getTile), is(true));
        assertThat(getTile.getBlob(), notNullValue());
        assertThat(resourceToString(getTile.getBlob()), is("IMAGE-10-50-5-B"));
        // clean up
        FileUtils.deleteQuietly(rootDirectoryA);
        FileUtils.deleteQuietly(rootDirectoryB);
    }

    @Test
    public void testDirectoryReplaceOperation() throws Exception {
        // instantiating the stores
        File rootDirectoryA = Files.createTempDirectory("replace-tests-a-").toFile();
        File rootDirectoryB = Files.createTempDirectory("replace-tests-a-").toFile();
        addFilesToDelete(rootDirectoryA, rootDirectoryB);
        MbtilesInfo configurationA = getDefaultConfiguration();
        configurationA.setRootDirectory(rootDirectoryA.getPath());
        MbtilesInfo configurationB = getDefaultConfiguration();
        configurationB.setRootDirectory(rootDirectoryB.getPath());
        MbtilesBlobStore storeA = new MbtilesBlobStore(configurationA);
        MbtilesBlobStore storeB = new MbtilesBlobStore(configurationB);
        addStoresToClean(storeA, storeB);
        // create the tiles that will be stored
        TileObject putTileA = TileObject.createCompleteTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null, stringToResource("IMAGE-10-50-5-A"));
        TileObject putTileB = TileObject.createCompleteTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null, stringToResource("IMAGE-10-50-5-B"));
        TileObject putTileC = TileObject.createCompleteTileObject("africa",
                new long[]{10, 5050, 15}, "EPSG:4326", "image/png", null, stringToResource("IMAGE-15-5050-5-B"));
        // storing the tile
        storeA.put(putTileA);
        storeB.put(putTileB);
        storeB.put(putTileC);
        // make sure connections to the created stores a re closed
        storeA.clear();
        storeB.clear();
        // check that database files exists
        String relativePathA = Utils.buildPath("EPSG_4326", "africa", "image_png", "5", "tiles-0-0.sqlite");
        String relativePathB = Utils.buildPath("EPSG_4326", "africa", "image_png", "15", "tiles-0-5000.sqlite");
        File fileA = new File(rootDirectoryA, relativePathA);
        File fileB = new File(rootDirectoryB, relativePathA);
        File fileC = new File(rootDirectoryB, relativePathB);
        assertThat(fileA.exists(), is(true));
        assertThat(fileB.exists(), is(true));
        assertThat(fileC.exists(), is(true));
        // replace store A database files with store B database files
        storeA.replace(rootDirectoryB);
        // let's query store A to see if we get store B tiles
        TileObject getTile = TileObject.createQueryTileObject("africa",
                new long[]{10, 50, 5}, "EPSG:4326", "image/png", null);
        assertThat(storeA.get(getTile), is(true));
        assertThat(getTile.getBlob(), notNullValue());
        assertThat(resourceToString(getTile.getBlob()), is("IMAGE-10-50-5-B"));
        // let's query the second tile
        getTile = TileObject.createQueryTileObject("africa",
                new long[]{10, 5050, 15}, "EPSG:4326", "image/png", null);
        assertThat(storeA.get(getTile), is(true));
        assertThat(getTile.getBlob(), notNullValue());
        assertThat(resourceToString(getTile.getBlob()), is("IMAGE-15-5050-5-B"));
        // clean up
        FileUtils.deleteQuietly(rootDirectoryA);
        FileUtils.deleteQuietly(rootDirectoryB);
    }
}