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
 * @author David Vick / Boundless 2017
 *     <p>Original files MemoryCacheStatsFinder.java MemoryCacheStatsResource.java
 */
package org.geowebcache.rest.controller;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.ContextualConfigurationProvider.Context;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.DefaultStorageBroker;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.blobstore.memory.CacheStatistics;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.geowebcache.util.ApplicationContextProvider;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class MemoryCacheController {
    /** {@link Log} used for logging the exceptions */
    public static Logger log = Logging.getLogger(MemoryCacheController.class.getName());

    /** Store associated to the StorageBroker to use */
    @Autowired
    StorageBroker broker;

    private WebApplicationContext context;

    @Autowired
    public MemoryCacheController(ApplicationContextProvider appCtx) {
        context = appCtx == null ? null : appCtx.getApplicationContext();
    }

    /** BlobStore used for getting statistics */
    private BlobStore store;

    // set by spring
    public void setStorageBroker(StorageBroker broker) {
        this.broker = broker;
    }

    public void setContext(WebApplicationContext context) {
        this.context = context;
    }

    /** Setter for the BlobStore */
    public void setBlobStore(BlobStore store) {
        this.store = store;
    }

    @RequestMapping(value = "/statistics", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest request) {
        ResponseEntity<?> entity;

        if (log.isLoggable(Level.FINE)) {
            log.fine("Getting BlobStore from the storage broker");
        }

        // Getting the BlobStore if present
        if (broker instanceof DefaultStorageBroker storageBroker) {
            store = storageBroker.getBlobStore();
        }

        if (store != null && store instanceof MemoryBlobStore memoryStore) {
            if (log.isLoggable(Level.FINE)) {
                log.fine("Memory Blobstore found, now getting statistics");
            }
            CacheStatistics stats = memoryStore.getCacheStatistics();
            CacheStatistics statistics = new CacheStatistics(stats);

            if (request.getPathInfo().contains("json")) {
                try {
                    entity = getJsonRepresentation(statistics);
                } catch (JSONException e) {
                    entity = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                entity = getXmlRepresentation(statistics);
            }
        } else {
            entity = new ResponseEntity<>(
                    "No statistics available for the current BlobStore: " + (store != null ? store.getClass() : null),
                    HttpStatus.NOT_FOUND);
        }
        return entity;
    }

    /**
     * Private method for retunring a JSON representation of the Statistics
     *
     * @return a {@link ResponseEntity} object
     */
    private ResponseEntity<?> getJsonRepresentation(CacheStatistics stats) throws JSONException {
        XStream xs = XMLConfiguration.getConfiguredXStreamWithContext(
                new GeoWebCacheXStream(new JsonHierarchicalStreamDriver()), context, Context.REST);
        JSONObject obj = new JSONObject(xs.toXML(stats));
        return new ResponseEntity<>(obj.toString(), HttpStatus.OK);
    }

    /**
     * Private method for retunring an XML representation of the Statistics
     *
     * @return a {@link ResponseEntity} object
     */
    private ResponseEntity<?> getXmlRepresentation(CacheStatistics stats) {
        XStream xStream = getConfiguredXStream(new GeoWebCacheXStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xStream.toXML(stats);

        return new ResponseEntity<>(xmlText, HttpStatus.OK);
    }

    /**
     * This method adds to the input {@link XStream} an alias for the CacheStatistics
     *
     * @return an updated XStream
     */
    public static XStream getConfiguredXStream(XStream xs) {
        xs.setMode(XStream.NO_REFERENCES);
        xs.alias("gwcInMemoryCacheStatistics", CacheStatistics.class);
        return xs;
    }
}
