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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.blobstore.memory.CacheStatistics;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;

/**
 * {@link Resource} object used for representing Cache Statistics in an XML or JSON format
 * 
 * @author Nicola Lagomarsini Geosolutions
 */
public class MemoryCacheStatsResource extends Resource {

    /** {@link Log} used for logging operations */
    public static Log LOG = LogFactory.getLog(MemoryCacheStatsResource.class);

    /** BlobStore used for getting statistics */
    private BlobStore store;

    /**
     * Setter for the BlobStore
     * 
     * @param store
     */
    public void setBlobStore(BlobStore store) {
        this.store = store;
    }

    @Override
    public boolean allowGet() {
        return true;
    }

    @Override
    public boolean allowPut() {
        return false;
    }

    @Override
    public boolean allowPost() {
        return false;
    }

    @Override
    public boolean allowDelete() {
        return false;
    }

    @Override
    public void handleGet() {
        final Request request = getRequest();
        final Response response = getResponse();
        final String formatExtension = (String) request.getAttributes().get("extension");

        // Getting the store statistics if it is a MemoryCacheBlobStore
        Representation representation;
        if (store != null && store instanceof MemoryBlobStore) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Memory Blobstore found, now getting statistics");
            }
            // Getting statistics
            MemoryBlobStore memoryStore = (MemoryBlobStore) store;
            CacheStatistics stats = memoryStore.getCacheStatistics();
            CacheStatistics statistics = new CacheStatistics(stats);
            // create a new Representation object
            if ("json".equals(formatExtension)) {
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Statistics requested in JSON format");
                    }
                    representation = getJsonRepresentation(statistics);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else if ("xml".equals(formatExtension)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Statistics requested in XML format");
                }
                representation = getXmlRepresentation(statistics);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Statistics requested in a bad format");
                }
                response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST,
                        "Unknown or missing format extension : " + formatExtension);
                return;
            }
            // Update the response
            response.setEntity(representation);
            response.setStatus(Status.SUCCESS_OK);
        } else {
            response.setStatus(Status.CLIENT_ERROR_NOT_FOUND,
                    "No statistics available for the current BlobStore: " + store.getClass());
        }
    }

    /**
     * Private method for retunring a JSON representation of the Statistics
     * 
     * @param stats
     * @return a {@link JsonRepresentation} object
     * @throws JSONException
     */
    private JsonRepresentation getJsonRepresentation(CacheStatistics stats) throws JSONException {
        XStream xs = getConfiguredXStream(new GeoWebCacheXStream(new JsonHierarchicalStreamDriver()));
        JSONObject obj = new JSONObject(xs.toXML(stats));
        JsonRepresentation rep = new JsonRepresentation(obj);
        return rep;
    }

    /**
     * Private method for retunring an XML representation of the Statistics
     * 
     * @param stats
     * @return a {@link StringRepresentation} object
     * @throws JSONException
     */
    private Representation getXmlRepresentation(CacheStatistics stats) {
        XStream xStream = getConfiguredXStream(new GeoWebCacheXStream());
        String xml = xStream.toXML(stats);
        return new StringRepresentation(xml, MediaType.TEXT_XML);
    }

    /**
     * This method adds to the input {@link XStream} an alias for the CacheStatistics
     * 
     * @param xs
     * @return an updated XStream
     */
    public static XStream getConfiguredXStream(XStream xs) {
        xs.setMode(XStream.NO_REFERENCES);
        xs.alias("gwcInMemoryCacheStatistics", CacheStatistics.class);
        return xs;
    }
}
