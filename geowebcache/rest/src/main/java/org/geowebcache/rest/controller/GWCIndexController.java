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
 *     <p>Original file IndexRestlet.java
 */
package org.geowebcache.rest.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class GWCIndexController {

    @RequestMapping(method = RequestMethod.GET, produces = "text/html")
    public ResponseEntity<?> handleRequestInternal(HttpServletRequest request) {
        String idx = "<html><body>\n"
                + "<a id=\"logo\" href=\""
                + request.getRequestURI().toString()
                + "\">"
                + "<img src=\""
                + request.getRequestURI()
                + "/web/geowebcache_logo.png\" alt=\"\" height=\"100\" width=\"353\" border=\"0\"/></a>\n"
                + "<h3>Resources available from here:</h3>"
                + "<ul>"
                + "<li><h4><a href=\""
                + request.getRequestURI()
                + "/layers/\">layers</a></h4>"
                + "Lets you see the configured layers. You can also view a specific layer "
                + " by appending the name of the layer to the URL, DELETE an existing layer "
                + " or POST a new one. Note that the latter operations only make sense when GeoWebCache"
                + " has been configured through geowebcache.xml. You can POST either XML or JSON."
                + "</li>\n"
                + "<li><h4>seed</h4>"
                + ""
                + "</li>\n"
                + "</ul>"
                + "</body></html>";
        return new ResponseEntity<>(idx, HttpStatus.OK);
    }
}
