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
 * @author Arne Kepp / The Open Planning Project 2008
 * @author David Vick / Boundless 2017
 *     <p>Original file
 *     <p>ReloadRestlet.java
 */
package org.geowebcache.rest.controller;

import com.google.common.base.Splitter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.util.ServletUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class ReloadController implements ApplicationContextAware {
    private static Logger log = Logging.getLogger(ReloadController.class.getName());

    @Autowired
    TileLayerDispatcher layerDispatcher;

    private ApplicationContext applicationContext;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        return new ResponseEntity<>(ex.toString(), ex.getStatus());
    }

    @RequestMapping(value = "/reload", method = RequestMethod.POST)
    public @ResponseBody ResponseEntity<?> doPost(
            HttpServletRequest request, InputStream inputStream, @RequestParam Map<String, String> params)
            throws GeoWebCacheException, RestException, IOException {

        String body =
                new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));

        // If Content-Type is not application/x-www-urlencoded, the form contents will still
        // be in the body.
        if (body != null && body.length() > 0) {
            Map<String, String> formMap = splitToMap(body);
            params.putAll(formMap);
        }

        if (body == null || !params.containsKey("reload_configuration")) {
            throw new RestException(
                    "Unknown or malformed request. Please try again, somtimes the form "
                            + "is not properly received. This frequently happens on the first POST "
                            + "after a restart. The POST was to "
                            + request.getRequestURI(),
                    HttpStatus.BAD_REQUEST);
        }

        StringBuilder doc = new StringBuilder();

        doc.append("<html>\n"
                + ServletUtils.gwcHtmlHeader("../", "GWC Reload")
                + "<body>\n"
                + ServletUtils.gwcHtmlLogoLink("../"));

        try {
            GeoWebCacheExtensions.reinitialize(applicationContext);
            String info = "TileLayerConfiguration reloaded. Read "
                    + layerDispatcher.getLayerCount()
                    + " layers from configuration resources.";

            log.info(info);
            doc.append("<p>" + info + "</p>");

            doc.append("<p>Note that this functionality has not been rigorously tested,"
                    + " please reload the servlet if you run into any problems."
                    + " Also note that you must truncate the tiles of any layers that have changed.</p>");

        } catch (Exception e) {
            doc.append("<p>There was a problem reloading the configuration:<br>\n"
                    + e.getMessage()
                    + "\n<br>"
                    + " If you believe this is a bug, please submit a ticket at "
                    + "<a href=\"https://geowebcache.osgeo.org\">GeoWebCache.osgeo.org</a>"
                    + "</p>");
        }

        doc.append("<p><a href=\"../demo\">Go back</a></p>\n");
        doc.append("</body></html>");

        return new ResponseEntity<>(doc.toString(), HttpStatus.OK);
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }

    private Map<String, String> splitToMap(String data) {
        if (data.contains("&")) {
            return Splitter.on("&").withKeyValueSeparator("=").split(data);
        } else {
            return Splitter.on(" ").withKeyValueSeparator("=").split(data);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
