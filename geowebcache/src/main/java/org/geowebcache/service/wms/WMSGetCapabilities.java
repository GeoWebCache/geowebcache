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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.service.wms;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.wms.BBOX;

public class WMSGetCapabilities {
    
    private static Log log = LogFactory.getLog(WMSGetCapabilities.class);
    
    private TileLayerDispatcher tld;
    
    private String urlStr;
    
    protected WMSGetCapabilities(TileLayerDispatcher tld, HttpServletRequest servReq) {
        this.tld = tld;
        urlStr = servReq.getRequestURL().toString() + "?SERVICE=WMS&amp;";
    }
    
    protected void writeReponse(HttpServletResponse response) {
        
        byte[] data = generateGetCapabilities().getBytes();

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/vnd.ogc.wms_xml");
        response.setContentLength(data.length);
        
        try {
            OutputStream os = response.getOutputStream();
            os.write(data);
            os.flush();
        } catch (IOException ioe) {
            log.debug("Caught IOException" + ioe.getMessage());
        }
    }

    private String generateGetCapabilities() {
        StringBuilder str = new StringBuilder();
        
        str.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        str.append("<!DOCTYPE WMT_MS_Capabilities SYSTEM \"http://schemas.opengis.net/wms/1.1.1/capabilities_1_1_1.dtd\">\n");
        str.append("<WMT_MS_Capabilities version=\"1.1.1\">\n");
        
        // The actual meat
        service(str);
        capability(str);
        
        str.append("</WMT_MS_Capabilities>\n");
        
        return str.toString();
    }
        
    private void service(StringBuilder str) {
        str.append("<Service>\n");
        str.append("  <Name>OGC:WMS</Name>\n");
        str.append("  <Title>GeoWebCache</Title>\n");
        
        str.append("  <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""+urlStr+"\"/>\n");
        str.append("</Service>\n");
    }
    
    private void capability(StringBuilder str) {
        str.append("<Capability>\n");
        str.append("  <Request>\n");
          capabilityRequestGetCapabilities(str);
          capabilityRequestGetMap(str);
          capabilityRequestGetFeatureInfo(str);
          capabilityRequestDescribeLayer(str);
          capabilityRequestGetLegendGraphic(str);
        str.append("  </Request>\n");
        capabilityException(str);
        capabilityVendorSpecific(str);
        capabilityLayerOuter(str);
        str.append("</Capability>\n");
        
    }
    
    private void capabilityRequestGetCapabilities(StringBuilder str) {
        str.append("    <GetCapabilities>\n");
        str.append("      <Format>application/vnd.ogc.wms_xml</Format>\n");
        str.append("      <DCPType>\n");
        str.append("        <HTTP>\n");
        str.append("          <Get>\n");
        str.append("            <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""+urlStr+"\"/>\n");        
        str.append("          </Get>\n");
        str.append("        </HTTP>\n");
        str.append("      </DCPType>\n");
        str.append("    </GetCapabilities>\n");
    }
    
    private void capabilityRequestGetMap(StringBuilder str) {
        // Find all the formats we support
        Iterator<TileLayer> layerIter = tld.getLayers().values().iterator();
        
        HashSet<String> formats = new HashSet<String>();
        
        while(layerIter.hasNext()) {
            TileLayer layer = layerIter.next();
            if(layer.getMimeTypes() != null) {
                Iterator<MimeType> mimeIter = layer.getMimeTypes().iterator();
                while(mimeIter.hasNext()) {
                    MimeType mime = mimeIter.next();
                    formats.add(mime.getFormat());
                }
            } else {
                formats.add("image/png");
                formats.add("image/jpeg");
            }

        }
        
        str.append("    <GetMap>\n");
        Iterator<String> formatIter = formats.iterator();
        while(formatIter.hasNext()) {
            str.append("      <Format>"+formatIter.next()+"</Format>\n");
        }
        str.append("      <DCPType>\n");
        str.append("        <HTTP>\n");
        str.append("          <Get>\n");
        str.append("            <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""+urlStr+"\"/>\n");        
        str.append("          </Get>\n");
        str.append("        </HTTP>\n");
        str.append("      </DCPType>\n");
        str.append("    </GetMap>\n");        
    }
    
