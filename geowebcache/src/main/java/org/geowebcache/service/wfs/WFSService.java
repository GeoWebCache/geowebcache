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
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.service.wfs;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorWFS;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.service.Service;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.ServletUtils;

public class WFSService extends Service {
    public static final String SERVICE_WFS = "wfs";
    
    private static Log log = LogFactory.getLog(org.geowebcache.service.wfs.WFSService.class);

    private int readTimeout = 10 * 60 * 1000;
    
    private String urlString;
    
    public WFSService(String urlString) {
        super(SERVICE_WFS);
        this.urlString = urlString;
    }
    
    public ConveyorWFS getConveyor(HttpServletRequest request,
            HttpServletResponse response, StorageBroker sb)
            throws GeoWebCacheException {
        String parameters = null;

        byte[] queryBlob = null;
        if (request.getContentLength() > 0) {
            try {
                queryBlob = ServletUtils.readStream(request.getInputStream(),2048, 1024);
            } catch (IOException ioe) {
                throw new GeoWebCacheException("Unable to get WFS query blob: "+ ioe.getMessage());
            }
        } else {
            parameters = request.getQueryString();
        }
       
        // Request handler is set automatically
        ConveyorWFS conv = new ConveyorWFS(sb, parameters, queryBlob, request, response);
        
        return conv;
    }
    
    public void handleRequest(TileLayerDispatcher tLD, Conveyor genConv) 
    throws GeoWebCacheException {
        ConveyorWFS conv = (ConveyorWFS) genConv;
        if(! conv.retrieve(-1)) {
            forwardRequest(conv);
            conv.persist();
        }
        
        super.writeResponse(conv, false);
    }

    /**
     * Creates the connection, passes it to connectAndLoad() which does
     * all the dirty work and saves information to the conveyor object.
     * The latter must be persisted outside of this method.
     * 
     * @param conv
     * @throws GeoWebCacheException
     */
    private void forwardRequest(ConveyorWFS conv) throws GeoWebCacheException {
        HttpURLConnection conxn = null;
        try {

            if (conv.getQueryBlob() == null) {
                URL conxnUrl = new URL(this.urlString + "?"+ conv.servletReq.getQueryString());
                conxn = (HttpURLConnection) conxnUrl.openConnection();
                conxn.setConnectTimeout(5000);
                conxn.setReadTimeout(this.readTimeout);
            } else {
                URL conxnUrl = new URL(this.urlString);
                conxn = (HttpURLConnection) conxnUrl.openConnection();
                conxn.setDoOutput(true);
                conxn.setConnectTimeout(5000);
                conxn.setReadTimeout(this.readTimeout);
                conxn.setRequestMethod(conv.servletReq.getMethod());
                if (conv.servletReq.getContentType() != null) {
                    conxn.setRequestProperty(
                            "Content-Type", 
                            conv.servletReq.getContentType());
                }
            }

            connectAndLoad(conxn, conv);
            
        } catch (MalformedURLException mue) {
            throw new GeoWebCacheException(
                    "Unable to connect to WFS backend: " + mue.getMessage());
        } catch (IOException ioe) {
            throw new GeoWebCacheException(
                    "Unable to communicate with WFS backend " + ioe.getMessage());
        } finally {
            if (conxn != null)
                conxn.disconnect();
        }
    }
    
    private void connectAndLoad(HttpURLConnection conxn, ConveyorWFS conv) 
    throws IOException, GeoWebCacheException {
        conxn.connect();

        if (200 != conxn.getResponseCode()) {
            throw new GeoWebCacheException(
                    "Got responsecode " + conxn.getResponseCode() 
                    + " from " + conxn.getURL());
        }

        int contentLength = conxn.getContentLength();
        String contentType = conxn.getContentType();
        
        byte[] data = ServletUtils.readStream(
                conxn.getInputStream(),
                50 * 1024, 1024);

        if (data == null || data.length == 0
                || (contentLength > 0 && contentLength != data.length) ) {
            String dataDesc;
            if (data == null) {
                dataDesc = "null";
            } else {
                dataDesc = "byte[" + data.length + "]";
            }

            throw new GeoWebCacheException(
                    "Data: " + dataDesc + ", HTTP Content-Lenght: " + contentLength);
        }
        
        // Check whether this is an exception report
        if(contentType.startsWith("text/xml")) {
            String testStr = "<?xml version=\"1.0\" ?>\n<ServiceExceptionReport";
            int byteLength = testStr.getBytes().length;
            
            if(byteLength < data.length) {
                byte[] tmp = new byte[byteLength];
                System.arraycopy(data, 0, tmp, 0, byteLength);
                String tmpStr = new String(tmp);
                if(tmpStr.contains("xception")) {
                    throw new GeoWebCacheException(
                            "Not caching response because it is believed to be an "
                            + " exception: " + tmpStr + "(Excerpt may use XML tags, use \"view source\")");
                }                                     
            }
        }
        
        conv.setContent(data);
        conv.setMimeTypeString(contentType);
    }
}
