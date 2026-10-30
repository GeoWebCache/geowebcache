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
 * <p>Copyright 2026
 */
package org.geowebcache.rest.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.storage.memorycache.MemoryCacheInfo;
import org.geowebcache.storage.memorycache.UnresolvedKeyValueStoreInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Reads and replaces the {@code <memoryCache>} settings of {@code geowebcache.xml}. */
@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest/memorycache")
public class MemoryCacheController {

    private final XMLConfiguration config;

    /** Serves the settings of {@code config}. */
    @Autowired
    public MemoryCacheController(XMLConfiguration config) {
        this.config = config;
    }

    /** The saved settings, an absent value meaning its default. */
    @RequestMapping(
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public MemoryCacheInfo memoryCacheGet() {
        MemoryCacheInfo memoryCache = config.getMemoryCache();
        return memoryCache == null ? new MemoryCacheInfo() : memoryCache;
    }

    /**
     * Replaces the settings, an absent value taking its default. A change rebuilds the cache, which starts empty.
     *
     * @throws RestException 400 when the cache could not be built from them
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_XML_VALUE})
    public void memoryCachePut(@RequestBody MemoryCacheInfo memoryCache) throws IOException {
        // saved as is, the cache would only log its failure and drop every tile
        if (memoryCache.getTimeToLive() < 1) {
            String message = "Time to live must be at least 1 second, got " + memoryCache.getTimeToLive();
            throw new RestException(message, HttpStatus.BAD_REQUEST);
        }
        if (memoryCache.getBackend() instanceof UnresolvedKeyValueStoreInfo unresolved) {
            String message = "Backend " + unresolved.getAlias() + " is not installed";
            throw new RestException(message, HttpStatus.BAD_REQUEST);
        }
        // the same settings must not rebuild, and empty, the cache
        if (!memoryCache.equals(config.getMemoryCache())) config.setMemoryCache(memoryCache);
    }

    /** Answers with the status and message of the exception, as plain text. */
    @ExceptionHandler(RestException.class)
    public void handleRestException(RestException e, HttpServletResponse response, OutputStream os) throws IOException {
        // local, as in GeoServer its catch-all REST advice can run before the GWC one and answer 500
        response.setStatus(e.getStatus().value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        StreamUtils.copy(e.getMessage(), StandardCharsets.UTF_8, os);
    }
}
