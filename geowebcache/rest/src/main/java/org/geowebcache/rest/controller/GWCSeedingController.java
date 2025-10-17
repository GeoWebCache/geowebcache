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
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009
 * @author David Vick / Boundless 2017
 *     <p>Original file
 *     <p>GWCSeedingRestlet.java
 */
package org.geowebcache.rest.controller;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.geowebcache.config.ContextualConfigurationProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.util.ApplicationContextProvider;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.WebApplicationContext;

public abstract class GWCSeedingController extends GWCController {

    public JSONObject myrequest;

    private WebApplicationContext context;

    @Autowired
    public GWCSeedingController(ApplicationContextProvider appCtx) {
        context = appCtx == null ? null : appCtx.getApplicationContext();
    }

    /** Handle a GET request */
    public abstract ResponseEntity<?> doGet(HttpServletRequest request) throws RestException;

    /** Handle a POST request */
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws RestException, IOException {
        String formatExtension = req.getParameter("extension");

        XStream xs = configXStream(new GeoWebCacheXStream(new DomDriver()));

        Object obj = null;

        if (formatExtension == null || formatExtension.equalsIgnoreCase("xml")) {
            obj = xs.fromXML(req.getInputStream());
        } else if (formatExtension.equalsIgnoreCase("json")) {
            obj = xs.fromXML(convertJson(req.getInputStream().toString()));
        } else {
            throw new RestException(
                    "Format extension unknown or not specified: " + formatExtension, HttpStatus.BAD_REQUEST);
        }

        handleRequest(req, resp, obj);
    }

    protected abstract void handleRequest(HttpServletRequest req, HttpServletResponse resp, Object obj);

    /**
     * Deserializing a json string is more complicated.
     *
     * <p>XStream does not natively support it. Rather, it uses a JettisonMappedXmlDriver to convert to intermediate xml
     * and then deserializes that into the desired object. At this time, there is a known issue with the Jettison driver
     * involving elements that come after an array in the json string.
     *
     * <p>http://jira.codehaus.org/browse/JETTISON-48
     *
     * <p>The code below is a hack: it treats the json string as text, then converts it to the intermediate xml and then
     * deserializes that into the SeedRequest object.
     */
    protected String convertJson(String entityText) throws IOException {
        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
        try (StringReader reader = new StringReader(entityText);
                StringWriter writer = new StringWriter()) {
            HierarchicalStreamReader hsr = driver.createReader(reader);

            new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
            return writer.toString();
        }
    }

    public void setContext(WebApplicationContext context) {
        this.context = context;
    }

    protected XStream configXStream(XStream xs) {
        return XMLConfiguration.getConfiguredXStreamWithContext(
                xs, context, ContextualConfigurationProvider.Context.REST);
    }
}
