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
package org.geowebcache.service;

import java.io.Serial;

/**
 * An exception thrown by a service to report back an http error code.
 *
 * <p>Instances of this exception are recognized by the dispatcher. The {@link #getErrorCode()} is used to set
 *
 * @author Justin Deoliveira, The Open Planning Project
 */
public class HttpErrorCodeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -1900532970945396474L;

    /** the error code */
    final int errorCode;

    public HttpErrorCodeException(int errorCode) {
        super();
        this.errorCode = errorCode;
    }

    public HttpErrorCodeException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public HttpErrorCodeException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public HttpErrorCodeException(int errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
