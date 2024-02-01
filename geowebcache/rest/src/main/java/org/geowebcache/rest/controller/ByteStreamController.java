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
 * @author David Vick, Boundless, Copyright 2017
 *     <p>File was reworked from ByteStreamerRestlet.java
 */
package org.geowebcache.rest.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.webresources.WebResourceBundle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class ByteStreamController {
    private static Logger log = Logging.getLogger(ByteStreamController.class.getName());

    volatile WebResourceBundle bundle;

    private static final WebResourceBundle DEFAULT_BUNDLE = WebResourceBundle.class::getResource;

    protected URL getResource(String path) {
        if (bundle == null) {
            synchronized (this) {
                if (bundle == null) {
                    List<WebResourceBundle> result =
                            GeoWebCacheExtensions.extensions(WebResourceBundle.class);
                    if (result.isEmpty()) {
                        bundle = DEFAULT_BUNDLE;
                    } else {
                        if (result.size() > 1) {
                            log.warning(
                                    "Multiple web resource bundles present, using "
                                            + result.get(0).getClass().getName());
                        }
                        bundle = result.get(0);
                    }
                }
            }
        }
        URL resource = bundle.apply(path);
        if (resource == null && bundle != DEFAULT_BUNDLE) {
            resource = DEFAULT_BUNDLE.apply(path);
        }
        return resource;
    }

    static final Pattern UNSAFE_RESOURCE = Pattern.compile("^/|/\\.\\./|^\\.\\./|\\.class$");

    // "gwc/rest/web/openlayers3/ol.js" -> openlayers3/ol.js
    // "/rest/web/openlayers3/ol.js" -> openlayers3/ol.js
    String getFileName(HttpServletRequest request) throws IOException {
        String path =
                URLDecoder.decode(request.getRequestURI(), "UTF-8")
                        .substring(request.getContextPath().length())
                        .replace(File.separatorChar, '/');
        int index = path.indexOf("/rest/web/");
        return index < 0 ? null : path.substring(index + "/rest/web/".length());
    }

    @RequestMapping(value = "/web/**", method = RequestMethod.GET)
    ResponseEntity<?> doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        final String filename = getFileName(request);
        if (filename == null || filename.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // Just to make sure we don't allow access to arbitrary resources
        if (UNSAFE_RESOURCE.matcher(filename).find()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        URL resource = getResource(filename);
        if (resource == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        String[] filenameParts = filename.split("\\.");
        String extension = filenameParts[filenameParts.length - 1];

        MimeType mime = null;
        try {
            mime = MimeType.createFromExtension(extension);
        } catch (MimeException e) {
            return new ResponseEntity<Object>(
                    "Unable to create MimeType for " + extension, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // TODO write ByteArrayOutputStream ResponseEntity

        response.setContentType(mime.getFormat());
        try (InputStream inputStream = resource.openStream();
                ServletOutputStream outputStream = response.getOutputStream(); ) {
            StreamUtils.copy(inputStream, outputStream);
        } catch (IOException e) {
            return new ResponseEntity<Object>("Internal error", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }
}
