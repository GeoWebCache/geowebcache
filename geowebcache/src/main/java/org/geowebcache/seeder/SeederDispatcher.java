package org.geowebcache.seeder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;

public class SeederDispatcher implements ApplicationContextAware {

    private static Log log = LogFactory
            .getLog(org.geowebcache.seeder.SeederDispatcher.class);
    
    public final static String GEOWEBCACHE_ALLOWED_SEEDERS = "GEOWEBCACHE_ALLOWED_SEEDERS";

    private HashMap<Integer,InetAddress> allowedSeeders = new HashMap<Integer,InetAddress>();
    
    private boolean disableCheck = false;
    
    public SeederDispatcher() {
        // Do nothing, I guess
    }

    /**
     * List of addresses that are allowed to seed the cache.
     * 
     * @param addressList
     *            list of addresses from applicationContext.xml
     */
    public void setAllowedSeeders(List addressList) {
        Iterator iter = addressList.iterator();
        while (iter.hasNext()) {
            String curAdr = (String) iter.next();
            if(curAdr.equalsIgnoreCase("*")) {
                this.disableCheck = true;
                return;
            }
            try {
                InetAddress allowedSeeder = InetAddress.getByName(curAdr);
                log.info("Adding " + allowedSeeder.getHostAddress()
                        + " to list of allowed seeders");
                allowedSeeders.put(allowedSeeder.hashCode(), allowedSeeder);
            } catch (UnknownHostException e) {
                log.error("Unable to determine address for " + curAdr);
            }
        }        
    }

    /**
     * 
     * @param request
     * @return all "layers" parameters present in request
     * @throws SeederException
     */
    public String[] getLayerIdents(HttpServletRequest request)
            throws SeederException {
        String[] layers = ServletUtils.stringsFromMap(request.getParameterMap(),
                "layers");

        if (layers == null) {
            throw new SeederException("layers parameter not specified");
        }
        return layers;
    }

    /**
     * Parses the request to dermine bounds, start, stop and format, then calls
     * startSeeder()
     * 
     * @param layer
     *            predetermined layer
     * @param request
     * @param response
     */
    public void handleSeed(TileLayer layer, HttpServletRequest request,
            HttpServletResponse response) throws GeoWebCacheException,
            IOException {

        // Throws exception if necessary
        checkSeeder(request);

        //String adrStr = adr.toString();
        String layerStr = layer.getName();

        String[] relevantParams = { "bbox", "start", "stop", "srs", "format" };
        String[] paramValues= ServletUtils.selectedStringsFromMap(request
                .getParameterMap(), relevantParams);

        /* Projection */
        SRS srs = null;
        int srsIdx = -1;

        String srsStr = paramValues[3];
        if (srsStr != null) {
            srs = new SRS(srsStr);
            srsIdx = layer.getSRSIndex(srs);
        }
        if (srsIdx < 0) {
            log.info("Reverting to default SRS for " + layerStr);
            srsIdx = 0;
            srs = layer.getProjections()[0];
        }

        /* Bounding box */
        BBOX bbox = null;

        String bboxStr = paramValues[0];
        if (bboxStr != null) {
            bbox = new BBOX(bboxStr);
        }
        if (bbox == null || !bbox.isSane()
                || null != layer.supportsBbox(srs, bbox)) {
            log.info("Reverting to bounding box for " + layerStr);
            bbox = layer.getBounds(srsIdx);
        }

        /* Format */
        MimeType mime = null;

        String formatStr = paramValues[4];
        if (formatStr != null) {
            mime = MimeType.createFromFormat(formatStr);
        }
        if (mime == null) {
            mime = layer.getDefaultMimeType();
            log.info("Reverting to default MIME type: "
                    + mime.toString());
        }

        /* Stop */
        int stop = -1;

        String stopStr = paramValues[2];
        if (stopStr != null) {
            stop = Integer.parseInt(stopStr);
        }
        if (stop < 0) {
            stop = 20;
            log.info("Reverting to default stop value: " + stop);
        }

        /* Start */
        int start = -1;

        String startStr = paramValues[1];
        if (startStr != null) {
            start = Integer.parseInt(startStr);
        }
        if (start < 0) {
            start = 0;
            log.info("Reverting to default start value: " + start);
        }

        if (start > stop) {
            throw new SeederException("start (" + start
                    + ") cannot be greater than stop (" + stop + ")");
        }

        /* Create a new seeder */
        Seeder aSeeder = new Seeder(layer);

        /* Start the seeding */
        aSeeder.doSeed(start, stop, mime, srs, bbox, response);
    }
    
    
    /**
     * Parses the request to dermine bounds, start, stop and format, then calls
     * startSeeder()
     * 
     * @param layer
     *            predetermined layer
     * @param request
     * @param response
     */
    public void handleTruncate(TileLayer layer, HttpServletRequest request,
            HttpServletResponse response) throws GeoWebCacheException,
            IOException {
        
        // Throws exception if necessary
        checkSeeder(request);

        //String adrStr = adr.toString();
        String layerStr = layer.getName();

        String[] relevantParams = { "bbox", "start", "stop", "srs", "format" };
        String[] paramValues= ServletUtils.selectedStringsFromMap(request
                .getParameterMap(), relevantParams);

        /* Projection */
        SRS srs = null;
        //int srsIdx = -1;

        String srsStr = paramValues[3];
        if (srsStr != null) {
            srs = new SRS(srsStr);
            //srsIdx = layer.getSRSIndex(srs);
        }

        /* Bounding box */
        BBOX bbox = null;

        String bboxStr = paramValues[0];
        if (bboxStr != null) {
            bbox = new BBOX(bboxStr);
        }
        if (bbox != null && (bbox.isSane() || null != layer.supportsBbox(srs, bbox))) {
            log.info("Reverting to bounding box for " + layerStr);
            bbox = null;
        }

        /* Format */
        MimeType mime = null;

        String formatStr = paramValues[4];
        if (formatStr != null) {
            mime = MimeType.createFromFormat(formatStr);
        }
        
        /* Stop */
        int stop = -1;

        String stopStr = paramValues[2];
        if (stopStr != null) {
            stop = Integer.parseInt(stopStr);
        }

        /* Start */
        int start = -1;

        String startStr = (String) paramValues[1];
        if (startStr != null) {
            start = Integer.parseInt(startStr);
        }

        if (start > 0 && stop > 0 && start > stop) {
            throw new SeederException("start (" + start
                    + ") cannot be greater than stop (" + stop + ")");
        }

        /* Create a new truncater */
        Truncater aTruncater = new Truncater(layer);

        /* Start truncating */
        aTruncater.doTruncate(start, stop, mime, srs, bbox, response);
    }
    

