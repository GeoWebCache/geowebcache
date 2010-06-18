/**
 * 
 */
package org.geowebcache.diskquota.paging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    @Override
    protected void setUp() throws Exception {
        pages = new ArrayList<TilePage>();
        pages.add(new TilePage(1, 2, 3, 1000L, 50L, 5));
        pages.add(new TilePage(4, 5, 6, 1000L, 50L, 5));
        pages.add(new TilePage(7, 8, 9, 1000L, 50L, 5));

        mockConfigLoader = EasyMock.createMock(ConfigLoader.class);

        store = new FilePageStore(mockConfigLoader);
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.FilePageStore#getPages(java.lang.String, java.lang.String)}
     * .
     */
    public void testGetPages() throws Exception {
        final String layerName = "someLayer";
        final String gridSetId = "someGridsetId";
        final String expectedFileName = layerName + "." + gridSetId + ".pages";

        InputStream mockStream;
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ObjectOutputStream(out).writeObject(this.pages);
            byte[] serialized = out.toByteArray();
            mockStream = new ByteArrayInputStream(serialized);
        }
        EasyMock.expect(mockConfigLoader.getStorageInputStream(EasyMock.eq(expectedFileName)))
                .andReturn(mockStream);
        EasyMock.replay(mockConfigLoader);

        List<TilePage> loaded = store.getPages(layerName, gridSetId);
        assertEquals(pages, loaded);
        EasyMock.verify(mockConfigLoader);
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.FilePageStore#savePages(java.lang.String, java.lang.String, java.util.ArrayList)}
     */
    public void testSavePages() throws Exception {
        final String layerName = "someLayer";
        final String gridSetId = "someGridsetId";
        final String expectedFileName = layerName + "." + gridSetId + ".pages";

        final ByteArrayOutputStream mockOut = new ByteArrayOutputStream();
        EasyMock.expect(mockConfigLoader.getStorageOutputStream(EasyMock.eq(expectedFileName)))
                .andReturn(mockOut);
        EasyMock.replay(mockConfigLoader);
        store.savePages(layerName, gridSetId, pages);

        Object deserialized = new ObjectInputStream(new ByteArrayInputStream(mockOut.toByteArray()))
                .readObject();

        assertEquals(pages, deserialized);
    }

}
