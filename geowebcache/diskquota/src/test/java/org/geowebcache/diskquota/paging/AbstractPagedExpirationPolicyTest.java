/**
 * 
 */
package org.geowebcache.diskquota.paging;

import java.util.Hashtable;
import java.util.List;

import junit.framework.TestCase;

import org.easymock.classextension.EasyMock;
import org.geowebcache.diskquota.ConfigLoader;
import org.geowebcache.diskquota.LayerQuota;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.util.TestUtils;

/**
 * @author groldan
 * 
 */
public class AbstractPagedExpirationPolicyTest extends TestCase {

    private TileBreeder tileBreeder;

    private ConfigLoader configLoader;

    private AbstractPagedExpirationPolicy policy;

    @Override
    protected void setUp() throws Exception {
        tileBreeder = EasyMock.createNiceMock(TileBreeder.class);
        configLoader = EasyMock.createNiceMock(ConfigLoader.class);
        policy = new AbstractPagedExpirationPolicy(tileBreeder, configLoader) {

            @Override
            protected List<TilePage> sortPagesForExpiration(List<TilePage> allPages) {
                return allPages;
            }

            @Override
            public String getName() {
                return "MockPolicy";
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        // not much to do
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#getName()}.
     */
    public final void testGetName() {
        assertEquals("MockPolicy", policy.getName());
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#attach(org.geowebcache.layer.TileLayer, org.geowebcache.diskquota.LayerQuota)}
     * .
     */
    public final void testAttach() {
        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.expect(layer.getName()).andReturn("MockLayer").anyTimes();
        Hashtable<String, GridSubset> gridSubsets = new Hashtable<String, GridSubset>();
        gridSubsets.put("fakeGridSubset", GridSubsetFactory.createGridSubSet(new GridSetBroker(
                false, false).WORLD_EPSG4326));
        EasyMock.expect(layer.getGridSubsets()).andReturn(gridSubsets).anyTimes();

        // this is the call we're interested in
        layer.addLayerListener((TileLayerListener) EasyMock.anyObject());

        EasyMock.replay(layer);

        LayerQuota layerQuota = new LayerQuota(layer.getName(), "MockPolicy");
        policy.attach(layer, layerQuota);
        EasyMock.verify(layer);
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#dettach(java.lang.String)}
     * .
     */
    public final void testDettach() {
        assertFalse(policy.dettach("notAttachedLayerName"));

        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.expect(layer.getName()).andReturn("MockLayer").anyTimes();
        Hashtable<String, GridSubset> gridSubsets = new Hashtable<String, GridSubset>();
        gridSubsets.put("fakeGridSubset", GridSubsetFactory.createGridSubSet(new GridSetBroker(
                false, false).WORLD_EPSG4326));
        EasyMock.expect(layer.getGridSubsets()).andReturn(gridSubsets).anyTimes();

        layer.addLayerListener((TileLayerListener) EasyMock.anyObject());

        // this is the call we're interested in
        EasyMock.expect(layer.removeLayerListener((TileLayerListener) EasyMock.anyObject()))
                .andReturn(Boolean.TRUE);
        EasyMock.replay(layer);

        LayerQuota layerQuota = new LayerQuota(layer.getName(), "MockPolicy");
        // attach it
        policy.attach(layer, layerQuota);

        // now dettach it and let EasyMock verify removeLayerListener is called
        assertTrue(policy.dettach(layer.getName()));
        EasyMock.verify(layer);
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#destroy()}.
     */
    public final void testDestroy() {
        fail("Not yet implemented"); // TODO
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#save(org.geowebcache.diskquota.LayerQuota)}
     * .
     */
    public final void testSave() {
        fail("Not yet implemented"); // TODO
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#expireTiles(java.lang.String)}
     * .
     */
    public final void testExpireTiles() {
        fail("Not yet implemented"); // TODO
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#createInfoFor(org.geowebcache.diskquota.LayerQuota, java.lang.String, long[], java.io.File)}
     * .
     */
    public final void testCreateInfoFor() {
        fail("Not yet implemented"); // TODO
    }

}