    private static String getAllowedSeeders(WebApplicationContext ctx) {
        String tmpStr = null;
        if(ctx != null) {
            tmpStr = ctx.getServletContext().getInitParameter(
                    GEOWEBCACHE_ALLOWED_SEEDERS);
        }
        if(tmpStr != null) {
            log.info("Using servlet init context parameter to configure "
                    +GEOWEBCACHE_ALLOWED_SEEDERS+" to "+tmpStr);
            return tmpStr;
        }
        
        tmpStr = System.getProperty(GEOWEBCACHE_ALLOWED_SEEDERS);
        if(tmpStr != null && tmpStr.length() > 7) {
            log.info("Using Java environment variable to configure "
                    +GEOWEBCACHE_ALLOWED_SEEDERS+" to "+tmpStr);
            return tmpStr;
        }
        
        tmpStr = System.getenv(GEOWEBCACHE_ALLOWED_SEEDERS);
        if(tmpStr != null && tmpStr.length() > 7) {
            log.info("Using System environment variable to configure "
                    +GEOWEBCACHE_ALLOWED_SEEDERS+" to "+tmpStr);
            return tmpStr;
        }

        log.info("No context parameter, system or Java environment variables"
                +" found for " + GEOWEBCACHE_ALLOWED_SEEDERS);
        return null;
    }
    
    /**
     * Throws exception if client is not allowed to seed
     * 
     * @param request
     * @throws SeederException
     */
    public void checkSeeder(HttpServletRequest request) 
    throws SeederException {
        InetAddress adr = null;
        
        if (!disableCheck) {
            try {
                adr = InetAddress.getByName(request.getRemoteAddr());
            } catch (UnknownHostException uhe) {
                throw new SeederException("Unable to lookup "
                        + request.getRemoteAddr());
            }

            if (!this.allowedSeeders.containsKey(adr.hashCode())) {
                throw new SeederException(adr.toString()
                        + " is not in the list of allowed seeders."
                        + " Adjust in geowebcache-servlet.xml or set "
                        + SeederDispatcher.GEOWEBCACHE_ALLOWED_SEEDERS);
            }
        }
    }

    public void setApplicationContext(ApplicationContext context) throws BeansException {
        // Add list from environment variable
        String seedersStr = getAllowedSeeders((WebApplicationContext) context);
        if(seedersStr == null) {
            return;
        }
        if(seedersStr.equalsIgnoreCase("*")) {
            this.disableCheck = true;
            return;
        }
        String[] seedersStrs = seedersStr.split(",");
        for(int i=0; i<seedersStrs.length; i++) {
            try {
                InetAddress allowedSeeder = InetAddress.getByName(seedersStrs[i]);
                log.info("Adding " + allowedSeeder.getHostAddress()
                        + " to list of allowed seeders");
                allowedSeeders.put(allowedSeeder.hashCode(), allowedSeeder);
            } catch (UnknownHostException e) {
                log.error("Unable to determine address for " + seedersStrs[i]);
            }
        }
    }
}
