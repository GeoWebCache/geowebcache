package org.geowebcache.rest.webresources;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
    
    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        if (met.equals(Method.GET)) {
            doGet(request, response);
        } else {
            throw new RestletException("Method not allowed", Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        }
    }

    private void doGet(Request request, Response response) {
        String filename = (String) request.getAttributes().get("filename");
        
        InputStream is = ByteStreamerRestlet.class.getResourceAsStream(filename);
        
        if(is == null) {
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
        
        ByteRepresentation imgRep = new ByteRepresentation(new MediaType(mime.getMimeType()), is);
        
        response.setEntity(imgRep);
    }

    private class ByteRepresentation extends OutputRepresentation {
        InputStream is = null;

        public ByteRepresentation(MediaType mediaType, InputStream is) {
            super(mediaType);
            this.is = is;
        }

        public void write(OutputStream os) throws IOException {
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
