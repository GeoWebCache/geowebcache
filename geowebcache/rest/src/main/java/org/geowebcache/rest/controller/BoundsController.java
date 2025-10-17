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
 * @author David Vick, Boundless, Copyright 2017
 */
package org.geowebcache.rest.controller;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.HttpServletRequest;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.exception.RestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class BoundsController extends GWCController {
    @Autowired
    TileLayerDispatcher tld;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>(ex.toString(), headers, ex.getStatus());
    }

    @RequestMapping(value = "/bounds/{layer}/{srs}/{type}", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(
            HttpServletRequest request,
            @PathVariable String layer,
            @PathVariable String srs,
            @PathVariable String type) {
        TileLayer tl = findTileLayer(layer, tld);
        if (tl == null) {
            throw new RestException(layer + " is not known", HttpStatus.NOT_FOUND);
        }

        GridSubset grid = tl.getGridSubset(srs);

        if (grid == null) {
            throw new RestException(layer + " does not support " + srs, HttpStatus.NOT_FOUND);
        }

        StringBuilder str = new StringBuilder();
        long[][] bounds = grid.getCoverages();

        if (type.equalsIgnoreCase("java")) {
            str.append("{");
            for (int i = 0; i < bounds.length; i++) {
                str.append("{");

                for (int j = 0; j < bounds[i].length; j++) {
                    str.append(bounds[i][j]);

                    if (j + 1 < bounds[i].length) {
                        str.append(", ");
                    }
                }

                str.append("}");

                if (i + 1 < bounds.length) {
                    str.append(", ");
                }
            }
            str.append("}");

            return new ResponseEntity<>(str.toString(), HttpStatus.OK);
        } else {
            throw new RestException("Unknown or missing format extension : " + type, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Sets the tileLayerDispatcher, for use when constructing this controller manually (mainly in test cases)
     *
     * @param tileLayerDispatcher The {@link TileLayerDispatcher}
     */
    @VisibleForTesting
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        tld = tileLayerDispatcher;
    }
}
