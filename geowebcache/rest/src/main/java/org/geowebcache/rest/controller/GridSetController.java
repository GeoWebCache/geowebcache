package org.geowebcache.rest.controller;

import org.geowebcache.GeoWebCache;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.rest.config.XMLConfiguration;
import org.geowebcache.rest.controller.GWCController;
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
@RequestMapping(path="${gwc.context.suffix:}/rest/gridsets")
public class GridSetController extends GWCController {

    @Autowired
    //change to GridSetBroker after changes to gridset broker are added
    GridSetBroker broker;
//    GridSetConfiguration broker;
    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<Object>(ex.toString(), headers, ex.getStatus());
    }

    @RequestMapping(method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public XStreamListAliasWrapper gridSetsGet() {
        return new XStreamListAliasWrapper(broker.getGridSetNames(), "gridSet", Set.class, this.getClass());
    }

    @RequestMapping(path = "/{gridSetName}", method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public GridSet gridSetGet(@PathVariable String gridSetName) {
        if (broker.get(gridSetName) != null){
            return broker.get(gridSetName);
        } else{
            throw new RestException(String.format(
                    "Failed to get GridSet. A GridSet with name \"%s\" does not exist.", gridSetName),
                    HttpStatus.NOT_FOUND);
            }
        }


    @RequestMapping(path = "/{gridSetName}", method = RequestMethod.PUT,
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<?> gridSetPut(@PathVariable String gridSetName, @RequestBody GridSet gridSet) {

        if (broker.get(gridSetName) != null) {
            broker.put(gridSet);
        } else {
            broker.addGridSet(gridSet);
            return new ResponseEntity<Object>("", HttpStatus.CREATED);
        }
        return null;
    }

    @RequestMapping(path = "/{gridSetName}", method = RequestMethod.DELETE)
    public void gridSetDelete(@PathVariable String gridSetName) {
        if (broker.get(gridSetName) != null) {
            broker.removeGridSet(gridSetName);
        } else {
            throw new RestException(String.format(
                    "Failed to remove GridSet. A GridSet with name \"%s\" does not exist.", gridSetName),
                    HttpStatus.NOT_FOUND);
        }
    }
}
