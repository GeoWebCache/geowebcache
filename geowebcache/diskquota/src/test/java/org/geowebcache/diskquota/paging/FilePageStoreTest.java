/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota.paging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.diskquota.ConfigLoader;
import org.geowebcache.util.ApplicationContextProvider;

/**
 * @author groldan
 * 
 */
public class FilePageStoreTest extends TestCase {

    private FilePageStore store;

    private ConfigLoader mockConfigLoader;

    private List<TilePage> pages;

    final String layerName = "someLayer";

    final String gridSetId = "someGridsetId";

    @Override
    protected void setUp() throws Exception {
        pages = new ArrayList<TilePage>();
        pages.add(new TilePage(layerName, gridSetId, 1, 2, 3, 1000L, 50L, 5));
        pages.add(new TilePage(layerName, gridSetId, 4, 5, 6, 1000L, 50L, 5));
        pages.add(new TilePage(layerName, gridSetId, 7, 8, 9, 1000L, 50L, 5));

    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.FilePageStore#savePages(java.lang.String, java.lang.String, java.util.ArrayList)}
     */
    public void testSaveAndGetPages() throws Exception {
        final String[] expectedFileName = { "diskquota_pagestore",
                layerName + "." + gridSetId + ".pages" };

        final ByteArrayOutputStream mockOut = new ByteArrayOutputStream();
        mockConfigLoader = new ConfigLoader(null, new ApplicationContextProvider(), null) {
            @Override
            public OutputStream getStorageOutputStream(String... fileNameRelPath)
                    throws IOException {
                assertTrue(Arrays.equals(expectedFileName, fileNameRelPath));
                return mockOut;
            }
        };
        store = new FilePageStore(mockConfigLoader);

        store.savePages(layerName, gridSetId, pages);

        final InputStream mockInputStream = new ByteArrayInputStream(mockOut.toByteArray());
        mockConfigLoader = new ConfigLoader(null, new ApplicationContextProvider(), null) {
            @Override
            public InputStream getStorageInputStream(String... fileNameRelPath) throws IOException {
                assertTrue(Arrays.equals(expectedFileName, fileNameRelPath));
                return mockInputStream;
            }
        };
        store = new FilePageStore(mockConfigLoader);

        List<TilePage> deserialized = store.getPages(layerName, gridSetId);

        assertEquals(pages, deserialized);
    }
}
