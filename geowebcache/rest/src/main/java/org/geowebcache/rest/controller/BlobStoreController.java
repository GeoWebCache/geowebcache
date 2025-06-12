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
 * @author Torben Barsballe (Boundless), 2018
 */
package org.geowebcache.rest.controller;

import java.util.List;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.rest.converter.XStreamListAliasWrapper;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.storage.BlobStoreAggregator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest/blobstores")
public class BlobStoreController extends GWCController {

    @Autowired
    private BlobStoreAggregator blobStores;

    @RequestMapping(
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public XStreamListAliasWrapper blobStoresGet() {
        return new XStreamListAliasWrapper(blobStores.getBlobStoreNames(), "blobStore", List.class, this.getClass());
    }

    @RequestMapping(
            path = "/{blobStoreName}",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public BlobStoreInfo blobStoreGet(@PathVariable String blobStoreName) {
        try {
            return blobStores.getBlobStore(blobStoreName);
        } catch (GeoWebCacheException e) {
            throw new RestException(
                    "Failed to get BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist."
                            .formatted(blobStoreName),
                    HttpStatus.NOT_FOUND,
                    e);
        }
    }

    @RequestMapping(
            path = "/{blobStoreName}",
            method = RequestMethod.PUT,
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<?> blobStorePut(@PathVariable String blobStoreName, @RequestBody BlobStoreInfo blobStore) {

        if (blobStores.blobStoreExists(blobStoreName)) {
            blobStores.modifyBlobStore(blobStore);
        } else {
            blobStores.addBlobStore(blobStore);
            return new ResponseEntity<>("", HttpStatus.CREATED);
        }
        return null;
    }

    @RequestMapping(path = "/{blobStoreName}", method = RequestMethod.DELETE)
    public void blobStoreDelete(@PathVariable String blobStoreName) {
        if (blobStores.blobStoreExists(blobStoreName)) {
            blobStores.removeBlobStore(blobStoreName);
        } else {
            throw new RestException(
                    "Failed to remove BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist."
                            .formatted(blobStoreName),
                    HttpStatus.NOT_FOUND);
        }
    }
}
