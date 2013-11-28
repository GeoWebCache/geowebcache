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
 * @author Arne Kepp / OpenGeo, Copyright 2009
 */
package org.geowebcache.service.tms;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.URLMangler;

/**
 * Basic implementation of the TMS documents. Not all of GWCs more advanced
 * features can easily be accomodated by this service.
 * 
 * The commented out sections are optional parts of the OSGeo standard
 */
public class TMSDocumentFactory {
    
    TileLayerDispatcher tld;
    
    GridSetBroker gsb;
    
    String baseUrl;

    private final String contextPath;

    private final URLMangler urlMangler;
    
    protected TMSDocumentFactory(TileLayerDispatcher tld, GridSetBroker gsb, String baseUrl,
            String contextPath, URLMangler urlMangler) {
        this.tld = tld;
        this.gsb = gsb;
        this.baseUrl = baseUrl;
        this.contextPath = contextPath;
        this.urlMangler = urlMangler;
    }
    
    protected String getTileMapServiceDoc() {
        StringBuilder str = new StringBuilder();
        str.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        str.append("<TileMapService version=\"1.0.0\" services=\""+urlMangler.buildURL(baseUrl, contextPath, "")+"\">\n");
        // TODO can have these set through Spring
        str.append("  <Title>Tile Map Service</Title>\n");
        str.append("  <Abstract>A Tile Map Service served by GeoWebCache</Abstract>\n");
        //TODO Optional stuff, note that there is some meta data stuff on the 
        // TileLayer object that we simply don't use yet
        
        // <KeywordList>example tile service</KeywordList>
        // <ContactInformation>
        //   <ContactPersonPrimary>
        //     <ContactPerson>Paul Ramsey</ContactPerson>
        //     <ContactOrganization>Refractions Research</ContactOrganization>
        //   </ContactPersonPrimary>
        //   <ContactPosition>Manager</ContactPosition>
        //   <ContactAddress>
        //     <AddressType>postal</AddressType>
        //     <Address>300 - 1207 Douglas Street</Address>
        //     <City>Victoria</City>
        //     <StateOrProvince>British Columbia</StateOrProvince>
        //     <PostCode>V8W2E7</PostCode>
        //     <Country>Canada</Country>
        //   </ContactAddress>
        //   <ContactVoiceTelephone>12503833022</ContactVoiceTelephone>
        //   <ContactFacsimileTelephone>12503832140</ContactFacsimileTelephone>
        //   <ContactElectronicMailAddress>pramsey@refractions.net</ContactElectronicMailAddress>
        // </ContactInformation>
        str.append("  <TileMaps>\n");
        Iterable<TileLayer> iter = tld.getLayerList();
        for (TileLayer layer : iter) {
            if(!layer.isEnabled()){
                continue;
            }
            tileMapsForLayer(str, layer);
        }
        str.append("  </TileMaps>\n");
        str.append("</TileMapService>\n");
        
        return str.toString();
    }
    
    private void tileMapsForLayer(StringBuilder str, TileLayer layer) {
        for(String gridSetId : layer.getGridSubsets()){
            GridSubset gridSub = layer.getGridSubset(gridSetId);
            for(MimeType mimeType : layer.getMimeTypes()) {
                // GridSubset gridSub = iter.next();
                str.append("    <TileMap\n");
                str.append("      title=\"").append(tileMapTitle(layer)).append("\"\n");
                str.append("      srs=\"").append(gridSub.getSRS().toString()).append("\"\n");
                str.append("      profile=\"");
                str.append(profileForGridSet(gridSub.getGridSet()));
                str.append("\"\n");
                str.append("      href=\"").append(tileMapUrl(layer, gridSub, mimeType)).append("\" />\n");
            }
        }
    }
    
