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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.filter.request;

import jakarta.servlet.http.HttpServletResponse;
import java.io.Serial;
import org.geowebcache.io.Resource;

public class RequestFilterException extends Exception {
    @Serial
    private static final long serialVersionUID = -6790605482047043946L;

    RequestFilter reqFilter;

    int httpCode;

    String contentType;

    public RequestFilterException(RequestFilter reqFilter, int httpCode, String contentType) {
        this.reqFilter = reqFilter;
        this.httpCode = httpCode;
        this.contentType = contentType;
    }

    public void setHttpInfoHeader(HttpServletResponse resp) {
        resp.setHeader("gwc-request-filter", reqFilter.getName());
    }

    public int getResponseCode() {
        return httpCode;
    }

    public String getContentType() {
        return contentType;
    }

    public Resource getResponse() {
        return null;
    }
}
