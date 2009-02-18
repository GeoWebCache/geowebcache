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
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorKMLTile;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.storage.StorageBroker;

public class KMLSiteMap {
    private ConveyorKMLTile tile = null;
    private TileLayerDispatcher tLD = null;
    private StorageBroker storageBroker;
    
    public KMLSiteMap(ConveyorKMLTile tile, TileLayerDispatcher tLD) {
        this.tile = tile;
        this.tLD = tLD;
        this.storageBroker = tile.getStorageBroker();
    }
    
    public void write() throws GeoWebCacheException, IOException {
        tile.servletResp.setCharacterEncoding("utf-8");
        tile.servletResp.setContentType("application/xml");
        tile.servletResp.setStatus(200);
        
        if(tile.getHint() == KMLService.HINT_SITEMAP_LAYER) {
            writeSiteMap();
        } else {
            writeSiteMapIndex();
        }
    }
    
    private void writeSiteMapIndex() throws IOException {
        OutputStream os = tile.servletResp.getOutputStream();
        
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n";
        os.write(header.getBytes());
        
        writeSiteMapIndexLoop();
        
        String footer = "</sitemapindex>";
        os.write(footer.getBytes());
    }
    
    private void writeSiteMapIndexLoop() throws IOException {
        OutputStream os = tile.servletResp.getOutputStream();
        String urlPrefix = tile.getUrlPrefix();
        
        Iterator<TileLayer> iter = tLD.getLayers().values().iterator();
        
        while(iter.hasNext()) {
            TileLayer tl = iter.next();
            
            // May have to initialize
            tl.isInitialized();
            
            Hashtable<SRS,Grid> grids = tl.getGrids();
            List<MimeType> mimeTypes = tl.getMimeTypes();
            
            if( grids != null && grids.containsKey(SRS.getEPSG4326())
                    && mimeTypes != null && mimeTypes.contains(XMLMime.kml) ) {
                String smStr = "<sitemap><loc>"+urlPrefix+tl.getName()+"/sitemap.xml</loc></sitemap>";
                os.write(smStr.getBytes());
            }
        }
    }

    private void writeSiteMap() throws GeoWebCacheException, IOException {
        TileLayer layer = tile.getLayer();
        
        writeSiteMapHeader();
        
        int[] gridLoc = layer.getZoomedOutGridLoc(SRS.getEPSG4326());
        
        if(gridLoc[2] < 0) {
            int[] gridLocWest = {0,0,0};
            int[] gridLocEast = {1,0,0};
            
            writeSiteMapLoop(gridLocWest);
            writeSiteMapLoop(gridLocEast);
            
        } else {
            writeSiteMapLoop(gridLoc);
        }
        
        writeSiteMapFooter();
    }
    
    private void writeSiteMapHeader() throws IOException {
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        		"<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:geo=\"http://www.google.com/geo/schemas/sitemap/1.0\">\n";
        tile.servletResp.getOutputStream().write(header.getBytes());
    }

    private void writeSiteMapFooter() throws IOException {
        String footer = "</urlset>";
        tile.servletResp.getOutputStream().write(footer.getBytes());       
    }

    public void writeSiteMapLoop(int[] gridLoc) throws GeoWebCacheException, IOException {
        OutputStream os = tile.servletResp.getOutputStream();
        SRS srs = SRS.getEPSG4326();
        TileLayer tileLayer = tile.getLayer();
        String urlPrefix = tile.getUrlPrefix();
        
        // Add a link to the super overlay first
        String  superOverlayLoc = 
            "<url><loc>" + urlPrefix.substring(0, urlPrefix.length() - 1) 
            + ".kml.kml</loc><geo:geo><geo:format>kml</geo:format></geo:geo></url>\n";
        os.write(superOverlayLoc.getBytes());
        
        
        LinkedList<int[]> subTileList = new LinkedList<int[]>();
            
        subTileList.addFirst(gridLoc);
        
        while(subTileList.peek() != null) {
            int[] curLoc = subTileList.removeFirst();
            int[][] linkGridLocs = tileLayer.getZoomInGridLoc(srs, curLoc);
            linkGridLocs = KMZHelper.filterGridLocs(storageBroker, tileLayer, XMLMime.kml, linkGridLocs);
         
            // Save the links we still need to follow for later
            for(int[] subTile : linkGridLocs) {
                if(subTile[2] > 0) {
                    subTileList.addLast(subTile);
                }
            }
            
            // We need to link to the data tiles only, for now
            String tmp = "<url><loc>" + urlPrefix + KMLService.gridLocString(curLoc) +".kml" + "</loc><geo:geo><geo:format>kml</geo:format></geo:geo></url>\n";
            os.write(tmp.getBytes());
            
            // Could add priority as 1 / (zoomlevel + 1)
        }
     }
}