    private void capabilityRequestGetFeatureInfo(StringBuilder str) {
        str.append("    <GetFeatureInfo>\n");
        str.append("      <Format>text/plain</Format>\n");
        str.append("      <Format>text/html</Format>\n");
        str.append("      <Format>application/vnd.ogc.gml</Format>\n");
        str.append("      <DCPType>\n");
        str.append("        <Get>\n");
        str.append("          <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""+urlStr+"\"/>\n");
        str.append("        </Get>\n");
        str.append("      </DCPType>\n");
        str.append("    </GetFeatureInfo>\n");
        
    }

    private void capabilityRequestDescribeLayer(StringBuilder str) {
        str.append("    <DescribeLayer>\n");
        str.append("      <Format>application/vnd.ogc.wms_xml</Format>\n");
        str.append("      <DCPType>\n");
        str.append("        <Get>\n");
        str.append("          <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""+urlStr+"\"/>\n");
        str.append("        </Get>\n");
        str.append("      </DCPType>\n");
        str.append("    </DescribeLayer>\n");
    }
    
    private void capabilityRequestGetLegendGraphic(StringBuilder str) {
        str.append("    <GetLegendGraphic>\n");
        str.append("      <Format>image/png</Format>\n");
        str.append("      <Format>image/jpeg</Format>\n");
        str.append("      <Format>image/gif</Format>\n");
        str.append("      <DCPType>\n");
        str.append("        <Get>\n");
        str.append("          <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""+urlStr+"\"/>\n");
        str.append("        </Get>\n");
        str.append("      </DCPType>\n");
        str.append("    </GetLegendGraphic>\n");
        
    }
        
