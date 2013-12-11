package org.geowebcache.filter.resource;

import java.io.File;
import java.io.IOException;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.GWCVars;
import org.springframework.web.context.WebApplicationContext;

/**
 * Filter which applies PNGCrush to PNG images before caching them.
 * 
 * @author Kevin Smith, Boundless
 *
 */
public class PNGCrushFilter extends ExternalImageOptimizerFilter {

    private static Log log = LogFactory.getLog(PNGCrushFilter.class);

    String pngCrushPath;
    String[] pngCrushOptions;
    
    public PNGCrushFilter(DefaultStorageFinder dsf, ApplicationContextProvider acp) throws IOException,
            ConfigurationException {
        super(dsf, acp);
        initFromSystemVariables(acp.getApplicationContext());
    }

    public PNGCrushFilter(File stagingArea, ApplicationContextProvider acp) throws IOException, 
            ConfigurationException{
        super(stagingArea);
        initFromSystemVariables(acp.getApplicationContext());
    }

    private void initFromSystemVariables(WebApplicationContext ctxt) {
        String path = GWCVars.findEnvVar(ctxt, "PNGCRUSH_PATH");
        String options = GWCVars.findEnvVar(ctxt, "PNGCRUSH_OPTIONS");
        
        //Defaults
        if(options==null) options="-q";
        
        setPngCrushPath(path);
        setOptions(options);
    }
    
    @Override
    protected void optimize(File source, File dest, MimeType type) throws ResourceFilterException {
        run(getCommandLineArguments(source, dest));
    }
    
    @Override
    protected boolean appliesTo(MimeType type) {
        return (type==ImageMime.png||type==ImageMime.png8||type==ImageMime.png24||type==ImageMime.png_24);
    }
    
   
    protected CommandLine getCommandLineArguments(File file, File converted) {
        
        CommandLine parsedCommand = new CommandLine(pngCrushPath);
        
        parsedCommand.addArguments(pngCrushOptions, false);
        
        parsedCommand.addArgument(file.getAbsolutePath());
        
        parsedCommand.addArgument(converted.getAbsolutePath());

        return parsedCommand;
    }

    
    protected void run(CommandLine cmd) throws ResourceFilterException {
        if(log.isInfoEnabled()) log.info("Running PNGCrush: "+cmd.toString());
        DefaultExecutor executor = new DefaultExecutor();
        try {
            executor.execute(cmd);
        } catch (Exception e) {
            throw new ResourceFilterException("Could not execute "+cmd.toString(), e);
        }
    }
    
    /**
     * Set the path of the pngcrush command.  Filter will do nothing if set to {@code null} or empty 
     * string.
     * @param pngCrushPath
     */
    public void setPngCrushPath(String pngCrushPath) {
        if(log.isInfoEnabled()) log.info("PNGCrush path set to: "+pngCrushPath);
        this.pngCrushPath = pngCrushPath;
    }
    
    /**
     * Additional options to use when calling pngcrush
     * 
     * @param options
     */
    public void setOptions(String options) {
        // Parse the options from the given string using a dummy command
        if(log.isInfoEnabled()) log.info("PNGCrush options set to: "+options);
        this.pngCrushOptions = CommandLine.parse("dummycommand "+options).getArguments();
    }
    
    public boolean hasOptimizerPath() {
        return pngCrushPath != null && !pngCrushPath.isEmpty();
    }
}
