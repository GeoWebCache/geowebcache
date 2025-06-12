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
 * @author John Schulz (Boundless), 2018
 */
package org.geowebcache.rest.controller;

import java.util.Set;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.rest.converter.XStreamListAliasWrapper;
import org.geowebcache.rest.exception.RestException;
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
@RequestMapping(path = "${gwc.context.suffix:}/rest/gridsets")
public class GridSetController extends GWCController {

    @Autowired
    GridSetBroker broker;

    @RequestMapping(
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public XStreamListAliasWrapper gridSetsGet() {
        return new XStreamListAliasWrapper(broker.getGridSetNames(), "gridSet", Set.class, this.getClass());
    }

    @RequestMapping(
            path = "/{gridSetName}",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public GridSet gridSetGet(@PathVariable String gridSetName) {
        if (broker.get(gridSetName) != null) {
            return broker.get(gridSetName);
        } else {
            throw new RestException(
                    "Failed to get GridSet. A GridSet with name \"%s\" does not exist.".formatted(gridSetName),
                    HttpStatus.NOT_FOUND);
        }
    }

    @RequestMapping(
            path = "/{gridSetName}",
            method = RequestMethod.PUT,
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<?> gridSetPut(@PathVariable String gridSetName, @RequestBody GridSet gridSet) {

        if (broker.get(gridSetName) != null) {
            broker.put(gridSet);
        } else {
            broker.addGridSet(gridSet);
            return new ResponseEntity<>("", HttpStatus.CREATED);
        }
        return null;
    }

    @RequestMapping(path = "/{gridSetName}", method = RequestMethod.DELETE)
    public void gridSetDelete(@PathVariable String gridSetName) {
        if (broker.get(gridSetName) != null) {
            broker.removeGridSet(gridSetName);
        } else {
            throw new RestException(
                    "Failed to remove GridSet. A GridSet with name \"%s\" does not exist.".formatted(gridSetName),
                    HttpStatus.NOT_FOUND);
        }
    }
}
