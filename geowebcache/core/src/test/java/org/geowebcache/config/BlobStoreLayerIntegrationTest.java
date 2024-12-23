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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.junit.Test;

/** Tests that blob stores and layers remain in sync */
public class BlobStoreLayerIntegrationTest extends GWCConfigIntegrationTest {

    @Test
    public void testRenameBlobStoreChangesLayer() throws GeoWebCacheException {
        TileLayer layer = tileLayerDispatcher.getLayerList().iterator().next();
        BlobStoreInfo blobStore = blobStoreAggregator.getBlobStore(layer.getBlobStoreId());

        assertNotNull(blobStore);

        blobStoreAggregator.renameBlobStore(blobStore.getName(), "foobarbaz");

        layer = tileLayerDispatcher.getLayerList().iterator().next();
        assertEquals("foobarbaz", layer.getBlobStoreId());
    }
}
