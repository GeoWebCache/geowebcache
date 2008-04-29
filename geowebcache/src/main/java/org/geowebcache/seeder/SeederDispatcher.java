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
    
    public static String GEOWEBCACHE_ALLOWED_SEEDERS = "GEOWEBCACHE_ALLOWED_SEEDERS";

    private HashMap allowedSeeders = new HashMap();
    
    public SeederDispatcher() {
        // Do nothing, I guess
    }

    /**
     * List of addresses that are allowed to seed the cache.
     * 
     * TODO Move into separate class
     * 
     * @param addressList
     *            list of addresses from applicationContext.xml
     */
    public void setAllowedSeeders(List addressList) {
        Iterator iter = addressList.iterator();
        while (iter.hasNext()) {
            String curAdr = (String) iter.next();
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

    /*
     * 
     */
    public String getLayerIdent(HttpServletRequest request)
            throws SeederException {
        String layers = ServletUtils.stringFromMap(request.getParameterMap(),
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
        InetAddress adr = null;
        try {
            adr = InetAddress.getByName(request.getRemoteAddr());
        } catch (UnknownHostException uhe) {
            throw new SeederException("Unable to lookup "
                    + request.getRemoteAddr());
        }

        if (!this.allowedSeeders.containsKey(adr.hashCode())) {
            throw new SeederException(
                    adr.toString()
                            + " is not in the list of allowed seeders"
                            + " or addjust in applicationContex.xml or set "
                            + SeederDispatcher.GEOWEBCACHE_ALLOWED_SEEDERS );
        }

        String adrStr = adr.toString();
        String layerStr = layer.getName();

        String[] relevantParams = { "bbox", "start", "stop", "srs", "format" };
        Map params = ServletUtils.selectedStringsFromMap(request
                .getParameterMap(), relevantParams);

        /* Projection */
        SRS srs = null;
        int srsIdx = -1;

        String srsStr = (String) params.get("srs");
        if (srsStr != null) {
            srs = new SRS(srsStr);
            srsIdx = layer.getSRSIndex(srs);
        }
        if (srsIdx < 0) {
            log.info(adrStr + " reverting to default SRS for " + layerStr);
            srsIdx = 0;
            srs = layer.getProjections()[0];
        }

        /* Bounding box */
        BBOX bbox = null;

        String bboxStr = (String) params.get("bbox");
        if (bboxStr != null) {
            bbox = new BBOX(bboxStr);
        }
        if (bbox == null || !bbox.isSane()
                || null != layer.supportsBbox(srs, bbox)) {
            log.info(adrStr + " reverting to bounding box for " + layerStr);
            bbox = layer.getBounds(srsIdx);
        }

        /* Format */
        MimeType mime = null;

        String formatStr = (String) params.get("format");
        if (formatStr != null) {
            mime = MimeType.createFromFormat(formatStr);
        }
        if (mime == null) {
            mime = layer.getDefaultMimeType();
            log.info(adrStr + " reverting to default MIME type: "
                    + mime.toString());
        }

        /* Stop */
        int stop = -1;

        String stopStr = (String) params.get("stop");
        if (stopStr != null) {
            stop = Integer.parseInt(stopStr);
        }
        if (stop < 0) {
            stop = 20;
            log.info(adrStr + " reverting to default stop value: " + stop);
        }

        /* Start */
        int start = -1;

        String startStr = (String) params.get("start");
        if (startStr != null) {
            start = Integer.parseInt(startStr);
        }
        if (start < 0) {
            start = 0;
            log.info(adrStr + " reverting to default start value: " + start);
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

    private static String getAllowedSeeders(WebApplicationContext ctx) {
        String tmpStr = null;
        if(ctx != null) {
            tmpStr = ctx.getServletContext().getInitParameter(GEOWEBCACHE_ALLOWED_SEEDERS);
        }
        if(tmpStr != null && tmpStr.length() > 7) {
            log.info("Using servlet init context parameter to configure "+GEOWEBCACHE_ALLOWED_SEEDERS+" to "+tmpStr);
            return tmpStr;
        }
        
        tmpStr = System.getProperty(GEOWEBCACHE_ALLOWED_SEEDERS);
        if(tmpStr != null && tmpStr.length() > 7) {
            log.info("Using Java environment variable to configure "+GEOWEBCACHE_ALLOWED_SEEDERS+" to "+tmpStr);
            return tmpStr;
        }
        
        tmpStr = System.getenv(GEOWEBCACHE_ALLOWED_SEEDERS);
        if(tmpStr != null && tmpStr.length() > 7) {
            log.info("Using System environment variable to configure "+GEOWEBCACHE_ALLOWED_SEEDERS+" to "+tmpStr);
            return tmpStr;
        }
        
        tmpStr = "http://localhost:8080/geoserver/wms?request=GetCapabilities";
        log.info("No context parameter, system or Java environment variables found for " + GEOWEBCACHE_ALLOWED_SEEDERS);
        log.info("Reverting to " + tmpStr );
        
        return tmpStr;
    }

    public void setApplicationContext(ApplicationContext context) throws BeansException {
        // Add list from environment variable
        String seedersStr = getAllowedSeeders((WebApplicationContext) context);
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
