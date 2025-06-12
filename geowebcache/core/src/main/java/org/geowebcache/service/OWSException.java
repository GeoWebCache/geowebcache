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
package org.geowebcache.service;

import java.io.Serial;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.owasp.encoder.Encode;

public class OWSException extends Exception {
    @Serial
    private static final long serialVersionUID = -8024005353689857211L;

    int httpCode;

    String exceptionCode;

    String locator;

    String exceptionText;

    public OWSException(int httpCode, String exceptionCode, String locator, String exceptionText) {
        this.httpCode = httpCode;
        this.exceptionCode = Encode.forXml(exceptionCode);
        this.locator = Encode.forXml(locator);
        this.exceptionText = Encode.forXml(exceptionText);
    }

    public int getResponseCode() {
        return httpCode;
    }

    public String getContentType() {
        return "text/xml";
    }

    public Resource getResponse() {
        return new ByteArrayResource(this.toString().getBytes());
    }

    @Override
    public String getMessage() {
        return exceptionText;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        str.append("<ExceptionReport version=\"1.1.0\" xmlns=\"http://www.opengis.net/ows/1.1\"\n");
        str.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        str.append(
                "  xsi:schemaLocation=\"http://www.opengis.net/ows/1.1 http://geowebcache.org/schema/ows/1.1.0/owsExceptionReport.xsd\">\n");
        if (locator != null) {
            str.append("  <Exception exceptionCode=\"" + exceptionCode + "\" locator=\"" + locator + "\">\n");
        } else {
            str.append("  <Exception exceptionCode=\"" + exceptionCode + "\">\n");
        }

        str.append("    <ExceptionText>" + exceptionText + "</ExceptionText>\n");
        str.append("  </Exception>\n");
        str.append("</ExceptionReport>\n");

        return str.toString();
    }

    /** Returns the OWS exception <code>code</code> attribute */
    public String getExceptionCode() {
        return exceptionCode;
    }

    /** Returns the OWS exception <code>locator</code> attribute */
    public String getLocator() {
        return locator;
    }
}
