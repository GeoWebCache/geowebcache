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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service.kml;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.layer.BadTileException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.wms.BBOX;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * 
 * Creates a grid of tiles and puts the grid index on each of them
 * 
 */
public class KMLDebugGridLayer extends TileLayer implements Cache, CacheKey {

    public static final String LAYERNAME = "debugGrid";
    
    public static final int IS_KMZ = 100;
    
    //private static Log log = LogFactory.getLog(org.geowebcache.service.kml.KMLDebugGridLayer.class);
    
    private static KMLDebugGridLayer instance;
    
    
    
    //temporary hack
    public void lazyLayerInitialization(CacheFactory c){
        //blah

    }

    private KMLDebugGridLayer() {
        super.grids = new Hashtable<SRS,Grid>();
        grids.put(SRS.getEPSG4326(), new Grid(SRS.getEPSG4326(),BBOX.WORLD4326, BBOX.WORLD4326, null));
    }
    
    synchronized static public KMLDebugGridLayer getInstance() {
        if(instance == null) {
            instance = new KMLDebugGridLayer();
        }
        return instance;
    }
    
    public void acquireLayerLock() {
    }

    public void destroy() {
    }

    public Tile doNonMetatilingRequest(Tile tile) throws GeoWebCacheException {
        return null;
    }

    public BBOX getBounds(SRS srs) {
        return new BBOX(-180.0, -90.0, 180.0, 90.0);
    }

    public Cache getCache() {
        return null;
    }

    public CacheKey getCacheKey() {
        return this;
    }

    public String getCachePrefix() {
        return null;
    }

    public MimeType getDefaultMimeType() {
        return null;
    }

    public int[] getMetaTilingFactors() {
        return null;
    }

    public List <MimeType> getMimeTypes() {
        return null;
    }

    public String getName() {
        return "Debug grid";
    }

    public SRS[] getProjections() {
        SRS[] srsList = { SRS.getEPSG4326() };
        return srsList;
    }

    public double[] getResolutions(int srsIdx) {
        return null;
    }

    public Tile getTile(Tile tile)
            throws GeoWebCacheException, IOException {        
        int[] gridLoc = tile.getTileIndex();

        BBOX bbox = this.getBboxForGridLoc(SRS.getEPSG4326(), gridLoc);
       
        String data  = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://earth.google.com/kml/2.1\">\n"
                + "<Document>\n"
                //+"<!-- Name>DocumentName</Name --->"
                +"<Placemark id=\"PlaceMarkId\">\n"
                //+"<styleUrl>#square</styleUrl>\n"
                +"<name>"+gridLoc[0]+ ","+gridLoc[1]+","+gridLoc[2]+"</name>"
                +"<Style id=\"square\">\n"
                +"<PolyStyle><color>7fffffff</color><colorMode>random</colorMode>\n"  
                +"</PolyStyle>\n"
                +"<IconStyle><Icon><href>http://icons.opengeo.org/dynamic/circle/aaffaa_aaffaa_2.png</href></Icon></IconStyle>\n"
                +"<LabelStyle id=\"name\"><color>ffffffff</color><colorMode>normal</colorMode><scale>1.0</scale></LabelStyle>\n"
                +"</Style>\n"
                +"<MultiGeometry>\n"
                +"<Point><coordinates>"+((bbox.coords[0]+bbox.coords[2])/2) 
                +","+ ((bbox.coords[1]+bbox.coords[3])/2) 
                +",0</coordinates></Point>\n"
                +"<Polygon><outerBoundaryIs><LinearRing>\n"
                +"<coordinates decimal=\".\" cs=\",\" ts=\" \">\n"
                + bbox.coords[0] +","+ bbox.coords[1] + " "
                + bbox.coords[2] +","+ bbox.coords[1] + " "
                + bbox.coords[2] +","+ bbox.coords[3] + " "
                + bbox.coords[0] +","+ bbox.coords[3]
                +"</coordinates>\n"
                +"</LinearRing></outerBoundaryIs></Polygon>\n"
                +"</MultiGeometry>\n"
                +"</Placemark>\n"
                + "</Document>\n"
                + "</kml>";
        
        tile.setContent(data.getBytes());
        tile.setStatus(200);
        return tile;
    }

    public String getStyles() {
        return null;
    }

