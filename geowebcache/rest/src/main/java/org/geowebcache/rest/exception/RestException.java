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
 * @author David Winslow / The Open Planning Project 2008
 * @author David Vick, Boundless, Copyright 2017
 *     <p>Original file RestletException.java
 */
package org.geowebcache.rest.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * An exception with an associated {@link HttpStatus}. Used to wrap other exceptions so they can be caught and
 * appropriately handled by an {@link org.springframework.web.bind.annotation.ExceptionHandler}
 */
@SuppressWarnings("OverrideThrowableToString")
public class RestException extends RuntimeException {
    /** serialVersionUID */
    @Serial
    private static final long serialVersionUID = 5762645820684796082L;

    private final HttpStatus status;

    public RestException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public RestException(String message, HttpStatus status, Throwable t) {
        super(message, t);
        this.status = status;
    }

    /**
     * Get the {@link HttpStatus} to be used in the error response
     *
     * @return the HTTP status
     */
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getName());
        if (status != null) {
            builder.append(" ");
            builder.append(status.value());
            builder.append(" ");
            builder.append(status.name());
        }
        String message = getLocalizedMessage();
        if (message != null) {
            builder.append(": ");
            builder.append(message);
        }
        return builder.toString();
    }
}
