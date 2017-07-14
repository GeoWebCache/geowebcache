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
 * @author Arne Kepp / The Open Planning Project 2008
 * @author David Vick / Boundless 2017
 *
 * Original file
 *
 * ReloadRestlet.java
 */

package org.geowebcache.rest.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.util.ServletUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RestController
@RequestMapping(path="${gwc.context.suffix:}/rest")
public class ReloadController {
    private static Log log = LogFactory.getLog(ReloadController.class);

    @Autowired
    TileLayerDispatcher layerDispatcher;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        return new ResponseEntity<Object>(ex.toString(), ex.getStatus());
    }

    @RequestMapping(value = "/reload", method = RequestMethod.POST)
    public @ResponseBody
    ResponseEntity<?> doPost(HttpServletRequest req, HttpServletResponse resp)
            throws GeoWebCacheException, RestException, IOException {

        if (req.getParameterMap() == null || req.getParameter("reload_configuration") == null) {
            throw new RestException(
                    "Unknown or malformed request. Please try again, somtimes the form "
                            +"is not properly received. This frequently happens on the first POST "
                            +"after a restart. The POST was to " + req.getRequestURI(),
                    HttpStatus.BAD_REQUEST);
        }

        StringBuilder doc = new StringBuilder();

        doc.append("<html>\n"+ ServletUtils.gwcHtmlHeader("../","GWC Reload") +"<body>\n" + ServletUtils.gwcHtmlLogoLink("../"));

        try {
            layerDispatcher.reInit();
            String info = "Configuration reloaded. Read "
                    + layerDispatcher.getLayerCount()
                    + " layers from configuration resources.";

            log.info(info);
            doc.append("<p>"+info+"</p>");

            doc.append("<p>Note that this functionality has not been rigorously tested,"
                    + " please reload the servlet if you run into any problems."
                    + " Also note that you must truncate the tiles of any layers that have changed.</p>");

        } catch (Exception e) {
            doc.append("<p>There was a problem reloading the configuration:<br>\n"
                    + e.getMessage()
                    + "\n<br>"
                    + " If you believe this is a bug, please submit a ticket at "
                    + "<a href=\"http://geowebcache.org\">GeoWebCache.org</a>"
                    + "</p>");
        }

        doc.append("<p><a href=\"../demo\">Go back</a></p>\n");
        doc.append("</body></html>");


        return new ResponseEntity<Object>(doc.toString(), HttpStatus.OK);
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
}
