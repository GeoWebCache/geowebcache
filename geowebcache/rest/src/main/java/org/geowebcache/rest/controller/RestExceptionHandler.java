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
 * @author Fernando Mino / Geosolutions 2019
 */
package org.geowebcache.rest.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geowebcache.rest.exception.RestException;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/** Common Rest Exception Handler for Spring MVC Controllers. */
@ControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOGGER = Logger.getLogger(RestExceptionHandler.class.getName());

    /** Exception Handler method for {@link RestException}. Ensures the Media Type for the response is text/plain. */
    @ExceptionHandler(RestException.class)
    public void handleRestException(RestException e, HttpServletResponse response, WebRequest request, OutputStream os)
            throws IOException {
        LOGGER.log(Level.SEVERE, e.getMessage(), e);
        response.setStatus(e.getStatus().value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        StreamUtils.copy(e.getMessage(), StandardCharsets.UTF_8, os);
    }
}
