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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.filter.parameters;

import java.io.Serial;
import org.geowebcache.GeoWebCacheException;

/** A problem occurred while filtering a parameter. */
public class ParameterException extends GeoWebCacheException {

    @Serial
    private static final long serialVersionUID = 2471474123508934754L;

    private final int httpCode;
    private final String exceptionCode;
    private final String locator;

    public ParameterException(String msg) {
        super(msg);
        httpCode = 500;
        exceptionCode = "NoApplicableCode";
        locator = "";
    }

    public ParameterException(int httpCode, String exceptionCode, String locator, String msg) {
        super(msg);
        this.httpCode = httpCode;
        this.exceptionCode = exceptionCode;
        this.locator = locator;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public String getExceptionCode() {
        return exceptionCode;
    }

    public String getLocator() {
        return locator;
    }
}
