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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.blobstore.file;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;
import org.easymock.EasyMock;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.geowebcache.storage.blobstore.file.XYZFilePathGenerator;
import org.geowebcache.storage.blobstore.file.XYZFilePathGenerator.Convention;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class XYZFileBlobStoreComformanceTest extends AbstractBlobStoreTest<FileBlobStore> {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Parameterized.Parameter
    public Convention convention;

    private TileLayerDispatcher layers;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{Convention.TMS}, {Convention.XYZ}});
    }

    @Override
    @SuppressWarnings("CatchFail")
    public void createTestUnit() throws Exception {
        this.layers = createMock(TileLayerDispatcher.class);
        GridSet wgs84Grid = new DefaultGridsets(false, false).worldEpsg4326();
        GridSubset gridSubset = GridSubsetFactory.createGridSubSet(wgs84Grid);
        Stream.of("testLayer", "testLayer1", "testLayer2")
                .map(name -> {
                    TileLayer layer = createMock(name, TileLayer.class);
                    expect(layer.getName()).andStubReturn(name);
                    expect(layer.getId()).andStubReturn(name);
                    expect(layer.getGridSubsets()).andStubReturn(Collections.singleton("testGridSet1"));
                    expect(layer.getGridSubset(EasyMock.anyString())).andStubReturn(gridSubset);
                    expect(layer.getMimeTypes()).andStubReturn(Arrays.asList(org.geowebcache.mime.ImageMime.png));
                    try {
                        expect(layers.getTileLayer(eq(name))).andStubReturn(layer);
                    } catch (GeoWebCacheException e) {
                        fail();
                    }
                    return layer;
                })
                .forEach(EasyMock::replay);
        replay(layers);

        String root = temp.getRoot().getAbsolutePath();
        this.store = new FileBlobStore(root, new XYZFilePathGenerator(root, layers, convention));
    }
}
