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

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.geowebcache.storage.blobstore.memory.NullBlobStore;
import org.geowebcache.storage.blobstore.memory.guava.GuavaCacheProvider;
import org.junit.Test;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;

/**
 * Test class for testing that the statistics via REST are reported correctly
 * 
 * @author Nicola Lagomarsini Geosolutions
 */
public class MemoryCacheStatsTest {

    @Test
    public void testStatsXML() throws IOException {
        // Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mbs.setCacheProvider(cache);

        // Create the request
        Request request = new Request();
        request.setMethod(Method.GET);
        request.getAttributes().put("extension", "xml");
        Response response = new Response(request);

        // Create the resource
        MemoryCacheStatsResource resource = new MemoryCacheStatsResource();
        resource.setBlobStore(mbs);
        resource.setRequest(request);
        resource.setResponse(response);

        // Handle the request
        resource.handleGet();

        // Check the result
        Representation entity = response.getEntity();
        String text = entity.getText();

        assertTrue(text.contains("<gwcInMemoryCacheStatistics>"));
        assertTrue(text.contains("<hitCount>"));
        assertTrue(text.contains("<missCount>"));
        assertTrue(text.contains("<evictionCount>"));
    }

    @Test
    public void testStatsJSON() throws IOException {
        // Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mbs.setCacheProvider(cache);

        // Create the request
        Request request = new Request();
        request.setMethod(Method.GET);
        request.getAttributes().put("extension", "json");
        Response response = new Response(request);

        // Create the resource
        MemoryCacheStatsResource resource = new MemoryCacheStatsResource();
        resource.setBlobStore(mbs);
        resource.setRequest(request);
        resource.setResponse(response);

        // Handle the request
        resource.handleGet();

        // Check the result
        Representation entity = response.getEntity();
        String text = entity.getText();

        assertTrue(text.contains("{\"gwcInMemoryCacheStatistics\""));
        assertTrue(text.contains("\"hitCount\""));
        assertTrue(text.contains("\"missCount\""));
        assertTrue(text.contains("\"evictionCount\""));
    }

}
