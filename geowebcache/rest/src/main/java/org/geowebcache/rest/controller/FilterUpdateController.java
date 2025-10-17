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

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.config.XMLConfiguration;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.rest.filter.XmlFilterUpdate;
import org.geowebcache.rest.filter.ZipFilterUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class FilterUpdateController extends GWCController {

    @Autowired
    TileLayerDispatcher tld;

    @RequestMapping(value = "/filter/{filterName}/update/{updateType}", method = RequestMethod.POST)
    public ResponseEntity<?> doPost(
            HttpServletRequest request, @PathVariable String filterName, @PathVariable String updateType) {

        Iterator<TileLayer> lIter = tld.getLayerList().iterator();

        RequestFilter filter = null;

        TileLayer tl = null;

        while (lIter.hasNext() && filter == null) {
            tl = lIter.next();
            List<RequestFilter> filters = tl.getRequestFilters();
            if (filters != null) {
                Iterator<RequestFilter> fIter = filters.iterator();
                while (fIter.hasNext() && filter == null) {
                    RequestFilter cFilter = fIter.next();
                    if (cFilter.getName().equals(filterName)) {
                        filter = cFilter;
                    }
                }
            }
        }

        // Check that we have found a filter and that it's the correct type
        if (filter == null) {
            throw new RestException("No filter by the name " + filterName + " was found.", HttpStatus.BAD_REQUEST);
        }
        try {
            if (updateType.equalsIgnoreCase("xml")) {
                // Parse the input using XStream
                @SuppressWarnings("PMD.CloseResource") // managed by servlet container
                InputStream input = request.getInputStream();
                XmlFilterUpdate fu = XMLConfiguration.parseXMLFilterUpdate(input);

                fu.runUpdate(filter, tl);

            } else if (updateType.equalsIgnoreCase("zip")) {
                ZipFilterUpdate fu = new ZipFilterUpdate(request.getInputStream());

                fu.runUpdate(filter, tl);
            } else {
                throw new RestException("Unknow update type " + updateType + "\n", HttpStatus.BAD_REQUEST);
            }
        } catch (IOException e) {
            throw new RestException("Internal Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        // prepare response content type
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>("Filter update completed, no problems encountered.\n", headers, HttpStatus.OK);
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        tld = tileLayerDispatcher;
    }
}
