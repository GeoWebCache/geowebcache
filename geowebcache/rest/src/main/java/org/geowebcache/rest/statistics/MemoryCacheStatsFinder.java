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
 */
package org.geowebcache.rest.statistics;

import java.lang.reflect.Field;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.DefaultStorageBroker;
import org.geowebcache.storage.StorageBroker;
import org.restlet.Finder;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Resource;

/**
 * {@link Finder} used for creating a new {@link MemoryCacheStatsResource} object
 * 
 * @author Nicola Lagomarsini Geosolutions
 */
public class MemoryCacheStatsFinder extends Finder {

    /** {@link Log} used for logging the exceptions */
    public static Log LOG = LogFactory.getLog(MemoryCacheStatsFinder.class);

    /** Store associated to the StorageBroker to use */
    private StorageBroker broker;

    public MemoryCacheStatsFinder(StorageBroker broker) {
        super(null, MemoryCacheStatsResource.class);
        // Add the store
        this.broker = broker;
    }

    @Override
    public Resource findTarget(Request request, Response response) {
        MemoryCacheStatsResource resource = (MemoryCacheStatsResource) super.findTarget(request,
                response);
        // Check if the StorageBroker contains a MemoryBlobStore
        BlobStore blobStore = null;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Getting BlobStore from the storage broker");
        }

        // Getting the BlobStore if present
        if (broker instanceof DefaultStorageBroker) {
            blobStore = ((DefaultStorageBroker) broker).getBlobStore();
        }

        // Add the blobStore to the Resource
        resource.setBlobStore(blobStore);
        return resource;
    }
}
