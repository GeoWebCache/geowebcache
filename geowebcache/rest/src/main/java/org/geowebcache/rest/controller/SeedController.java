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
 * @author David Vick / Boundless 2017
 *     <p>Original file
 *     <p>SeedRestlet.java
 */
package org.geowebcache.rest.controller;

import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultingConfiguration;
import org.geowebcache.rest.service.FormService;
import org.geowebcache.rest.service.SeedService;
import org.geowebcache.seed.TileBreeder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class SeedController {

    @Autowired TileBreeder seeder;

    @Autowired SeedService seedService;

    @Autowired FormService formService;

    @Autowired protected DefaultingConfiguration xmlConfig;

    /** GET method for querying running GWC tasks */
    @RequestMapping(
            value = "/seed.json",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> doGet(HttpServletRequest req) {
        return seedService.getRunningTasks(req);
    }

    /** GET method for querying running tasks for the provided layer */
    @RequestMapping(
            value = "/seed/{layer:.+}.json",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> doGet(@PathVariable String layer) {
        return seedService.getRunningLayerTasks(layer);
    }

    @RequestMapping(
            value = "/seed/{layer:.+}.xml",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> doGetXml(@PathVariable String layer) {
        return seedService.getRunningLayerTasksXml(layer);
    }

    /** GET method for displaying the GeoWebCache UI form. */
    @RequestMapping(value = "/seed/{layer:.+}", method = RequestMethod.GET)
    public ResponseEntity<?> doFormGet(HttpServletRequest request, @PathVariable String layer) {
        return formService.handleGet(request, layer);
    }

    /** POST method to kill [all, running, pending] tasks */
    @RequestMapping(value = "/seed", method = RequestMethod.POST)
    public ResponseEntity doPost(HttpServletRequest request) {
        String response = seedService.handleKillAllThreads(request, null);
        if (response.equalsIgnoreCase("error")) {
            return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(response, headers, HttpStatus.OK);
        }
    }

    /**
     * POST method for Seeding and Truncating
     *
     * @param params Query parameters, including urlencoded form values
     */
    @RequestMapping(value = "/seed/{layer:.+}", method = RequestMethod.POST)
    public ResponseEntity<?> doPost(
            HttpServletRequest request,
            InputStream inputStream,
            @PathVariable String layer,
            @RequestParam Map<String, String> params) {
        String body = readBody(inputStream);

        try {
            // If Content-Type is not application/x-www-urlencoded, the form contents will still
            // be in the body.
            if (body != null && body.length() > 0) {
                Map<String, String> formMap = splitToMap(URLDecoder.decode(body, "UTF-8"));
                params.putAll(formMap);
            }
            return handleFormPostInternal(layer, params);
        } catch (UnsupportedEncodingException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<Object>(
                    "Unable to parse form result.", headers, HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/seed/{layer}.json", method = RequestMethod.POST)
    public ResponseEntity<?> seedOrTruncateWithJsonPayload(
            HttpServletRequest request,
            InputStream inputStream,
            @PathVariable(name = "layer") String layerName) {

        String body = readBody(inputStream);
        String extension = "json";
        return seedService.doSeeding(request, layerName, extension, body);
    }

    @RequestMapping(value = "/seed/{layer}.xml", method = RequestMethod.POST)
    public ResponseEntity<?> seedOrTruncateWithXmlPayload(
            HttpServletRequest request,
            InputStream inputStream,
            @PathVariable(name = "layer") String layerName) {

        String body = readBody(inputStream);
        String extension = "xml";
        return seedService.doSeeding(request, layerName, extension, body);
    }

    private String readBody(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    private ResponseEntity<?> handleFormPostInternal(String layer, Map<String, String> params) {
        try {
            return formService.handleFormPost(layer, params);
        } catch (GeoWebCacheException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<Object>("error", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, String> splitToMap(String data) {
        if (data.contains("&")) {
            return Splitter.on("&").withKeyValueSeparator("=").split(data);
        } else {
            return Splitter.on(" ").withKeyValueSeparator("=").split(data);
        }
    }

    public void setXmlConfig(DefaultingConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