    protected String getTileMapDoc(TileLayer layer, GridSubset gridSub, GridSetBroker gsb, MimeType mimeType) {
        StringBuilder str = new StringBuilder();
        str.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        str.append("<TileMap version=\"1.0.0\" tilemapservice=\""+ urlMangler.buildURL(baseUrl, contextPath, "/service/tms/1.0.0") + "\">\n");
        str.append("  <Title>").append(tileMapTitle(layer)).append("</Title>\n");
        str.append("  <Abstract>").append(tileMapDescription(layer)).append("</Abstract>\n");
       // <KeywordList></KeywordList>
       // <Metadata type="TC211" mime-type="text/xml" href="http://www.org" />
       // <Attribution>
       //   <Title>National Geospatial Intelligence Agency</Title>
       //   <Logo width="10" height="10" href="http://nga.mil/logo.gif" mime-type="image/gif" />
       // </Attribution>
       // <WebMapContext href="http://wms.org" />
       // <Face>0</Face>
        
        // Check with tschaub whether we actually have to provide this as OSGEO:40041
        // No.
        str.append("  <SRS>").append(gridSub.getSRS().toString()).append("</SRS>\n");
        double[] coords = gridSub.getCoverageBestFitBounds().getCoords();
        str.append("  <BoundingBox minx=\"").append(coords[0]);
        str.append("\" miny=\"").append(coords[1]);
        str.append("\" maxx=\"").append(coords[2]);
        str.append("\" maxy=\"").append(coords[3]).append("\" />\n");
        str.append("  <Origin x=\"").append(coords[0]).append("\" y=\"").append(coords[1]).append("\" />\n");
        // Can we have multiple formats? NO
        str.append("  <TileFormat width=\"").append(gridSub.getTileWidth());
        str.append("\" height=\"").append(gridSub.getTileHeight());
        str.append("\" mime-type=\""+mimeType.getMimeType()+"\" extension=\""+mimeType.getFileExtension()+"\" />\n");
        str.append("  <TileSets profile=\"");
        str.append(profileForGridSet(gridSub.getGridSet()));
        str.append("\">\n");
        double[] resolutions = gridSub.getResolutions();
        int resIdx = 0;
       
        for(int zoom = gridSub.getZoomStart(); zoom <= gridSub.getZoomStop(); zoom++) {
            str.append("    <TileSet href=\"");
            str.append(tileMapUrl(layer, gridSub, mimeType, zoom));
            str.append("\" units-per-pixel=\"").append(resolutions[resIdx]);
            str.append("\" order=\"").append(resIdx).append("\"/>\n");
            resIdx++;
        }
        
        str.append("  </TileSets>\n");
        str.append("</TileMap>\n");
        
        return str.toString();
    }
    
    private String profileForGridSet(GridSet gridSet) {
        if(gridSet == gsb.WORLD_EPSG4326) {
            return "global-geodetic";
        } else if(gridSet == gsb.WORLD_EPSG3857) {
            return "global-mercator";
        } else {
            return "local";
        }
    }
    
    private String tileMapUrl(TileLayer tl, GridSubset gridSub, MimeType mimeType) {
        // TODO add XML escaping
        return urlMangler.buildURL(baseUrl, contextPath, "/service/tms/1.0.0/" + tileMapName(tl,gridSub,mimeType));
    }
    
    private String tileMapUrl(TileLayer tl, GridSubset gridSub, MimeType mimeType, int z) {
        return tileMapUrl(tl, gridSub, mimeType) + "/" + z;
    }
    
    private String tileMapName(TileLayer tl, GridSubset gridSub, MimeType mimeType) {
        try {
            String name = URLEncoder.encode(tl.getName(), "UTF-8");
            String gridSubset = URLEncoder.encode(gridSub.getName(), "UTF-8");
            return name + "@" + gridSubset + "@" + mimeType.getFileExtension();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
    private String tileMapTitle(TileLayer tl) {
        LayerMetaInformation metaInfo = tl.getMetaInformation();
        if(metaInfo != null && metaInfo.getTitle() != null) {
            return metaInfo.getTitle();
        }
        
        return tl.getName();
    }
    private String tileMapDescription(TileLayer tl) {
        LayerMetaInformation metaInfo = tl.getMetaInformation();
        if(metaInfo != null && metaInfo.getDescription() != null) {
            return metaInfo.getDescription();
        }
        
        return "";
    }
}
