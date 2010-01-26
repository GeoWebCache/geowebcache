package org.geowebcache.georss;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import junit.framework.TestCase;

import org.easymock.classextension.EasyMock;
import org.geowebcache.georss.FilterMatrixSeedAreaProvider;
import org.geowebcache.georss.TileGridFilterMatrix;
import org.geowebcache.util.TestUtils;

public class FilterMatrixSeedAreaProviderTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testNextGridLocation() {

        long[][] mockCoverages = {//
        new long[] { 0, 0, 1, 0 },//
                new long[] { 5, 5, 6, 6 } //
        };
        TileGridFilterMatrix matrix = EasyMock.createMock(TileGridFilterMatrix.class);
        expect(matrix.getNumLevels()).andReturn(2);
        expect(matrix.getStartLevel()).andReturn(5);

        expect(matrix.getCoveredBounds(eq(5))).andReturn(mockCoverages[0]);
        expect(matrix.getCoveredBounds(eq(6))).andReturn(mockCoverages[1]);

        // calls to nextGridLocation
        expect(matrix.isTileSet(0, 0, 5)).andReturn(false);
        expect(matrix.isTileSet(1, 0, 5)).andReturn(true);

        expect(matrix.isTileSet(5, 5, 6)).andReturn(false);
        expect(matrix.isTileSet(6, 5, 6)).andReturn(false);
        expect(matrix.isTileSet(5, 6, 6)).andReturn(true);
        expect(matrix.isTileSet(6, 6, 6)).andReturn(true);

        replay(matrix);
        FilterMatrixSeedAreaProvider provider = new FilterMatrixSeedAreaProvider(matrix,
                "fakeGridsetId", null);

        TestUtils.assertEquals(new long[] { 1, 0, 5 }, provider.nextGridLocation());
        TestUtils.assertEquals(new long[] { 5, 6, 6 }, provider.nextGridLocation());
        TestUtils.assertEquals(new long[] { 6, 6, 6 }, provider.nextGridLocation());
        assertNull(provider.nextGridLocation());

        verify(matrix);
    }
}
