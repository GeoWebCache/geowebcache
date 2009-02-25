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
import java.io.InputStream;
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
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.ServletUtils;

public class WFSService extends Service {
    public static final String SERVICE_WFS = "wfs";
    
    public static final String GEOSERVER_WFS_URL = "GEOSERVER_WFS_URL";
    
    public static final String GEOWEBCACHE_WFS_FILTER = "GEOWEBCACHE_WFS_FILTER";
    
    private static Log log = LogFactory.getLog(org.geowebcache.service.wfs.WFSService.class);

    private int readTimeout;
    
    private String urlString;
    
    private String regexFilter;
    
    private StorageBroker storageBroker;
    
    public WFSService(StorageBroker sb, String urlString, String regexFilter, int readTimeout) {
        super(SERVICE_WFS);
        this.urlString = urlString;
        this.storageBroker = sb;
        
        if(regexFilter != null && regexFilter.length() == 0) {
            this.regexFilter = null;
        } else {
            this.regexFilter = regexFilter;
        }
        
        readTimeout = 1000 * readTimeout;
        
        log.info("Configured to forward to " + urlString 
                + " , timeout is " + readTimeout 
                + "ms regex filter " + regexFilter);
    }
    
    public WFSService(StorageBroker sb, ApplicationContextProvider ctxProv, String regexFilter, int readTimeout) {
        super(SERVICE_WFS);
        this.storageBroker = sb;
        urlString = ctxProv.getSystemVar(GEOSERVER_WFS_URL);
        if(urlString != null) {
            if(urlString.contains("?")) {
                urlString = urlString.substring(0, urlString.indexOf("?"));
            }
        } else {
            urlString = "http://localhost:8080/geoserver/wfs";
        }
        
        regexFilter = ctxProv.getSystemVar(GEOWEBCACHE_WFS_FILTER);
        
        readTimeout = 1000 * readTimeout;
        
        log.info("Configured to forward to " + urlString 
                + " , timeout is " + readTimeout 
                + "ms, regex filter " + regexFilter);
    }
    
    public ConveyorWFS getConveyor(HttpServletRequest request,HttpServletResponse response)
    throws GeoWebCacheException {
        
        String parameters = null;
        byte[] queryBlob = null;
        
        if (request.getContentLength() > 0) {
            try {
                queryBlob = ServletUtils.readStream(request.getInputStream(),2048, 1024);
            } catch (IOException ioe) {
                throw new GeoWebCacheException("Unable to get WFS query blob: "+ ioe.getMessage());
            }
            performRegexCheck(queryBlob);
        } else {
            parameters = request.getQueryString();
            performRegexCheck(parameters);
        }
       
        // Request handler is set automatically
        ConveyorWFS conv = new ConveyorWFS(storageBroker, parameters, queryBlob, request, response);
        
        return conv;
    }
    
    private void performRegexCheck(byte[] queryBlob) throws GeoWebCacheException {
        if(regexFilter == null)
            return;
        if(queryBlob.length > 100*1024)
            throw new GeoWebCacheException("Queryblob is too large ("
                    +queryBlob.length
                    +") to apply filter.");
        else
            performRegexCheck(new String(queryBlob));
    }
    
    private void performRegexCheck(String parameters) throws GeoWebCacheException {
        if(regexFilter == null)
            return;
        
        if(! parameters.matches(regexFilter))
            throw new GeoWebCacheException("Sorry. The request violates the filter.");
    }
    
    public void handleRequest(Conveyor genConv) throws GeoWebCacheException {
        ConveyorWFS conv = (ConveyorWFS) genConv;

        try {
            if (!conv.retrieve(-1)) {
                // TODO Replace with hash-based locking
                synchronized (this) {
                    if (!conv.retrieve(-1)) {
                        forwardRequest(conv);
                        conv.persist();
                    }
                }
            }
            super.writeWFSResponse(conv, false);
            
        } finally {
            InputStream is = conv.getInputStream();
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new GeoWebCacheException(e.getMessage());
                }
            }
        }
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
        
        conv.setInputStream(conxn.getInputStream());
//        byte[] data = ServletUtils.readStream(
//                conxn.getInputStream(),
//                50 * 1024, 1024);
//
//
//        if (data == null || data.length == 0
//                || (contentLength > 0 && contentLength != data.length) ) {
//            String dataDesc;
//            if (data == null) {
//                dataDesc = "null";
//            } else {
//                dataDesc = "byte[" + data.length + "]";
//            }
//
//            throw new GeoWebCacheException(
//                    "Data: " + dataDesc + ", HTTP Content-Lenght: " + contentLength);
//        }
        
        // Check whether this is an exception report
//        if(contentType.startsWith("text/xml")) {
//            String testStr = "<?xml version=\"1.0\" ?>\n<ServiceExceptionReport";
//            int byteLength = testStr.getBytes().length;
//            
//            if(byteLength < data.length) {
//                byte[] tmp = new byte[byteLength];
//                System.arraycopy(data, 0, tmp, 0, byteLength);
//                String tmpStr = new String(tmp);
//                if(tmpStr.contains("xception")) {
//                    throw new GeoWebCacheException(
//                            "Not caching response because it is believed to be an "
//                            + " exception: " + tmpStr + "(Excerpt may use XML tags, use \"view source\")");
//                }                                     
//            }
//        }
//        
//        conv.setContent(data);
        conv.setMimeTypeString(contentType);
    }
}
