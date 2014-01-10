package org.geowebcache.filter.resource;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.io.FileResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.GWCVars;

/**
 * Base class for ResourceFilters which run an external command which optimizes the compression of an 
 * image file.
 * 
 * @author Kevin Smith, Boundless
 *
 */
public abstract class ExternalImageOptimizerFilter implements ResourceFilter {

    private static Log log = LogFactory.getLog(ExternalImageOptimizerFilter.class);

    private File stagingArea;
    
    /**
     * Runs the external optimizer command on the file.
     * @param source The file to optimize
     * @param source The file to write the optimized image to
     * @param type The type of the file
     */
    protected abstract void optimize(File source, File dest, MimeType type) throws ResourceFilterException;
    
    /**
     * Check if the optimizer works for the given file type.
     * @param type the type of the file.
     * @return true if applicable, false otherwise
     */
    protected abstract boolean appliesTo(MimeType type);
    
    /**
     * Check if the necessary command is available.
     * @return true if the path to the external command has been specified, false otherwise.
     */
    protected abstract boolean hasOptimizerPath();
    
    public ExternalImageOptimizerFilter(File stagingArea) throws IOException {
        setStagingArea(stagingArea);
    }
    
    public ExternalImageOptimizerFilter(DefaultStorageFinder dsf, ApplicationContextProvider acp) throws IOException, ConfigurationException {
        String stagePath=GWCVars.findEnvVar(acp.getApplicationContext(), "OPTIMIZATION_STAGING_DIR");
        if(stagePath!=null) {
            setStagingArea(new File(stagePath));
        } else {
            setStagingArea(new File(dsf.getTemp(), "_gwc_image_optimization_staging"));
        }
    }

    public void applyTo(Resource res, MimeType type) throws ResourceFilterException {
        final FilePair staged;
        
        if(!hasOptimizerPath()) {
            log.trace("Image Optimization Filter does not have external command, doing nothing.");
            return;
        }
        if(!appliesTo(type)) {
            if(log.isTraceEnabled()) log.trace("Image Optimization Filter does not support format "+type.getFormat());
            return;
        }
        if(log.isInfoEnabled()) log.info("Applying Image Optimization Filter to image of type "+type.getFormat());
        
        try {
            staged = new FilePair(res);
        } catch (IOException e) {
            throw new ResourceFilterException("Could not create temporary file for image optimization",e);
        }
        try{
            if(log.isTraceEnabled()) log.trace("Optimizing image "+staged.original+" to "+staged.result);
            optimize(staged.original, staged.result, type);
            try {
                staged.unstage();
            } catch (IOException e) {
                throw new ResourceFilterException("Could not read temporary file back into resource after image optimization",e);
            }
        } finally {
            try {
                staged.close();
            } catch (IOException e) {
                log.error("Could not remove temporary files after image optimization", e);
            }
        }
    }
    
    protected File getStagingArea() {
        return stagingArea;
    }
    
    protected void setStagingArea(File f) {
        stagingArea=f;
        stagingArea.mkdir();
        if(log.isInfoEnabled()) log.info("Image Optimization staging in "+f);
    }

    private class FilePair implements Closeable {
        final File original;
        final File result;
        final Resource resource;
        final boolean deleteOriginal;
        
        public FilePair(Resource resource) throws IOException {
            this.resource = resource;
            if(resource instanceof FileResource) {
                original = ((FileResource) resource).getFile();
                deleteOriginal = false;
            } else {
                original = new File(getStagingArea(), UUID.randomUUID().toString());
                deleteOriginal = true;
                // Open the output stream and read the blob into the tile
                FileOutputStream fos = null;
                FileChannel channel = null;
                try {
                    fos = new FileOutputStream(original);

                    channel = fos.getChannel();
                    try {
                          resource.transferTo(channel);
                    } finally {
                        if(channel != null) {
                            channel.close();
                        }
                    }
                } finally {
                    IOUtils.closeQuietly(fos);
                }
            }
            
            result = new File(getStagingArea(), UUID.randomUUID().toString());
        }
        
        public void unstage() throws IOException {
            if(!(resource instanceof FileResource)){
                // If the resource is not a file, read the file into the resource
                FileInputStream fis = null;
                FileChannel channel = null;
                try {
                    fis = new FileInputStream(result);

                    channel = fis.getChannel();
                    try {
                          resource.transferFrom(channel);
                    } finally {
                        if(channel != null) {
                            channel.close();
                        }
                    }
                } finally {
                    IOUtils.closeQuietly(fis);
                }
            } else {
                // It it's a file resource, so overwrite the backing file.
                FileUtils.copyFile(result, ((FileResource)resource).getFile());
            }
            
        }

        public void close() throws IOException {
            result.delete();
            if(deleteOriginal) original.delete();
        }
    
    }
}
