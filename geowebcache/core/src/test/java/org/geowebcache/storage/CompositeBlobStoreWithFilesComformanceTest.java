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
 * @author Kevin Smith, Boundless, 2017
 */

package org.geowebcache.storage;

import static org.easymock.EasyMock.not;
import static org.easymock.EasyMock.or;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import java.util.LinkedList;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import org.easymock.EasyMock;

public class CompositeBlobStoreWithFilesComformanceTest extends AbstractBlobStoreTest<CompositeBlobStore> {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    private TileLayerDispatcher tld;
    private DefaultStorageFinder defaultStorageFinder;
    private XMLConfiguration configuration;
    private TileLayer defaultLayer;
    private TileLayer defaultLayer1;
    private TileLayer defaultLayer2;
    private LinkedList<BlobStoreInfo> configs;

    private String DEFAULT_LAYER="testLayer";
    private String DEFAULT_LAYER1="testLayer1";
    private String DEFAULT_LAYER2="testLayer2";
    
    @Override
    public void createTestUnit() throws Exception {
        tld = createNiceMock("tld", TileLayerDispatcher.class);
        defaultStorageFinder = createNiceMock("defaultStorageFinder", DefaultStorageFinder.class);
        configuration = createNiceMock("configuration", XMLConfiguration.class);
        
        configs = new LinkedList<>();
        expect(configuration.getBlobStores()).andStubReturn(configs);
        
        expect(defaultStorageFinder.getDefaultPath()).andStubReturn(
                temp.getRoot().getAbsolutePath());
        
        defaultLayer = createNiceMock("defaultLayer", TileLayer.class);
        defaultLayer1 = createNiceMock("defaultLayer1", TileLayer.class);
        defaultLayer2 = createNiceMock("defaultLayer2", TileLayer.class);
        expect(tld.getTileLayer(eq(DEFAULT_LAYER))).andStubReturn(defaultLayer);
        expect(tld.getTileLayer(eq(DEFAULT_LAYER1))).andStubReturn(defaultLayer1);
        expect(tld.getTileLayer(eq(DEFAULT_LAYER2))).andStubReturn(defaultLayer2);
        expect(tld.getTileLayer(not(or(eq(DEFAULT_LAYER), or(eq(DEFAULT_LAYER1), eq(DEFAULT_LAYER2)))))).andStubThrow(
                new GeoWebCacheException("layer not found"));
        
        EasyMock.replay(tld, defaultStorageFinder, configuration, defaultLayer, defaultLayer1, defaultLayer2);
        store = new CompositeBlobStore(tld, defaultStorageFinder, configuration);
    }
    
    @Before
    public void setEvents() throws Exception {
        this.events = true;
    }
}
