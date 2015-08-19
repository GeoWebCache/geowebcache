/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009  
 */
package org.geowebcache.rest.seed;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.Configuration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.MassTruncateRequest;
import org.geowebcache.seed.TruncateLayerRequest;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

import com.thoughtworks.xstream.XStream;

public class MassTruncateRestlet extends GWCSeedingRestlet {
    private static Log log = LogFactory.getLog(MassTruncateRestlet.class);

    private StorageBroker broker;
    private Configuration config;
    
    static final Class<?>[] DEFAULT_REQUEST_TYPES = {TruncateLayerRequest.class};

    Class<?>[] requestTypes;
    
    /**
     * Responds with a simple XML document indicating the available MassRequest types.
     */
    public void doGet(Request req, Response resp) throws RestletException {
        Representation rep = null;
        
        // Just use this for figuring out what the correct element names are
        XStream xs = configXStream(new GeoWebCacheXStream());
        
        // Not worth the trouble of messing with XStream for the output so just assemble some XML.
        
        StringBuilder sb = new StringBuilder();
        Set<String> result = new HashSet<String>();
        sb.append("<massTruncateRequests href=\"")
            .append(req.getResourceRef().toString(false, false)).append("\">");
        
        for(Class<?> requestType: getRequestTypes()) {
            String alias = xs.getMapper().serializedClass(requestType);
            sb.append(" <requestType>");
            sb.append(alias);
            sb.append("</requestType>");
            if(!result.add(alias) && log.isWarnEnabled()) {
                log.warn("Duplicate MassTruncate Request type: "+alias);
            }
        }
        
        sb.append("</massTruncateRequests>");
        
        rep = new StringRepresentation(sb.toString());
        rep.setMediaType(MediaType.APPLICATION_XML);
        resp.setEntity(rep);
    }

    protected void handleRequest(Request req, Response resp, Object obj) {
        MassTruncateRequest mtr = (MassTruncateRequest) obj;
        try {
            if(!mtr.doTruncate(broker, config)) {
                throw new RestletException("Truncation failed", Status.SERVER_ERROR_INTERNAL);
            }
        } catch (IllegalArgumentException e) {
            throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        } catch (StorageException e) {
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }
    }

    public void setStorageBroker(StorageBroker broker) {
        this.broker = broker;
    }
    
    public void setConfiguration(Configuration config) {
        this.config = config;
    }
    
    public Configuration getConfiguration() {
        if(this.config==null) return this.xmlConfig;
        return this.config;
    }

    protected Class<?>[] getRequestTypes() {
        if(requestTypes==null) requestTypes=DEFAULT_REQUEST_TYPES;
        return requestTypes;
    }
    
    @Override
    protected XStream configXStream(XStream xs) {
        xs = super.configXStream(xs);
        xs.processAnnotations(getRequestTypes());
        return xs;
    }

    public void setRequestTypes(Set<Class<?>> requestTypes) {
        this.requestTypes = requestTypes.toArray(new Class<?>[requestTypes.size()]);
    }
    
    
}
