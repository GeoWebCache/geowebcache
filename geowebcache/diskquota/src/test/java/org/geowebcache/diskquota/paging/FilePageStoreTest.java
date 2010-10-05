/**
 * 
 */
package org.geowebcache.diskquota.paging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.easymock.classextension.EasyMock;
import org.geowebcache.diskquota.ConfigLoader;

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
        final String expectedFileName = layerName + "." + gridSetId + ".pages";

        mockConfigLoader = EasyMock.createMock(ConfigLoader.class);
        store = new FilePageStore(mockConfigLoader);
        final ByteArrayOutputStream mockOut = new ByteArrayOutputStream();
        EasyMock.expect(mockConfigLoader.getStorageOutputStream(EasyMock.eq(expectedFileName)))
                .andReturn(mockOut);
        EasyMock.replay(mockConfigLoader);

        store.savePages(layerName, gridSetId, pages);

        EasyMock.verify(mockConfigLoader);

        mockConfigLoader = EasyMock.createMock(ConfigLoader.class);
        store = new FilePageStore(mockConfigLoader);
        InputStream mockInputStream = new ByteArrayInputStream(mockOut.toByteArray());
        EasyMock.expect(mockConfigLoader.getStorageInputStream(EasyMock.eq(expectedFileName)))
                .andReturn(mockInputStream);
        EasyMock.replay(mockConfigLoader);

        List<TilePage> deserialized = store.getPages(layerName, gridSetId);

        EasyMock.verify(mockConfigLoader);
        assertEquals(pages, deserialized);
    }

}
