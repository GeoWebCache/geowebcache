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
package org.geowebcache;

import java.io.IOException;
import java.io.Serial;

/**
 * An IOException that means a {@link ServiceStrategy#getDestination(javax.servlet.http.HttpServletResponse)
 * ServiceStrategy's destination} IO operation has been abruptly interrupted while writing a response.
 *
 * <p>This exception serves as an indicator to the dispatching system that there's no need to report the exception back
 * to the client.
 *
 * @author Gabriel Roldan (TOPP)
 * @version $Id$
 * @since 1.6.x
 */
public final class ClientStreamAbortedException extends IOException {

    @Serial
    private static final long serialVersionUID = -812677957232110980L;

    public ClientStreamAbortedException() {
        super();
    }

    public ClientStreamAbortedException(String message) {
        super(message);
    }

    public ClientStreamAbortedException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }

    public ClientStreamAbortedException(Throwable cause) {
        super();
        initCause(cause);
    }
}
