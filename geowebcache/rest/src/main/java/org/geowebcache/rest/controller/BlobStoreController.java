package org.geowebcache.rest.controller;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.rest.converter.XStreamListAliasWrapper;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.storage.BlobStoreAggregator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RestController
@RequestMapping(path="${gwc.context.suffix:}/rest/blobstores")
public class BlobStoreController extends GWCController {

    @Autowired
    private BlobStoreAggregator blobStores;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<Object>(ex.toString(), headers, ex.getStatus());
    }

    @RequestMapping(method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public XStreamListAliasWrapper blobStoresGet() {
        return new XStreamListAliasWrapper(blobStores.getBlobStoreNames(), "blobStore", List.class, this.getClass());
    }

    @RequestMapping(path = "/{blobStoreName}", method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public BlobStoreInfo blobStoreGet(@PathVariable String blobStoreName) {
        try {
            return blobStores.getBlobStore(blobStoreName);
        } catch (GeoWebCacheException e) {
            throw new RestException(String.format(
                    "Failed to get BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist.", blobStoreName),
                    HttpStatus.NOT_FOUND, e);
        }
    }

    @RequestMapping(path = "/{blobStoreName}", method = RequestMethod.PUT,
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<?> blobStorePut(@PathVariable String blobStoreName, @RequestBody BlobStoreInfo blobStore) {

        if (blobStores.blobStoreExists(blobStoreName)) {
            blobStores.modifyBlobStore(blobStore);
        } else {
            blobStores.addBlobStore(blobStore);
            return new ResponseEntity<Object>("", HttpStatus.CREATED);
        }
        return null;
    }

    @RequestMapping(path = "/{blobStoreName}", method = RequestMethod.DELETE)
    public void blobStoreDelete(@PathVariable String blobStoreName) {
        if (blobStores.blobStoreExists(blobStoreName)) {
            blobStores.removeBlobStore(blobStoreName);
        } else {
            throw new RestException(String.format(
                    "Failed to remove BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist.", blobStoreName),
                    HttpStatus.NOT_FOUND);
        }
    }
}
