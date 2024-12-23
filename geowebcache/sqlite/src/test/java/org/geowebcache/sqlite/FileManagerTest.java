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
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import static org.geowebcache.sqlite.Utils.Tuple;
import static org.geowebcache.sqlite.Utils.Tuple.tuple;
import static org.geowebcache.sqlite.Utils.tuplesToMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.junit.Test;

public final class FileManagerTest extends TestSupport {

    @Test
    public void testGetLayerAssociatedFilesWithMultipleLevels() throws Exception {
        String pathTemplate =
                Utils.buildPath("tiles", "{format}", "{style}", "zoom-{z}", "{layer}", "ranges-{x}-{y}.sqlite");
        File fileAsiaA = createFileInRootDir(
                Utils.buildPath("tiles", "png", "default", "zoom-10", "asia", "ranges-0-500.sqlite"));
        File fileAsiaB = createFileInRootDir(
                Utils.buildPath("tiles", "png", "default", "zoom-11", "asia", "ranges-100-500.sqlite"));
        File fileAsiaC = createFileInRootDir(
                Utils.buildPath("tiles", "png", "dark-borders", "zoom-10", "asia", "ranges-0-500.sqlite"));
        File fileAsiaD = createFileInRootDir(
                Utils.buildPath("tiles", "jpeg", "dark-borders", "zoom-10", "asia", "ranges-0-500.sqlite"));
        File fileAfricaA = createFileInRootDir(
                Utils.buildPath("tiles", "png", "default", "zoom-10", "africa", "ranges-0-500.sqlite"));
        File fileAmericaA = createFileInRootDir(
                Utils.buildPath("tiles", "png", "dark-borders", "zoom-10", "america", "ranges-0-500.sqlite"));
        File fileAmericaB = createFileInRootDir(
                Utils.buildPath("tiles", "jpeg", "dark-borders", "zoom-10", "america", "ranges-0-500.sqlite"));
        File fileEuropeA = createFileInRootDir(
                Utils.buildPath("tiles", "gif", "dark-borders", "zoom-15", "europe", "ranges-4000-500.sqlite"));
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 500, 500);
        List<File> asiaFiles = fileManager.getFiles("asia");
        assertThat(asiaFiles, containsInAnyOrder(fileAsiaA, fileAsiaB, fileAsiaC, fileAsiaD));
        List<File> africaFiles = fileManager.getFiles("africa");
        assertThat(africaFiles, containsInAnyOrder(fileAfricaA));
        List<File> americaFiles = fileManager.getFiles("america");
        assertThat(americaFiles, containsInAnyOrder(fileAmericaA, fileAmericaB));
        List<File> europeFiles = fileManager.getFiles("europe");
        assertThat(europeFiles, containsInAnyOrder(fileEuropeA));
    }

    @Test
    public void testGetLayerAssociatedFilesWithNoLayer() throws Exception {
        String pathTemplate = Utils.buildPath("tiles", "{z}", "tiles-{x}.sqlite");
        File fileA = createFileInRootDir(Utils.buildPath("tiles", "10", "tiles-0.sqlite"));
        File fileB = createFileInRootDir(Utils.buildPath("tiles", "10", "tiles-500.sqlite"));
        File fileC = createFileInRootDir(Utils.buildPath("tiles", "11", "tiles-0.sqlite"));
        File fileD = createFileInRootDir(Utils.buildPath("tiles", "11", "tiles-500.sqlite"));
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 1000, 1000);
        List<File> filesA = fileManager.getFiles((String) null);
        assertThat(filesA, containsInAnyOrder(fileA, fileB, fileC, fileD));
        List<File> filesB = fileManager.getFiles("something");
        assertThat(filesB, containsInAnyOrder(fileA, fileB, fileC, fileD));
    }

    @Test
    public void testLayerAssociatedFilesWithOnlyOneLevel() throws Exception {
        String pathTemplate = "{layer}.sqlite";
        File asiaFile = createFileInRootDir("asia.sqlite");
        File americaFile = createFileInRootDir("america.sqlite");
        File africaFile = createFileInRootDir("africa.sqlite");
        File australiaFile = createFileInRootDir("australia.sqlite");
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 1000, 1000);
        List<File> asiaFiles = fileManager.getFiles("asia");
        assertThat(asiaFiles, containsInAnyOrder(asiaFile));
        List<File> americaFiles = fileManager.getFiles("america");
        assertThat(americaFiles, containsInAnyOrder(americaFile));
        List<File> africaFiles = fileManager.getFiles("africa");
        assertThat(africaFiles, containsInAnyOrder(africaFile));
        List<File> australiaFiles = fileManager.getFiles("australia");
        assertThat(australiaFiles, containsInAnyOrder(australiaFile));
    }

    @Test
    public void testGetGridSetAndLayerAssociatedFiles() throws Exception {
        String pathTemplate = Utils.buildPath(
                "tiles", "{grid}", "{format}", "{style}", "zoom-{z}", "{layer}", "ranges-{x}-{y}.sqlite");
        File fileAsiaGrid1A = createFileInRootDir(
                Utils.buildPath("tiles", "grid1", "png", "default", "zoom-10", "asia", "ranges-0-500.sqlite"));
        File fileAsiaGrid2B = createFileInRootDir(
                Utils.buildPath("tiles", "grid2", "png", "default", "zoom-11", "asia", "ranges-100-500.sqlite"));
        File fileAsiaGrid3C = createFileInRootDir(
                Utils.buildPath("tiles", "grid3", "png", "dark-borders", "zoom-10", "asia", "ranges-0-500.sqlite"));
        File fileAsiaGrid3D = createFileInRootDir(
                Utils.buildPath("tiles", "grid3", "jpeg", "dark-borders", "zoom-10", "asia", "ranges-0-500.sqlite"));
        File fileAfricaGrid1A = createFileInRootDir(
                Utils.buildPath("tiles", "grid1", "png", "default", "zoom-10", "africa", "ranges-0-500.sqlite"));
        File fileAmericaGrid1A = createFileInRootDir(
                Utils.buildPath("tiles", "grid1", "png", "dark-borders", "zoom-10", "america", "ranges-0-500.sqlite"));
        File fileAmericaGrid2B = createFileInRootDir(
                Utils.buildPath("tiles", "grid2", "jpeg", "dark-borders", "zoom-10", "america", "ranges-0-500.sqlite"));
        File fileEuropeGrid1A = createFileInRootDir(Utils.buildPath(
                "tiles", "grid1", "gif", "dark-borders", "zoom-15", "europe", "ranges-4000-500.sqlite"));
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 500, 500);
        List<File> asiaFilesGrid1 = fileManager.getFiles("asia", "grid1");
        assertThat(asiaFilesGrid1, containsInAnyOrder(fileAsiaGrid1A));
        List<File> asiaFilesGrid2 = fileManager.getFiles("asia", "grid2");
        assertThat(asiaFilesGrid2, containsInAnyOrder(fileAsiaGrid2B));
        List<File> asiaFilesGrid3 = fileManager.getFiles("asia", "grid3");
        assertThat(asiaFilesGrid3, containsInAnyOrder(fileAsiaGrid3C, fileAsiaGrid3D));
        List<File> africaFilesGrid1 = fileManager.getFiles("africa", "grid1");
        assertThat(africaFilesGrid1, containsInAnyOrder(fileAfricaGrid1A));
        List<File> americaFilesGrid1 = fileManager.getFiles("america", "grid1");
        assertThat(americaFilesGrid1, containsInAnyOrder(fileAmericaGrid1A));
        List<File> americaFilesGrid2 = fileManager.getFiles("america", "grid2");
        assertThat(americaFilesGrid2, containsInAnyOrder(fileAmericaGrid2B));
        List<File> europeFilesGrid1 = fileManager.getFiles("europe", "grid1");
        assertThat(europeFilesGrid1, containsInAnyOrder(fileEuropeGrid1A));
    }

    @Test
    public void testSimplePathTemplate() throws Exception {
        genericTestPath(
                Utils.buildPath("tiles", "{grid}", "layers", "{layer}.sqlite"),
                Utils.buildPath("tiles", "wgs84", "layers", "europe.sqlite"),
                null,
                null,
                "europe",
                "wgs84",
                null);
    }

    @Test
    public void testPathTemplateWithRanges() throws Exception {
        genericTestPath(
                Utils.buildPath("{grid}", "tiles", "{layer}", "zoom-{z}", "ranges_{x}-{y}.sqlite"),
                Utils.buildPath("wgs84", "tiles", "usa", "zoom-15", "ranges_3000-1000.sqlite"),
                null,
                new long[] {3024, 1534, 15},
                "usa",
                "wgs84",
                null);
    }

    @Test
    public void testPathTemplateWithParameters() throws Exception {
        genericTestPath(
                Utils.buildPath("{background}-tiles", "{grid}", "{layer}-{style}", "zoom-{z}", "ranges{x}{y}.sqlite"),
                Utils.buildPath("blue-tiles", "wgs84", "america-default", "zoom-15", "ranges30001000.sqlite"),
                null,
                new long[] {3024, 1534, 15},
                "america",
                "wgs84",
                null,
                tuple("style", "default"),
                tuple("background", "blue"));
    }

    @Test
    public void testPathTemplateWithTileObject() throws Exception {
        TileObject tile = TileObject.createCompleteTileObject(
                "africa",
                new long[] {7050, 5075, 11},
                "wgs84",
                "jpeg",
                tuplesToMap(tuple("style", "dark borders"), tuple("background", "blue")),
                null);
        String pathTemplate =
                Utils.buildPath("{format}-tiles", "{grid}", "{layer}-{style}", "zoom-{z}", "ranges-{x}_{y}.sqlite");
        String expectedPath =
                Utils.buildPath("jpeg-tiles", "wgs84", "africa-dark_borders", "zoom-11", "ranges-7000_4000.sqlite");
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 2000, 1000);
        File file = fileManager.getFile(tile);
        assertThat(file.getCanonicalPath(), is(getRootDirectoryPath() + File.separator + expectedPath));
    }

    @Test
    public void testPathTemplateWithInvalidChars() throws Exception {
        genericTestPath(
                Utils.buildPath("{grid}", "{format}", "{layer}.sqlite"),
                Utils.buildPath("epsg_4326", "image_png", "europe_borders.sqlite"),
                null,
                null,
                "europe\\borders",
                "epsg:4326",
                "image/png");
    }

    @Test
    public void testGetFilesByTileRange() throws Exception {
        String pathTemplate = Utils.buildPath("tiles", "{z}", "tiles-{x}-{y}.sqlite");
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 10, 10);
        File file1 = createFileInRootDir(Utils.buildPath("tiles", "5", "tiles-0-0.sqlite"));
        File file2 = createFileInRootDir(Utils.buildPath("tiles", "5", "tiles-10-0.sqlite"));
        File file3 = createFileInRootDir(Utils.buildPath("tiles", "5", "tiles-0-10.sqlite"));
        File file4 = createFileInRootDir(Utils.buildPath("tiles", "5", "tiles-10-10.sqlite"));
        File file5 = createFileInRootDir(Utils.buildPath("tiles", "6", "tiles-10-0.sqlite"));
        long[][] rangeBounds = {
            {9, 9, 12, 13, 5},
            {16, 5, 16, 5, 6}
        };
        TileRange tileRange = new TileRange(
                "layer", "EPSG:4326", 5, 6, rangeBounds, MimeType.createFromExtension("png"), Collections.emptyMap());
        Map<File, List<long[]>> files = fileManager.getFiles(tileRange);
        assertThat(files, notNullValue());
        assertThat(files.size(), is(5));
        checkFileExistsAndContainsTiles(files, file1, new long[] {9, 9, 9, 9, 5});
        checkFileExistsAndContainsTiles(files, file2, new long[] {10, 9, 12, 9, 5});
        checkFileExistsAndContainsTiles(files, file3, new long[] {9, 10, 9, 13, 5});
        checkFileExistsAndContainsTiles(files, file4, new long[] {10, 10, 12, 13, 5});
        checkFileExistsAndContainsTiles(files, file5, new long[] {16, 5, 16, 5, 6});
    }

    @Test
    public void testPathSeparatorsAreConvertedToOsOnes() {
        String pathTemplate = "something-{layer}\\{grid-something}-something{grid}\\-el/se{format}/something-{params}";
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 250, 250);
        File file = fileManager.getFile(
                "parameters_id", new long[] {0, 0, 0}, "layer_name", "grid_set", "format_name", Collections.emptyMap());
        assertThat(
                file.toString(),
                is(buildRootFile(
                                "something-layer_name",
                                "null-somethinggrid_set",
                                "-el",
                                "seformat_name",
                                "something-parameters_id")
                        .toString()));
    }

    private void checkFileExistsAndContainsTiles(Map<File, List<long[]>> files, File file, long[]... expectedTiles) {
        List<long[]> tiles = files.get(file);
        assertThat(tiles, notNullValue());
        assertThat(tiles.size(), is(expectedTiles.length));
        assertThat(tiles, containsInAnyOrder(expectedTiles));
    }

    @SafeVarargs
    private final void genericTestPath(
            String pathTemplate,
            String expectedPath,
            String parametersId,
            long[] xyz,
            String layerName,
            String gridSetId,
            String format,
            Tuple<String, String>... tuples) {
        genericTestPath(
                pathTemplate, expectedPath, parametersId, xyz, layerName, gridSetId, format, tuplesToMap(tuples));
    }

    private void genericTestPath(
            String pathTemplate,
            String expectedPath,
            String parametersId,
            long[] xyz,
            String layerName,
            String gridSetId,
            String format,
            Map<String, String> parameters) {
        FileManager fileManager = new FileManager(getRootDirectory(), pathTemplate, 1000, 1000);
        File file = fileManager.getFile(parametersId, xyz, layerName, gridSetId, format, parameters);
        assertThat(file.getPath(), is(new File(getRootDirectory(), expectedPath).getPath()));
    }
}
