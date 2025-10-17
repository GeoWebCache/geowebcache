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
 * <p>Copyright 2019
 */
package org.geowebcache.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.geowebcache.GeoWebCacheDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Top-level dispatcher controller */
@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}")
public class GeoWebCacheDispatcherController {

    @Value("${gwc.context.suffix:}")
    String prefix;

    @Autowired
    @Qualifier("geowebcacheDispatcher")
    private GeoWebCacheDispatcher gwcDispatcher;

    @RequestMapping(path = {"", "/home", "/service/**", "/demo/**", "/proxy/**"})
    public void handleRestApiRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        gwcDispatcher.handleRequest(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public String getContextPath() {
                        return super.getContextPath() + ("".equals(prefix) ? "" : "/" + prefix);
                    }
                },
                response);
    }
}
