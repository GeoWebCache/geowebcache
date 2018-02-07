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
 * @author Kevin Smith, Boundless, Copyright 2016
 */

package org.geowebcache.sqlite;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.geotools.mbtiles.MBTilesFileVectorTileTest;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.storage.TileObject;
import org.junit.Before;
import org.junit.Test;

public class MbtilesPBFTest extends TestSupport {
    File file;
    String layer;
    
    @Before
    public void copyData() throws Exception {
        file = buildRootFile("planet.mbtiles");
        URL template = org.geotools.mbtiles.MBTilesFileVectorTileTest.class.getResource("planet.mbtiles");
        try(
            InputStream in = template.openStream();
            OutputStream out = new FileOutputStream(file);
        ){
            IOUtils.copy(in, out);
        }
        layer = "planet";
    }
    
    @Test
    public void testGetTileDefaultUnzip() throws Exception {
        MbtilesInfo configuration = getDefaultConfiguration();
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        TileObject tile = TileObject.createQueryTileObject(layer,
                new long[]{0, 0, 0}, "EPSG:900913", ApplicationMime.mapboxVector.getFormat(), null);
        assertThat(store.get(tile), is(true));
        try(
            InputStream is = MBTilesFileVectorTileTest.class.getResourceAsStream("tile_data.pbf.gz")
        ){
            assertTrue(IOUtils.contentEquals(tile.getBlob().getInputStream(),is));
        }
    }
    
    @Test
    public void testGetTileDoUnzip() throws Exception {
        MbtilesInfo configuration = getDefaultConfiguration();
        configuration.setGzipVector(true);
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        TileObject tile = TileObject.createQueryTileObject(layer,
                new long[]{0, 0, 0}, "EPSG:900913", ApplicationMime.mapboxVector.getFormat(), null);
        assertThat(store.get(tile), is(true));
        try(
            InputStream is = MBTilesFileVectorTileTest.class.getResourceAsStream("tile_data.pbf")
        ){
            assertTrue(IOUtils.contentEquals(tile.getBlob().getInputStream(),is));
        }
    }
    
    @Test
    public void testGetTileNoUnzip() throws Exception {
        MbtilesInfo configuration = getDefaultConfiguration();
        configuration.setGzipVector(false);
        MbtilesBlobStore store = new MbtilesBlobStore(configuration);
        addStoresToClean(store);
        TileObject tile = TileObject.createQueryTileObject(layer,
                new long[]{0, 0, 0}, "EPSG:900913", ApplicationMime.mapboxVector.getFormat(), null);
        assertThat(store.get(tile), is(true));
        try(
            InputStream is = MBTilesFileVectorTileTest.class.getResourceAsStream("tile_data.pbf.gz")
        ){
            assertTrue(IOUtils.contentEquals(tile.getBlob().getInputStream(),is));
        }
    }
    
    @Override
    protected MbtilesInfo getDefaultConfiguration() {
        MbtilesInfo config = super.getDefaultConfiguration();
        config.setTemplatePath("{layer}.mbtiles");
        return config;
    }
    
}