    private void capabilityException(StringBuilder str) {
        str.append("  <Exception>\n");
        str.append("    <Format>application/vnd.ogc.se_xml</Format>\n");
        str.append("  </Exception>\n");
    }
    
    
    private void capabilityVendorSpecific(StringBuilder str) {
        str.append("  <VendorSpecificCapabilities>\n");
        Iterator<TileLayer> layerIter = tld.getLayers().values().iterator();
        while(layerIter.hasNext()) {
            TileLayer layer = layerIter.next();
            Iterator<GridSubSet> gridIter = layer.getGridSubSets().values().iterator();

            while(gridIter.hasNext()) {
                GridSubSet grid = gridIter.next();
                
                Iterator<MimeType> mimeIter = null;
                
                if(layer.getMimeTypes() != null) {
                    mimeIter = layer.getMimeTypes().iterator();
                } else {
                    ArrayList<MimeType> arList = new ArrayList<MimeType>();
                    arList.add(ImageMime.png);
                    arList.add(ImageMime.jpeg);
                    mimeIter = arList.iterator();
                }
                
                while(mimeIter.hasNext()) {
                    try {
                        capabilityVendorSpecificTileset(str, layer, grid, mimeIter.next().getFormat());
                    } catch (GeoWebCacheException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        }
        str.append("  </VendorSpecificCapabilities>\n");   
    }
    
    private void capabilityVendorSpecificTileset(StringBuilder str, TileLayer layer, GridSubSet grid, String formatStr) 
    throws GeoWebCacheException {
        String srsStr = grid.getSRS().toString();
        StringBuilder resolutionsStr = new StringBuilder();
        double[] res = grid.getResolutions();
        for(int i=0; i<res.length; i++) {
           resolutionsStr.append(Double.toString(res[i]) + " ");
        }
        
        String[] bs = boundsPrep(grid.getCoverageBestFitBounds());
        
        str.append("    <TileSet>\n");
        str.append("      <SRS>"+srsStr+"</SRS>\n");        
        str.append("      <BoundingBox srs=\""+srsStr+"\" minx=\""+bs[0]+"\" miny=\""+bs[1]+"\"  maxx=\""+bs[2]+"\"  maxy=\""+bs[3]+"\" />\n");        
        str.append("      <Resolutions>"+resolutionsStr.toString()+"</Resolutions>\n");
        // TODO softcode
        str.append("      <Width>256</Width>\n");
        str.append("      <Height>256</Height>\n");
        str.append("      <Format>"+formatStr+"</Format>\n"); 
        str.append("      <Layers>"+layer.getName()+"</Layers>\n");
        // TODO ignoring styles for now
        str.append("      <Styles></Styles>\n");
        str.append("    </TileSet>\n");
    }

    private void capabilityLayerOuter(StringBuilder str) {
        str.append("  <Layer>\n");
        str.append("    <Title>GeoWebCache WMS</Title>\n");
        str.append("    <Abstract>Note that GeoWebCache does not provide full WMS services.</Abstract>\n");
        str.append("    <LatLonBoundingBox minx=\"-180.0\" miny=\"-90.0\" maxx=\"180.0\" maxy=\"90.0\"/>\n");
        
        Iterator<TileLayer> layerIter = tld.getLayers().values().iterator();
        while(layerIter.hasNext()) {
            TileLayer layer = layerIter.next();
            try {
                capabilityLayerInner(str, layer);
            } catch (GeoWebCacheException e) {
                log.error(e.getMessage());
            }
        }
        
        str.append("  </Layer>\n");
    }
    
    private void capabilityLayerInner(StringBuilder str, TileLayer layer) throws GeoWebCacheException {
        if(layer instanceof WMSLayer && ((WMSLayer) layer).isQueryable()) {
            str.append("    <Layer queryable=\"1\">\n");
        } else {
            str.append("    <Layer>\n");
        }

        str.append("      <Name>"+layer.getName()+"</Name>\n");
        str.append("      <Title>"+layer.getName()+"</Title>\n");
        
        Iterator<GridSubSet> gridSetIter = layer.getGridSubSets().values().iterator();
        TreeSet<SRS> srsSet = new TreeSet<SRS>();
        StringBuilder boundingBoxStr = new StringBuilder();
        while(gridSetIter.hasNext()) {
            GridSubSet curGridSubSet = gridSetIter.next();
            SRS curSRS = curGridSubSet.getSRS();
            if(! srsSet.contains(curSRS)) {
                str.append("      <SRS>"+curSRS.toString()+"</SRS>\n");
                
                // Save bounding boxes for later
                String[] bs = boundsPrep(curGridSubSet.getCoverageBestFitBounds());
                boundingBoxStr.append("      <BoundingBox SRS=\"" + curGridSubSet.getSRS().toString() +"\" minx=\""+bs[0]+"\" miny=\""+bs[1]+"\" maxx=\""+bs[2]+"\" maxy=\""+bs[3]+"\"/>\n");    
                
                srsSet.add(curSRS);
            }
        }
        
        GridSubSet epsg4326GridSubSet = layer.getGridSubSetForSRS(SRS.getEPSG4326());
        if(null != epsg4326GridSubSet) {
            String[] bs = boundsPrep(epsg4326GridSubSet.getCoverageBestFitBounds());
            str.append("      <LatLonBoundingBox minx=\""+bs[0]+"\" miny=\""+bs[1]+"\" maxx=\""+bs[2]+"\" maxy=\""+bs[3]+"\"/>\n");    
        }
        
        // Bounding boxes gathered earlier
        str.append(boundingBoxStr);
        
        // TODO style?
        str.append("    </Layer>\n");
    }
    
    String[] boundsPrep(BBOX bbox) {
        String[] bs = { 
                Double.toString(bbox.coords[0]), 
                Double.toString(bbox.coords[1]), 
                Double.toString(bbox.coords[2]), 
                Double.toString(bbox.coords[3]) };
        return bs;
    }
}
