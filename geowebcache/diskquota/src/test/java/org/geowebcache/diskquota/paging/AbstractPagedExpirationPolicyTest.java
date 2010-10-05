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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;

import junit.framework.TestCase;

import org.easymock.classextension.EasyMock;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.diskquota.LayerQuota;
import org.geowebcache.diskquota.StorageUnit;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.TileRange;

/**
 * @author groldan
 * 
 */
public class AbstractPagedExpirationPolicyTest extends TestCase {

    private static final class MockPagedExipirationPolicy extends AbstractPagedExpirationPolicy {

        private MockPagedExipirationPolicy(TileBreeder tileBreeder, PageStore pageStore) {
            super(tileBreeder, pageStore);
        }

        @Override
        public String getName() {
            return "MockPolicy";
        }

        @Override
        protected Comparator<TilePage> getExpirationComparator() {
            return new Comparator<TilePage>() {
                public int compare(TilePage o1, TilePage o2) {
                    return o1.getNumHits() > o2.getNumHits() ? 1 : o1.getNumHits() == o2
                            .getNumHits() ? 0 : -1;
                }
            };
        }
    }

    private Hashtable<String, GridSubset> gridSubsets;

    private TileBreeder tileBreeder;

    private PageStore pageStore;

    private AbstractPagedExpirationPolicy policy;

    @Override
    protected void setUp() throws Exception {
        gridSubsets = new Hashtable<String, GridSubset>();
        GridSubset gridSubSet = GridSubsetFactory
                .createGridSubSet(new GridSetBroker(false, false).WORLD_EPSG4326);
        gridSubsets.put(gridSubSet.getName(), gridSubSet);

        tileBreeder = EasyMock.createNiceMock(TileBreeder.class);
        pageStore = EasyMock.createNiceMock(PageStore.class);
        EasyMock.expect(
                pageStore.getPages((String) EasyMock.anyObject(), (String) EasyMock.anyObject()))
                .andReturn(Collections.EMPTY_LIST).anyTimes();
        EasyMock.replay(pageStore);
        policy = new MockPagedExipirationPolicy(tileBreeder, pageStore);
    }

