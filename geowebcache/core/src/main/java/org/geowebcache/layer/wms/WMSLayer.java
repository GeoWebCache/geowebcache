/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer.wms;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.config.legends.LegendsRawInfo;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.layer.ProxyLayer;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.URLs;

/** A tile layer backed by a WMS server */
public class WMSLayer extends AbstractTileLayer implements ProxyLayer {

    private static Logger log = Logging.getLogger(WMSLayer.class.getName());

    public enum RequestType {
        MAP,
        FEATUREINFO
    }

    public enum HttpRequestMode {
        Get,
        FormPost
    }

    private String[] wmsUrl;

    private String wmsLayers;

    private String wmsQueryLayers;

    protected String wmsStyles;

    protected Integer gutter;

    private String errorMime;

    private String wmsVersion;

    private String httpUsername;

    private String httpPassword;

    private String proxyUrl;

    // Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private Boolean tiled;

    private Boolean transparent;

    private String bgColor;

    private String palette;

    private String vendorParameters;

    // Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private String cachePrefix;

    private Integer concurrency;

    // private transient int expireCacheInt = -1;

    // private transient int expireClientsInt = -1;

    private transient int curWmsURL;

    private transient WMSSourceHelper sourceHelper;

    protected transient String sphericalMercatorOverride;

    private transient LockProvider lockProvider;

    private LegendsRawInfo legends;

    private HttpRequestMode httpRequestMode = HttpRequestMode.Get;

    WMSLayer() {
        // default constructor for XStream
    }

    /** Note XStream uses reflection, this is only used for testing and loading from getCapabilities */
    public WMSLayer(
            String layerName,
            String[] wmsURL,
            String wmsStyles,
            String wmsLayers,
            List<String> mimeFormats,
            Map<String, GridSubset> subSets,
            List<ParameterFilter> parameterFilters,
            int[] metaWidthHeight,
            String vendorParams,
            boolean queryable,
            String wmsQueryLayers) {

        this.name = layerName;
        this.wmsUrl = wmsURL;
        this.wmsLayers = wmsLayers;
        this.wmsStyles = wmsStyles;
        this.mimeFormats = mimeFormats == null ? null : new ArrayList<>(mimeFormats);
        this.subSets = subSets;
        this.gridSubsets = new ArrayList<>();
        if (subSets != null) {
            for (GridSubset subset : subSets.values()) {
                gridSubsets.add(new XMLGridSubset(subset));
            }
        }
        this.parameterFilters = parameterFilters == null ? null : new ArrayList<>(parameterFilters);
        this.metaWidthHeight = metaWidthHeight;
        this.vendorParameters = vendorParams;
        this.transparent = true;
        // this.bgColor = "0x000000";
        // this.palette = "test.png";
        this.queryable = queryable;
        this.wmsQueryLayers = wmsQueryLayers;
    }

    @Override
    protected Object readResolve() {
        super.readResolve();
        return this;
    }

