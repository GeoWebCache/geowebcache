/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009
 * @author David Vick / Boundless 2017
 *     <p>Original file MassTruncateRestlet.java
 */
package org.geowebcache.rest.controller;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.seed.MassTruncateRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.seed.TruncateBboxRequest;
import org.geowebcache.seed.TruncateLayerRequest;
import org.geowebcache.seed.TruncateOrphansRequest;
import org.geowebcache.seed.TruncateParametersRequest;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class MassTruncateController extends GWCSeedingController {
    private static Logger log = Logging.getLogger(MassTruncateController.class.getName());

    @Autowired private StorageBroker broker;

    @Autowired private TileBreeder breeder;

    @Autowired
    public MassTruncateController(ApplicationContextProvider appCtx) {
        super(appCtx);
    }

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        return new ResponseEntity<Object>(ex.toString(), ex.getStatus());
    }

    public void setStorageBroker(StorageBroker broker) {
        this.broker = broker;
    }

    public void setTileBreeder(TileBreeder breeder) {
        this.breeder = breeder;
    }

    static final Class<?>[] DEFAULT_REQUEST_TYPES = {
        TruncateLayerRequest.class,
        TruncateParametersRequest.class,
        TruncateOrphansRequest.class,
        TruncateBboxRequest.class
    };

    Class<?>[] requestTypes;

    /** Responds with a simple XML document indicating the available MassRequest types. */
    @Override
    @RequestMapping(value = "/masstruncate", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest req) {
        // Just use this for figuring out what the correct element names are
        XStream xs = configXStream(new GeoWebCacheXStream());

        // Not worth the trouble of messing with XStream for the output so just assemble some XML.

        StringBuilder sb = new StringBuilder();
        Set<String> result = new HashSet<>();
        sb.append("<massTruncateRequests href=\"").append(req.getRequestURL()).append("\">");

        for (Class<?> requestType : getRequestTypes()) {
            String alias = xs.getMapper().serializedClass(requestType);
            sb.append(" <requestType>");
            sb.append(alias);
            sb.append("</requestType>");
            if (!result.add(alias) && log.isLoggable(Level.WARNING)) {
                log.warning("Duplicate MassTruncate RestException type: " + alias);
            }
        }

        sb.append("</massTruncateRequests>");
        return new ResponseEntity<Object>(sb.toString(), HttpStatus.OK);
    }

    /** Issue a mass truncate request. */
    @RequestMapping(value = "/masstruncate", method = RequestMethod.POST)
    public ResponseEntity<?> doPost(HttpServletRequest req) throws IOException {
        String contentType = req.getContentType();

        XStream xs = configXStream(new GeoWebCacheXStream(new DomDriver()));
        StringWriter writer = new StringWriter();
        IOUtils.copy(req.getInputStream(), writer, Charset.defaultCharset());
        String reqData = writer.toString();

        Object obj = null;

        if (contentType == null || contentType.equalsIgnoreCase("text/xml")) {

            obj = xs.fromXML(reqData);
        } else if (contentType.equalsIgnoreCase("json")) {
            obj = xs.fromXML(convertJson(reqData));
        } else if (contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            obj = xs.fromXML(URLDecoder.decode(reqData, Charset.defaultCharset().name()));
        } else {
            throw new RestException(
                    "Format extension unknown or not specified: " + contentType,
                    HttpStatus.BAD_REQUEST);
        }

        MassTruncateRequest mtr = (MassTruncateRequest) obj;
        try {
            if (!mtr.doTruncate(broker, breeder)) {
                throw new RestException("Truncation failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (IllegalArgumentException e) {
            throw new RestException(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (StorageException | GeoWebCacheException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return mtr.getResponse(contentType);
    }

    @Override
    protected void handleRequest(HttpServletRequest req, HttpServletResponse resp, Object obj) {
        MassTruncateRequest mtr = (MassTruncateRequest) obj;
        try {
            if (!mtr.doTruncate(broker, breeder)) {
                throw new RestException("Truncation failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (IllegalArgumentException e) {
            throw new RestException(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (StorageException | GeoWebCacheException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    protected Class<?>[] getRequestTypes() {
        if (requestTypes == null) requestTypes = DEFAULT_REQUEST_TYPES;
        return requestTypes;
    }

    @Override
    protected XStream configXStream(XStream xs) {
        xs = super.configXStream(xs);
        xs.processAnnotations(getRequestTypes());
        return xs;
    }
}