    public int[][] getZoomInGridLoc(SRS srs, int[] gridLoc) {
        //log.warn("done - getZoomInGridLoc(srsIdx, gridLoc)");
        
        int[][] retVal = new int[4][3];
        
        int x = gridLoc[0] * 2;
        int y = gridLoc[1] * 2;
        int z = gridLoc[2] + 1;
        
        // Don't link to tiles past the last zoomLevel
        if(z > 25) {
            z = -1;
        }
        
        // Now adjust where appropriate
        retVal[0][0] = retVal[2][0] = x;
        retVal[1][0] = retVal[3][0] = x + 1;
        
        retVal[0][1] = retVal[1][1] = y;
        retVal[2][1] = retVal[3][1] = y + 1;

        retVal[0][2] = retVal[1][2] = retVal[2][2] = retVal[3][2] = z;
        
        return retVal;
    }

    public int getZoomStart() {
        return 0;
    }

    public int getZoomStop() {
        return 25;
    }

    public Boolean initialize() {
        return true;
    }

    public Boolean isInitialized() {
        return true;
    }

    public void putTile(Tile tile, Object ck, int[] gridLoc)
            throws CacheException {
    }

    public void releaseLayerLock() {
    }

    public void setExpirationHeader(HttpServletResponse response) {
    }

    public String supportsBbox(SRS srs, BBOX bounds)
            throws GeoWebCacheException {
        return null;
    }

    public boolean supportsFormat(String formatStr) throws GeoWebCacheException {
        return false;
    }

    public boolean supportsSRS(SRS srs) throws GeoWebCacheException {
        return false;
    }

    public boolean get(Tile tile, long ttl) throws CacheException {

        return true;
    }

    public String getDefaultKeyBeanId() {

        return null;
    }

    public String getDefaultPrefix(String param) throws CacheException {

        return null;
    }

    
    public void init(Properties props) throws CacheException {

        
    }

    public boolean remove(Tile tile) throws CacheException {

        return false;
    }

    public void removeAll() throws CacheException {

        
    }

    /** Cache interface **/
    public void set(Object key, Object obj, long ttl) throws CacheException {

        
    }

    public void setDefaultKeyBeanId(String defaultKeyBeanId) {

        
    }

    public void setUp(String cachePrefix) throws CacheException {

        
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {

        
    }
//
//    public Object createKey(Tile tile) {
//        Vector<Integer> lst = new Vector<Integer>();
//        if(tile.getMimeType() == XMLMime.kmz) {
//            lst.add(KMLDebugGridLayer.IS_KMZ);
//        } else {
//            lst.add(0);
//        }
//        int[] tileIndex = tile.getTileIndex();
//        lst.add(tileIndex[0]);
//        lst.add(tileIndex[1]);
//        lst.add(tileIndex[2]);  
//        
//        return (Object) lst;
//    }

    public int getType() {

        return 0;
    }

    public void init() {

        
    }

    public void putTile(Tile tile) throws CacheException {

        
    }

    public void set(Tile tile, long ttl) throws CacheException {

        
    }

    public boolean tryCacheFetch(Tile tile) {

        return false;
    }

    public boolean get(CacheKey keyProto, Tile tile, long ttl)
            throws CacheException, GeoWebCacheException {

        return false;
    }

    public boolean remove(CacheKey keyProto, Tile tile) throws CacheException {
        return false;
    }

    public void set(CacheKey keyProto, Tile tile, long ttl)
            throws CacheException, GeoWebCacheException {
    }

    public Object createKey(Tile tile) {
        return null;
    }

    public void setCacheFactory(CacheFactory cacheFactory) {
    }

    public BBOX getBboxForGridLoc(SRS srs, int[] gridLoc) {
        double tileWidth = 180.0 / Math.pow(2, gridLoc[2]);

        BBOX bbox = new BBOX(
                     -180.0 + tileWidth * gridLoc[0],
                     -90.0 + tileWidth * gridLoc[1],
                     -180.0 + tileWidth * (gridLoc[0] + 1),
                     -90.0 + tileWidth * (gridLoc[1] + 1));
        
        return bbox;
    }

    public int[][] getCoveredGridLevels(SRS srs, BBOX bounds) {
        return null;
    }

    public int[] getGridLocForBounds(SRS srs, BBOX bounds)
            throws BadTileException {
        return null;
    }

    public int[] getZoomedOutGridLoc(SRS srs) {
        // log.warn("done - getZoomedOutGridLoc");
        int[] zoomedOutGridLoc = new int[3];
        zoomedOutGridLoc[0] = -1;
        zoomedOutGridLoc[1] = -1;
        zoomedOutGridLoc[2] = -1;

        return zoomedOutGridLoc;

    }

    public void seedTile(Tile tile, boolean tryCache)
            throws GeoWebCacheException, IOException {
        
    }


    public int truncate(TileLayer tl, SRS srs, int zoomStart, int zoomStop,
            int[][] bounds, MimeType mimeType) throws CacheException {
        // TODO Auto-generated method stub
        return 0;
    }

}
