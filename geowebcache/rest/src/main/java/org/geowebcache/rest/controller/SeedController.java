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
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009
 * @author David Vick / Boundless 2017
 *
 * Original file
 *
 * SeedRestlet.java
 */

package org.geowebcache.rest.controller;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.rest.service.FormService;
import org.geowebcache.rest.service.SeedService;
import org.geowebcache.seed.TileBreeder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Component
@RestController
@RequestMapping(path="${gwc.context.suffix:}/rest")
public class SeedController {

    @Autowired
    TileBreeder seeder;

    @Autowired
    SeedService seedService;

    @Autowired
    FormService formService;

    @Autowired
    protected XMLConfiguration xmlConfig;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        return new ResponseEntity<Object>(ex.toString(), ex.getStatus());
    }

    /**
     * GET method for querying running GWC tasks
     * @param req
     * @return
     */
    @RequestMapping(value = "/seed.json", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest req) {
        return seedService.getRunningTasks(req);
    }

    /**
     * GET method for querying running tasks for the provided layer
     * @param req
     * @param layer
     * @return
     */
    @RequestMapping(value = "/seed/{layer}.json", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest req, @PathVariable String layer) {
        return seedService.getRunningLayerTasks(req, layer);
    }

    /**
     * GET method for displaying the GeoWebCache UI form.
     * @param request
     * @param layer
     * @return
     */
    @RequestMapping(value = "/seed/{layer}", method = RequestMethod.GET)
    public ResponseEntity<?> doFormGet(HttpServletRequest request, @PathVariable String layer) {
        return formService.handleGet(request, layer);
    }

    /**
     * POST method to kill [all, running, pending] tasks
     * @param request
     * @return
     */
    @RequestMapping(value = "/seed", method = RequestMethod.POST)
    public ResponseEntity doPost(HttpServletRequest request) {
        String response = seedService.handleKillAllThreads(request, null);
        if (response.equalsIgnoreCase("error")) {
            return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            return new ResponseEntity<String>(response, HttpStatus.OK);
        }
    }

    /**
     * POST method for Seeding and Truncating
     * @param request
     * @param layer
     * @return
     */
    @RequestMapping(value = "/seed/{layer:.+}", method = RequestMethod.POST)
    public ResponseEntity<?> doPost(HttpServletRequest request,
                                    @PathVariable String layer, @RequestBody String body) {
        if (layer.indexOf(".") == -1) {
            try {
                return formService.handleFormPost(request, layer, body);
            } catch (GeoWebCacheException e) {
                return new ResponseEntity<Object>("error", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } else {
            String extension = layer.substring(layer.indexOf(".") +1);
            String layerName = layer.substring(0, layer.indexOf("."));
            return seedService.doSeeding(request, layerName, extension, body);
        }
    }

}
