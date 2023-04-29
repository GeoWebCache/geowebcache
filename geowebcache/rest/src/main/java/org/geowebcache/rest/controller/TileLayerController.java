/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009
 * @author David Vick, Boundless, Copyright 2017
 *     <p>Original file TileLayerRestlet.java
 */
package org.geowebcache.rest.controller;

import java.io.IOException;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.converter.XStreamListAliasWrapper;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class TileLayerController extends GWCController {

    @Autowired TileLayerDispatcher layerDispatcher;

    @Autowired private StorageBroker storageBroker;

    // set by spring
    public void setStorageBroker(StorageBroker storageBroker) {
        this.storageBroker = storageBroker;
    }

    /*
    DO GET
     */

    /** Get List of layers as xml */
    @RequestMapping(value = "/layers", method = RequestMethod.GET)
    public XStreamListAliasWrapper layersGet(HttpServletRequest request) {
        return new XStreamListAliasWrapper(
                layerDispatcher.getLayerNames(), "layer", Set.class, this.getClass());
    }

    /** Get layer by name and requested output {xml, json} */
    @RequestMapping(value = "/layers/{layer}", method = RequestMethod.GET)
    public TileLayer layerGet(@PathVariable String layer) {
        return findTileLayer(layer, layerDispatcher);
    }

    /*
    DO POST
     */
    @Deprecated
    @RequestMapping(value = "/layers/{layerName}", method = RequestMethod.POST)
    public ResponseEntity<?> layerPost(@RequestBody TileLayer tl, @PathVariable String layerName)
            throws GeoWebCacheException, RestException, IOException {
        tl = checkLayer(layerName, tl);

        try {
            layerDispatcher.modify(tl);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<Object>(
                    "Layer "
                            + tl.getName()
                            + " is not known by the configuration."
                            + "Maybe it was loaded from another source, or you're trying to add a new "
                            + "layer and need to do an HTTP PUT ?",
                    HttpStatus.BAD_REQUEST);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Warning", "299: Deprecated API. Use PUT instead.");
        return new ResponseEntity<>(headers, HttpStatus.OK);
    }

    /*
    DO PUT
     */
    @RequestMapping(value = "/layers/{layerName}", method = RequestMethod.PUT)
    public ResponseEntity<?> layerPut(@RequestBody TileLayer tl, @PathVariable String layerName)
            throws GeoWebCacheException, RestException, IOException {
        tl = checkLayer(layerName, tl);

        TileLayer testtl = null;
        try {
            testtl = findTileLayer(tl.getName(), layerDispatcher);
        } catch (RestException re) {
            // This is the expected behavior, it should not exist
        }

        if (testtl == null) {
            layerDispatcher.addLayer(tl);
        } else {
            layerDispatcher.modify(tl);
        }
        return new ResponseEntity<Object>("layer saved", HttpStatus.OK);
    }

    /*
    DO DELETE
     */
    @RequestMapping(value = "/layers/{layer}", method = RequestMethod.DELETE)
    public ResponseEntity<?> doDelete(HttpServletRequest req, @PathVariable String layer)
            throws GeoWebCacheException, RestException, IOException {
        String layerName = layer;
        findTileLayer(layerName, layerDispatcher);
        // TODO: refactor storage management to use a comprehensive event system;
        // centralise duplicate functionality from GeoServer gs-gwc GWC.layerRemoved
        // and CatalogConfiguration.removeLayer into GeoWebCache and use event system
        // to ensure removal and rename operations are atomic and consistent. Until this
        // is done, the following is a temporary workaround:
        //
        // delete cached tiles first in case a blob store
        // uses the configuration to perform the deletion
        StorageException storageBrokerDeleteException = null;
        try {
            storageBroker.delete(layerName);
        } catch (StorageException se) {
            // save exception for later so failure to delete
            // cached tiles does not prevent layer removal
            storageBrokerDeleteException = se;
        }
        try {
            layerDispatcher.removeLayer(layerName);
        } catch (IllegalArgumentException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        if (storageBrokerDeleteException != null) {
            // layer removal worked, so report failure to delete cached tiles
            throw new RestException(
                    "Removal of layer "
                            + layerName
                            + " was successful but deletion of cached tiles failed: "
                            + storageBrokerDeleteException.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    storageBrokerDeleteException);
        }
        return new ResponseEntity<Object>(layerName + " deleted", HttpStatus.OK);
    }

    @SuppressWarnings("PMD.EmptyControlStatement")
    protected TileLayer checkLayer(String layerName, TileLayer newLayer)
            throws RestException, IOException {
        if (!newLayer.getName().equals(layerName)) {
            throw new RestException(
                    "There is a mismatch between the name of the "
                            + " layer in the submission and the URL you specified.",
                    HttpStatus.BAD_REQUEST);
        }

        // Check that the parameter filters deserialized correctly
        if (newLayer.getParameterFilters() != null) {
            try {
                for (@SuppressWarnings({"unused"})
                ParameterFilter filter : newLayer.getParameterFilters()) {
                    // Don't actually need to do anything here.  Just iterate over the elements
                    // casting them into ParameterFilter
                }
            } catch (ClassCastException ex) {
                // By this point it has already been turned into a POJO, so the XML is no longer
                // available.  Otherwise it would be helpful to include in the error message.
                throw new RestException(
                        "parameterFilters contains an element that is not "
                                + "a known ParameterFilter",
                        HttpStatus.BAD_REQUEST);
            }
        }
        return newLayer;
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
}
