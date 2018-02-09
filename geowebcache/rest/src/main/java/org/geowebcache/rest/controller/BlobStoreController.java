package org.geowebcache.rest.controller;

import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.rest.converter.XStreamListAliasWrapper;
import org.geowebcache.rest.exception.RestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Set;

@Component
@RestController
@RequestMapping(path="${gwc.context.suffix:}/rest/blobstores")
public class BlobStoreController extends GWCController {

    @Autowired
    private XMLConfiguration xmlConfig;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<Object>(ex.toString(), headers, ex.getStatus());
    }

    @RequestMapping(method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public XStreamListAliasWrapper blobStoresGet() {
        return new XStreamListAliasWrapper(xmlConfig.getBlobStoreNames(), "blobStore", Set.class, this.getClass());
    }

    @RequestMapping(path = "/{blobStoreName}", method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public BlobStoreInfo blobStoreGet(@PathVariable String blobStoreName) {
        //TODO: Use BlobStore Aggregator
        Optional<BlobStoreInfo> blobStore = xmlConfig.getBlobStore(blobStoreName);
        if (!blobStore.isPresent()) {
            throw new RestException(String.format(
                    "Failed to get BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist.", blobStoreName),
                    HttpStatus.NOT_FOUND);
        }
        return blobStore.get();
    }

    @RequestMapping(path = "/{blobStoreName}", method = RequestMethod.PUT,
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<?> blobStorePut(@PathVariable String blobStoreName, @RequestBody BlobStoreInfo blobStore) {

        if (xmlConfig.getBlobStore(blobStoreName).isPresent()) {
            xmlConfig.modifyBlobStore(blobStore);
        } else {
            xmlConfig.addBlobStore(blobStore);
            return new ResponseEntity<Object>("", HttpStatus.CREATED);
        }
        return null;
    }

    @RequestMapping(path = "/{blobStoreName}", method = RequestMethod.DELETE)
    public void blobStoreDelete(@PathVariable String blobStoreName) {
        if (xmlConfig.getBlobStore(blobStoreName).isPresent()) {
            xmlConfig.removeBlobStore(blobStoreName);
        } else {
            throw new RestException(String.format(
                    "Failed to remove BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist.", blobStoreName),
                    HttpStatus.NOT_FOUND);
        }
    }
}
