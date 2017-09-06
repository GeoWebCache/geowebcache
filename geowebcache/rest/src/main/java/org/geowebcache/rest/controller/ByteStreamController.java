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
 * @author David Vick, Boundless, Copyright 2017
 *
 * File was reworked from
 * ByteStreamerRestlet.java
 */

package org.geowebcache.rest.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.webresources.WebResourceBundle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RestController
@RequestMapping(path="${gwc.context.suffix:}/rest")
public class ByteStreamController {
    private static Log log = LogFactory.getLog(ByteStreamController.class);

    WebResourceBundle bundle;

    private static final WebResourceBundle DEFAULT_BUNDLE = WebResourceBundle.class::getResource;

    protected URL getResource(String path) {
        if(bundle==null) {
            synchronized(this) {
                if(bundle==null) {
                    List<WebResourceBundle> result= GeoWebCacheExtensions.extensions(WebResourceBundle.class);
                    if(result.isEmpty()) {
                        bundle = DEFAULT_BUNDLE;
                    } else {
                        bundle = result.get(0);
                        if(result.size()>1) {
                            log.warn("Multiple web resource bundles present, using "+bundle.getClass().getName());
                        }
                    }
                }
            }
        }
        URL resource = bundle.apply(path);
        if(resource==null && bundle != DEFAULT_BUNDLE) {
            resource = DEFAULT_BUNDLE.apply(path);
        }
        return resource;
    }

    static final Pattern UNSAFE_RESOURCE = Pattern.compile("^/|/\\.\\./|^\\.\\./|\\.class$");

    @RequestMapping(value = "/web/{filename:.+}", method = RequestMethod.GET)
    ResponseEntity<?> doGet(HttpServletRequest request, HttpServletResponse response, @PathVariable String filename) {

        // Just to make sure we don't allow access to arbitrary resources
        if(UNSAFE_RESOURCE.matcher(filename).find()) {
            return new ResponseEntity<Object>(HttpStatus.FORBIDDEN);
        }

        URL resource = getResource(filename);
        if(resource == null) {
            return new ResponseEntity<Object>(HttpStatus.NOT_FOUND);
        }

        String[] filenameParts = filename.split("\\.");
        String extension = filenameParts[filenameParts.length - 1];

        MimeType mime = null;
        try {
            mime = MimeType.createFromExtension(extension);
        } catch (MimeException e) {
            return new ResponseEntity<Object>("Unable to create MimeType for " + extension, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // TODO write ByteArrayOutputStream ResponseEntity

        try {
            InputStream inputStream = resource.openStream();
            StreamUtils.copy(inputStream, response.getOutputStream());
            response.setContentType(MediaType.IMAGE_PNG_VALUE);
        } catch (IOException e) {
            return new ResponseEntity<Object>("Internal error", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<Object>(HttpStatus.OK);
    }
}