    @Override
    protected void tearDown() throws Exception {
        // not much to do
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#attach(org.geowebcache.layer.TileLayer, org.geowebcache.diskquota.LayerQuota)}
     * .
     */
    public final void testAttach() {
        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.expect(layer.getName()).andReturn("MockLayer").anyTimes();
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
     * 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public final void testDestroy() throws Exception {
        pageStore = EasyMock.createNiceMock(PageStore.class);
        EasyMock.expect(
                pageStore.getPages((String) EasyMock.anyObject(), (String) EasyMock.anyObject()))
                .andReturn(Collections.EMPTY_LIST).anyTimes();

        // this is one call we're interested in
        pageStore.savePages(EasyMock.eq("MockLayer"),
                EasyMock.eq(gridSubsets.keySet().iterator().next()),
                (ArrayList<TilePage>) EasyMock.eq(Collections.EMPTY_LIST));
        EasyMock.replay(pageStore);

        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.expect(layer.getName()).andReturn("MockLayer").anyTimes();
        EasyMock.expect(layer.getGridSubsets()).andReturn(gridSubsets).anyTimes();

        layer.addLayerListener((TileLayerListener) EasyMock.anyObject());

        // this is the other call we're interested in
        EasyMock.expect(layer.removeLayerListener((TileLayerListener) EasyMock.anyObject()))
                .andReturn(Boolean.TRUE);
        EasyMock.replay(layer);

        policy = new MockPagedExipirationPolicy(tileBreeder, pageStore);

        LayerQuota layerQuota = new LayerQuota(layer.getName(), policy.getName());
        // attach it
        policy.attach(layer, layerQuota);

        policy.destroy();
        EasyMock.verify(pageStore);
        EasyMock.verify(layer);
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#save(org.geowebcache.diskquota.LayerQuota)}
     * .
     * 
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public final void testSave() throws IOException {
        try {
            policy.save("nonAttachedLayer");
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        pageStore = EasyMock.createNiceMock(PageStore.class);
        EasyMock.expect(
                pageStore.getPages((String) EasyMock.anyObject(), (String) EasyMock.anyObject()))
                .andReturn(Collections.EMPTY_LIST).anyTimes();

        // this is one call we're interested in
        final String gridsetId = gridSubsets.keySet().iterator().next();
        pageStore.savePages(EasyMock.eq("MockLayer"), EasyMock.eq(gridsetId),
                (ArrayList<TilePage>) EasyMock.eq(Collections.EMPTY_LIST));
        EasyMock.replay(pageStore);

        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.expect(layer.getName()).andReturn("MockLayer").anyTimes();
        EasyMock.expect(layer.getGridSubsets()).andReturn(gridSubsets).anyTimes();

        layer.addLayerListener((TileLayerListener) EasyMock.anyObject());

        EasyMock.replay(layer);

        policy = new MockPagedExipirationPolicy(tileBreeder, pageStore);

        String layerName = layer.getName();

        LayerQuota layerQuota = new LayerQuota(layerName, policy.getName());
        // attach it
        policy.attach(layer, layerQuota);

        policy.save(layerName);
        EasyMock.verify(pageStore);
        EasyMock.verify(layer);
    }

    /**
     * Test method for
     * {@link org.geowebcache.diskquota.paging.AbstractPagedExpirationPolicy#expireTiles(java.lang.String)}
     * .
     * 
     * @throws GeoWebCacheException
     */
    public final void testExpireTiles() throws GeoWebCacheException {
        // mock a layer
        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.expect(layer.getName()).andReturn("MockLayer").anyTimes();
        EasyMock.expect(layer.getGridSubsets()).andReturn(gridSubsets).anyTimes();
        EasyMock.expect(layer.getMimeTypes())
                .andReturn(Collections.singletonList(MimeType.createFromFormat("image/png")))
                .anyTimes();

        layer.addLayerListener((TileLayerListener) EasyMock.anyObject());
        // finish recording the expected method calls on the layer
        EasyMock.replay(layer);

        String layerName = layer.getName();

        // Mock up a layer quota that exceeded in its allowed quota
        final LayerQuota layerQuota = new LayerQuota(layerName, policy.getName());
        layerQuota.setExpirationPolicy(policy);
        layerQuota.getQuota().setValue(1024);
        layerQuota.getQuota().setUnits(StorageUnit.KiB);
        // used quota exceeds allowed quota
        layerQuota.getUsedQuota().setValue(2);
        layerQuota.getUsedQuota().setUnits(StorageUnit.MiB);

        // mock up a truncate task that somehow changes the layer quota consumption
        class MockGWCTask extends GWCTask {
            public boolean called;

            @Override
            public void doAction() throws GeoWebCacheException {
                called = true;
                layerQuota.getUsedQuota().setValue(0.5);
            }

        }
        ;
        GWCTask truncateTask = new MockGWCTask();
        GWCTask[] truncateTasks = { truncateTask };

        tileBreeder = EasyMock.createNiceMock(TileBreeder.class);
        EasyMock.expect(
                tileBreeder.createTasks((TileRange) EasyMock.anyObject(),
                        (TileLayer) EasyMock.anyObject(), EasyMock.eq(GWCTask.TYPE.TRUNCATE),
                        EasyMock.eq(1), EasyMock.eq(false))).andReturn(truncateTasks);
        EasyMock.replay(tileBreeder);
        policy = new MockPagedExipirationPolicy(tileBreeder, pageStore);

        policy.attach(layer, layerQuota);
        // for expireTiles to work, there must be tile pages, so lets create a couple ones
        final String gridsetId = gridSubsets.keySet().iterator().next();
        policy.createTileInfo(layerQuota, gridsetId, 0, 1, 2);
        policy.expireTiles(layerName);

        EasyMock.verify(layer);
        EasyMock.verify(tileBreeder);
        assertTrue(((MockGWCTask) truncateTask).called);
    }

}
