package org.geowebcache.rest.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.OutputRepresentation;

public class ByteStreamerRestlet extends GWCRestlet {
    
    private static Log log = LogFactory.getLog(ByteStreamerRestlet.class);
    
    WebResourceBundle bundle;
    
    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        if (met.equals(Method.GET)) {
            doGet(request, response);
        } else {
            throw new RestletException("Method not allowed", Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        }
    }
    
    private static final WebResourceBundle DEFAULT_BUNDLE = ByteStreamerRestlet.class::getResource;
    protected URL getResource(String path) {
        if(bundle==null) {
            synchronized(this) {
                if(bundle==null) {
                    List<WebResourceBundle> result=GeoWebCacheExtensions.extensions(WebResourceBundle.class);
                    if(result.isEmpty()) {
                        bundle = DEFAULT_BUNDLE;
                    } else {
                        bundle = result.get(0);
                        if(result.size()>1) {
                            log.warn("Multiple web resource bundles present, using "+bundle.getClass().getName());
                        }
                    }
                }
            }
        }
        URL resource = bundle.apply(path);
        if(resource==null && bundle != DEFAULT_BUNDLE) {
            resource = DEFAULT_BUNDLE.apply(path);
        }
        return resource;
    }
    
    static final Pattern UNSAFE_RESOURCE = Pattern.compile("^/|/\\.\\./|^\\.\\./|\\.class$"); 
    private void doGet(Request request, Response response) {
        String filename = (String) request.getAttributes().get("filename");
        
        // Just to make sure we don't allow access to arbitrary resources
        if(UNSAFE_RESOURCE.matcher(filename).find()) {
            throw new RestletException("Illegal web resource", Status.CLIENT_ERROR_FORBIDDEN);
        }
        
        URL resource = getResource(filename);
        if(resource == null) {
            response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return;
        }
        
        response.setStatus(Status.SUCCESS_OK);
        
        String[] filenameParts = filename.split("\\.");
        String extension = filenameParts[filenameParts.length - 1];
                      
        MimeType mime = null;
        try {
            mime = MimeType.createFromExtension(extension);
        } catch (MimeException e) {
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            response.setEntity("Unable to create MimeType for " + extension);
            return;
        }
        
        ByteRepresentation imgRep = new ByteRepresentation(new MediaType(mime.getMimeType()), resource);
        
        response.setEntity(imgRep);
    }
    
    private class ByteRepresentation extends OutputRepresentation {
        URL resourceURL;
        
        public ByteRepresentation(MediaType mediaType, URL resourceURL) {
            super(mediaType);
            this.resourceURL = resourceURL;
        }
        
        public void write(OutputStream os) throws IOException {
            try( InputStream is = resourceURL.openStream(); ){
                int count = 0;
                byte[] tmp = new byte[2048];
                
                while(count != -1) {
                    count = is.read(tmp);
                    if(count != -1) {
                        os.write(tmp, 0, count);
                    }
                }
            }
        }  
    }
}