    /** @see org.geowebcache.layer.AbstractTileLayer#initializeInternal(org.geowebcache.grid.GridSetBroker) */
    @Override
    protected boolean initializeInternal(GridSetBroker gridSetBroker) {
        if (null == this.enabled) {
            this.enabled = Boolean.TRUE;
        }

        if (null == this.sourceHelper) {
            log.config(this.name
                    + " is configured without a source, which is a bug unless you're running tests that don't care.");
        }

        curWmsURL = 0;

        if (backendTimeout == null) {
            backendTimeout = 120;
        }

        if (this.metaWidthHeight == null || this.metaWidthHeight.length != 2) {
            this.metaWidthHeight = new int[2];
            this.metaWidthHeight[0] = 3;
            this.metaWidthHeight[1] = 3;
        }

        // Create conditions for tile locking
        if (concurrency == null) {
            concurrency = 32;
        }

        if (this.sourceHelper instanceof WMSHttpHelper) {
            for (int i = 0; i < wmsUrl.length; i++) {
                String url = wmsUrl[i];
                if (!url.contains("?")) {
                    wmsUrl[i] = url + "?";
                }
            }
        }

        if (gutter == null) {
            gutter = Integer.valueOf(0);
        }

        if (this.requestFilters != null) {
            Iterator<RequestFilter> iter = requestFilters.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().initialize(this);
                } catch (GeoWebCacheException e) {
                    log.severe(e.getMessage());
                }
            }
        }

        return true;
    }

    @Override
    public Resource getFeatureInfo(ConveyorTile convTile, BoundingBox bbox, int height, int width, int x, int y)
            throws GeoWebCacheException {
        return sourceHelper.makeFeatureInfoRequest(convTile, bbox, height, width, x, y);
    }

    /**
     * The main function
     *
     * <p>1) Create cache key, test whether we can retrieve without locking 2) Get lock for metatile, monitor condition
     * variable if not (Recheck cache after signal) 3) Create metatile request, execute 4) Get tiles and save them to
     * cache 5) Unlock metatile, signal other threads 6) Set Cache-Control, return tile
     *
     * @param tile The tile request
     * @return The resulting tile request
     */
    @Override
    public ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException, IOException, OutsideCoverageException {
        MimeType mime = tile.getMimeType();

        if (mime == null) {
            mime = this.formats.get(0);
        }

        if (!formats.contains(mime)) {
            throw new GeoWebCacheException(mime.getFormat() + " is not a supported format for " + name);
        }

        String tileGridSetId = tile.getGridSetId();

        long[] gridLoc = tile.getTileIndex();

        GridSubset gridSubset = getGridSubset(tileGridSetId);
        // Final preflight check, throws exception if necessary
        gridSubset.checkCoverage(gridLoc);

        ConveyorTile returnTile;

        tile.setMetaTileCacheOnly(!gridSubset.shouldCacheAtZoom(gridLoc[2]));
        try {
            if (tryCacheFetch(tile)) {
                returnTile = finalizeTile(tile);
            } else if (mime.supportsTiling()) { // Okay, so we need to go to the backend
                returnTile = getMetatilingReponse(tile, true);
            } else {
                returnTile = getNonMetatilingReponse(tile, true);
            }
        } finally {
            cleanUpThreadLocals();
        }

        sendTileRequestedEvent(returnTile);

        return returnTile;
    }

    /** Used for seeding */
    @Override
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException, IOException {
        GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        if (gridSubset.shouldCacheAtZoom(tile.getTileIndex()[2])) {
            if (tile.getMimeType().supportsTiling() && (metaWidthHeight[0] > 1 || metaWidthHeight[1] > 1)) {
                getMetatilingReponse(tile, tryCache);
            } else {
                getNonMetatilingReponse(tile, tryCache);
            }
        }
    }

    /**
     * Metatiling request forwarding
     *
     * @param tile the Tile with all the information
     * @param tryCache whether to try the cache, or seed
     */
    private ConveyorTile getMetatilingReponse(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException {

        // int idx = this.getSRSIndex(tile.getSRS());
        long[] gridLoc = tile.getTileIndex();

        GridSubset gridSubset = subSets.get(tile.getGridSetId());

        // GridCalculator gridCalc = getGrid(tile.getSRS()).getGridCalculator();

        MimeType mimeType = tile.getMimeType();
        Map<String, String> filteringParameters = tile.getFilteringParameters();
        if (filteringParameters.isEmpty()) {
            filteringParameters = getDefaultParameterFilters();
        }
        WMSMetaTile metaTile = new WMSMetaTile(
                this,
                gridSubset,
                mimeType,
                this.getFormatModifier(tile.getMimeType()),
                gridLoc,
                metaWidthHeight[0],
                metaWidthHeight[1],
                filteringParameters);

        // Leave a hint to save expiration, if necessary
        if (saveExpirationHeaders) {
            metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
        }

        String metaKey = buildLockKey(tile, metaTile);
        Lock lock = null;
        try {
            /** ****************** Acquire lock ******************* */
            lock = lockProvider.getLock(metaKey);
            /** ****************** Check cache again ************** */
            if (tryCache && tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                return finalizeTile(tile);
            }

            tile.setCacheResult(CacheResult.MISS);

            /*
             * This thread's byte buffer
             */
            ByteArrayResource buffer = getImageBuffer(WMS_BUFFER);

            /** ****************** No luck, Request metatile ****** */
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
            }
            long requestTime = System.currentTimeMillis();
            sourceHelper.makeRequest(metaTile, buffer);

            if (metaTile.getError()) {
                throw new GeoWebCacheException("Empty metatile, error message: " + metaTile.getErrorMessage());
            }

            if (saveExpirationHeaders) {
                // Converting to seconds
                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
            }

            metaTile.setImageBytes(buffer);

            saveTiles(metaTile, tile, requestTime);

            /** ****************** Return lock and response ****** */
        } finally {
            if (lock != null) {
                lock.release();
            }
            metaTile.dispose();
        }
        return finalizeTile(tile);
    }

    private String buildLockKey(ConveyorTile tile, WMSMetaTile metaTile) {
        StringBuilder metaKey = new StringBuilder();

        final long[] tileIndex;
        if (metaTile != null) {
            tileIndex = metaTile.getMetaGridPos();
            metaKey.append("meta_");
        } else {
            tileIndex = tile.getTileIndex();
            metaKey.append("tile_");
        }
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];

        metaKey.append(tile.getLayerId());
        metaKey.append("_").append(tile.getGridSetId());
        metaKey.append("_").append(x).append("_").append(y).append("_").append(z);
        if (tile.getParametersId() != null) {
            metaKey.append("_").append(tile.getParametersId());
        }
        metaKey.append(".").append(tile.getMimeType().getFileExtension());

        return metaKey.toString();
    }

    /**
     * Non-metatiling forward to backend
     *
     * @param tile the Tile with all the information
     * @param tryCache whether to try the cache, or seed
     */
    private ConveyorTile getNonMetatilingReponse(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException {
        // String debugHeadersStr = null;
        long[] gridLoc = tile.getTileIndex();

        String lockKey = buildLockKey(tile, null);
        Lock lock = null;
        try {
            /** ****************** Acquire lock ******************* */
            lock = lockProvider.getLock(lockKey);

            /** ****************** Check cache again ************** */
            if (tryCache && tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                return tile;
            }

            /** ****************** Tile ******************* */
            // String requestURL = null;
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                tile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
            }

            tile = doNonMetatilingRequest(tile);

            if (tile.getStatus() > 299 || this.getExpireCache((int) gridLoc[2]) != GWCVars.CACHE_DISABLE_CACHE) {
                tile.persist();
            }

            if (saveExpirationHeaders) {
                // Converting to seconds in the process
                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
            }

            /** ****************** Return lock and response ****** */
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return finalizeTile(tile);
    }

    public boolean tryCacheFetch(ConveyorTile tile) {
        int expireCache = this.getExpireCache((int) tile.getTileIndex()[2]);
        if (expireCache != GWCVars.CACHE_DISABLE_CACHE) {
            try {
                return tile.retrieve(expireCache * 1000L);
            } catch (GeoWebCacheException gwce) {
                log.severe(gwce.getMessage());
                tile.setErrorMsg(gwce.getMessage());
                return false;
            }
        }
        return false;
    }

    @Override
    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        tile.setTileLayer(this);

        ByteArrayResource buffer = getImageBuffer(WMS_BUFFER);
        sourceHelper.makeRequest(tile, buffer);

        if (tile.getError() || buffer.getSize() == 0) {
            throw new GeoWebCacheException("Empty tile, error message: " + tile.getErrorMessage());
        }

        tile.setBlob(buffer);
        return tile;
    }

    private ConveyorTile finalizeTile(ConveyorTile tile) {
        if (tile.getStatus() == 0 && !tile.getError()) {
            tile.setStatus(200);
        }

        if (tile.servletResp != null) {
            setExpirationHeader(tile.servletResp, (int) tile.getTileIndex()[2]);
        }

        return tile;
    }

    protected void saveExpirationInformation(int backendExpire) {
        this.saveExpirationHeaders = false;

        try {
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireCacheList.set(0, new ExpirationRule(0, 7200));
                    log.log(
                            Level.SEVERE,
                            "Layer profile wants MaxAge from backend,"
                                    + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireCacheList.set(backendExpire, new ExpirationRule(0, 7200));
                }
                log.finer("Setting expireCache to: " + expireCache);
            }
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireClientsList.set(0, new ExpirationRule(0, 7200));
                    log.log(
                            Level.SEVERE,
                            "Layer profile wants MaxAge from backend,"
                                    + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireClientsList.set(0, new ExpirationRule(0, backendExpire));
                    log.finer("Setting expireClients to: " + expireClients);
                }
            }
        } catch (Exception e) {
            // Sometimes this doesn't work (network conditions?),
            // and it's really not worth getting caught up on it.
            log.log(Level.FINE, e.getMessage(), e);
        }
    }

    public Map<String, String> getWMSRequestTemplate(MimeType responseFormat, RequestType reqType) {
        Map<String, String> params = new HashMap<>();
        FormatModifier mod = getFormatModifier(responseFormat);

        params.put("SERVICE", "WMS");

        String request;
        if (reqType == RequestType.MAP) {
            request = "GetMap";
        } else { // if(reqType == RequestType.FEATUREINFO) {
            request = "GetFeatureInfo";
        }
        params.put("REQUEST", request);

        String version = wmsVersion;
        if (wmsVersion == null) {
            version = "1.1.1";
        }
        params.put("VERSION", version);

        String layers;
        if (this.wmsLayers != null && this.wmsLayers.length() != 0) {
            layers = wmsLayers;
        } else {
            layers = getName();
        }
        params.put("LAYERS", layers);

        if (reqType == RequestType.FEATUREINFO) {
            params.put("QUERY_LAYERS", wmsQueryLayers != null ? wmsQueryLayers : layers);
        }

        String exceptions;
        if (errorMime != null) {
            exceptions = errorMime;
        } else {
            exceptions = XMLMime.ogcxml.getMimeType();
        }
        params.put("EXCEPTIONS", exceptions);

        String styles = "";
        if (wmsStyles != null && wmsStyles.length() != 0) {
            styles = wmsStyles;
        }
        params.put("STYLES", styles);

        if (reqType == RequestType.MAP) {
            Boolean tmpTransparent = transparent;

            if (mod != null && mod.getTransparent() != null) {
                tmpTransparent = mod.getTransparent();
            }

            if (tmpTransparent == null || tmpTransparent) {
                params.put("TRANSPARENT", "TRUE");
            } else {
                params.put("TRANSPARENT", "FALSE");
            }

            String tmpBgColor = bgColor;
            if (mod != null && mod.getBgColor() != null) {
                tmpBgColor = mod.getBgColor();
            }

            if (tmpBgColor != null && tmpBgColor.length() != 0) {
                params.put("BGCOLOR", tmpBgColor);
            }

            String tmpPalette = palette;
            if (mod != null && mod.getPalette() != null) {
                tmpPalette = mod.getPalette();
            }

            if (tmpPalette != null && tmpPalette.length() != 0) {
                params.put("PALETTE", tmpPalette);
            }
        }

        if (vendorParameters != null && vendorParameters.length() != 0) {
            String[] vparams = vendorParameters.split("&");
            for (String vp : vparams) {
                if (vp.length() > 0) {
                    String[] split = vp.split("=");
                    String key = split[0];
                    String val = split[1];
                    if (key.length() > 0) {
                        params.put(key, val);
                    }
                }
            }
        }

        return params;
    }

    /**
     * Get the WMS backend URL that should be used next according to the round robin.
     *
     * @return the next URL
     */
    protected String nextWmsURL() {
        curWmsURL = (curWmsURL + 1) % wmsUrl.length;
        return wmsUrl[curWmsURL];
    }

    public long[][] getZoomedInGridLoc(String gridSetId, long[] gridLoc) throws GeoWebCacheException {
        return null;
    }

    public void setErrorMime(String errormime) {
        this.errorMime = errormime;
    }

    public void addMetaWidthHeight(int w, int h) {
        this.metaWidthHeight[0] = w;
        this.metaWidthHeight[1] = h;
    }

    public void setWMSurl(String[] wmsurl) {
        this.wmsUrl = wmsurl;
    }

    public String[] getWMSurl() {
        return this.wmsUrl;
    }

    public String getWmsLayers() {
        return wmsLayers;
    }

    public void setWmsLayers(String wmsLayers) {
        this.wmsLayers = wmsLayers;
    }

    public String getWmsQueryLayers() {
        return wmsQueryLayers;
    }

    public String getHttpPassword() {
        return httpPassword;
    }

    public String getHttpUsername() {
        return httpUsername;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    /** Mandatory */
    public void setSourceHelper(WMSSourceHelper source) {
        log.fine("Setting sourceHelper on " + this.name);
        this.sourceHelper = source;
        if (concurrency != null) {
            this.sourceHelper.setConcurrency(concurrency);
        } else {
            this.sourceHelper.setConcurrency(32);
        }
        if (backendTimeout != null) {
            this.sourceHelper.setBackendTimeout(backendTimeout);
        } else {
            this.sourceHelper.setBackendTimeout(120);
        }
    }

    public WMSSourceHelper getSourceHelper() {
        return sourceHelper;
    }

    public void setVersion(String version) {
        this.wmsVersion = version;
    }

    public void setTiled(boolean tiled) {
        this.tiled = tiled;
    }

    public boolean getTransparent() {
        if (transparent == null || transparent) {
            return true;
        } else {
            return false;
        }
    }

    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    public int[] getBackgroundColor() {
        if (bgColor == null || transparent != null && transparent) {
            return null;
        }
        int[] ret = new int[3];
        // 0xRRGGBB
        ret[0] = Integer.parseInt(bgColor.substring(2, 4), 16);
        ret[1] = Integer.parseInt(bgColor.substring(4, 6), 16);
        ret[2] = Integer.parseInt(bgColor.substring(6, 8), 16);
        return ret;
    }

    @Override
    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {

        // Should we do mime type checks?

        // note: not using getImageBuffer() here cause this method is not called during seeding, so
        // there's no gain
        Resource buffer = new ByteArrayResource(2048);
        sourceHelper.makeRequest(tile, buffer);
        tile.setBlob(buffer);

        return tile;
    }

    public String backendSRSOverride(SRS srs) {
        if (sphericalMercatorOverride != null && srs.equals(SRS.getEPSG3857())) {
            return sphericalMercatorOverride;
        } else {
            return srs.toString();
        }
    }

    public void cleanUpThreadLocals() {
        WMS_BUFFER.remove();
        WMS_BUFFER2.remove();
    }

    public void setMetaInformation(LayerMetaInformation layerMetaInfo) {
        this.metaInformation = layerMetaInfo;
    }

    public void setMetadataURLs(List<MetadataURL> metadataURLs) {
        this.metadataURLs = metadataURLs;
    }

    @Override
    public String getStyles() {
        return wmsStyles;
    }

    public void setLockProvider(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    @Override
    public void proxyRequest(ConveyorTile tile) throws GeoWebCacheException {
        String queryStr = tile.servletReq.getQueryString();
        String serverStr = getWMSurl()[0];

        try {
            URL url;
            if (serverStr.contains("?")) {
                url = URLs.of(serverStr + "&" + queryStr);
            } else {
                url = URLs.of(serverStr + queryStr);
            }

            WMSSourceHelper helper = getSourceHelper();
            if (!(helper instanceof WMSHttpHelper)) {
                throw new GeoWebCacheException("Can only proxy if WMS Layer is backed by an HTTP backend");
            }

            try (ClassicHttpResponse httpResponse =
                    ((WMSHttpHelper) helper).executeRequest(url, null, getBackendTimeout(), getHttpRequestMode())) {
                HttpEntity entity = httpResponse.getEntity();
                try (InputStream is = entity.getContent()) {
                    HttpServletResponse response = tile.servletResp;
                    org.apache.hc.core5.http.Header contentType = httpResponse.getFirstHeader("Content-Type");
                    if (contentType != null) {
                        response.setContentType(contentType.getValue());
                        String contentEncoding = entity.getContentEncoding();
                        if (!MimeType.isBinary(contentType.getValue())) {
                            response.setCharacterEncoding(contentEncoding);
                        }
                    }

                    int read = 0;
                    byte[] data = new byte[1024];

                    while (read > -1) {
                        read = is.read(data);
                        if (read > -1) {
                            response.getOutputStream().write(data, 0, read);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            tile.servletResp.setStatus(500);
            log.log(Level.SEVERE, ioe.getMessage());
        }
    }

    public LegendsRawInfo getLegends() {
        return legends;
    }

    public void setLegends(LegendsRawInfo legends) {
        this.legends = legends;
    }

    @Override
    public Map<String, org.geowebcache.config.legends.LegendInfo> getLayerLegendsInfo() {
        String layerName = wmsLayers == null ? getName() : wmsLayers;
        return legends == null
                ? super.getLayerLegendsInfo()
                : legends.getLegendsInfo(layerName, wmsUrl != null && wmsUrl.length > 0 ? wmsUrl[0] : null);
    }

    /** The request mode used for this layer, defaults to {@link HttpRequestMode#Get} if not set in the configuration */
    public HttpRequestMode getHttpRequestMode() {
        return httpRequestMode == null ? HttpRequestMode.Get : httpRequestMode;
    }
}
