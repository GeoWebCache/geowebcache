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
 * 
 */
package org.geowebcache.service.wmts;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.meta.ServiceContact;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.config.meta.ServiceProvider;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.mime.MimeType;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class WMTSGetCapabilities {
    
    private static Log log = LogFactory.getLog(WMTSGetCapabilities.class);
    
    private TileLayerDispatcher tld;
    
    private GridSetBroker gsb;
    
    private String baseUrl;
    
    protected WMTSGetCapabilities(TileLayerDispatcher tld, GridSetBroker gsb, HttpServletRequest servReq, String baseUrl,
            String contextPath, URLMangler urlMangler) {
        this.tld = tld;
        this.gsb = gsb;

        String forcedBaseUrl = ServletUtils.stringFromMap(servReq.getParameterMap(), servReq.getCharacterEncoding(), "base_url");

        if(forcedBaseUrl!=null) {
            // This should prevent anyone from passing in anything nasty
            this.baseUrl = encodeXmlChars(forcedBaseUrl);
        } else {
            this.baseUrl = urlMangler.buildURL(baseUrl, contextPath, WMTSService.SERVICE_PATH);
        }
        
    }
    
    protected void writeResponse(HttpServletResponse response, RuntimeStats stats) {
        byte[] data = generateGetCapabilities().getBytes();

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/vnd.ogc.wms_xml");
        response.setCharacterEncoding("UTF-8");
        response.setContentLength(data.length);
        response.setHeader("content-disposition", "inline;filename=wmts-getcapabilities.xml");
        
        stats.log(data.length, CacheResult.OTHER);
        
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
        str.append("<Capabilities xmlns=\"http://www.opengis.net/wmts/1.0\"\n");
        str.append("xmlns:ows=\"http://www.opengis.net/ows/1.1\"\n"); 
        str.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n");
        str.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        str.append("xmlns:gml=\"http://www.opengis.net/gml\" ");
        str.append("xsi:schemaLocation=\"http://www.opengis.net/wmts/1.0 http://schemas.opengis.net/wmts/1.0/wmtsGetCapabilities_response.xsd\"\n"); 
        // There were some contradictions in the draft schema, haven't checked whether they've fixed those
        //str.append("xsi:schemaLocation=\"http://www.opengis.net/wmts/1.0 http://geowebcache.org/schema/opengis/wmts/1.0.0/wmtsGetCapabilities_response.xsd\"\n"); 
        str.append("version=\"1.0.0\">\n");
        
        serviceIdentification(str);
        serviceProvider(str);
        operationsMetadata(str);
        contents(str);
        str.append("<ServiceMetadataURL xlink:href=\""+baseUrl+"?REQUEST=getcapabilities&amp;VERSION=1.0.0\"/>\n");
        str.append("</Capabilities>");

        return str.toString();
    }

    private void serviceIdentification(StringBuilder str) {
        ServiceInformation servInfo = tld.getServiceInformation();
        
        str.append("<ows:ServiceIdentification>\n");
        
        if (servInfo != null) {
            appendTag(str, "  ", "ows:Title", servInfo.getTitle(), "Web Map Tile Service - GeoWebCache");
            appendTag(str, "  ", "ows:Abstract", servInfo.getDescription(), null);
            
            if (servInfo != null && servInfo.getKeywords() != null) {
                str.append("  <ows:Keywords>\n");
                Iterator<String> keywordIter = servInfo.getKeywords().iterator();
                while(keywordIter.hasNext()) {
                    appendTag(str, "    ", "ows:Keyword", keywordIter.next(), null);
                }
                str.append("  </ows:Keywords>\n");
            }
        } else {
            str.append("  <ows:Title>Web Map Tile Service - GeoWebCache</ows:Title>\n");
        }

        str.append("  <ows:ServiceType>OGC WMTS</ows:ServiceType>\n");
        str.append("  <ows:ServiceTypeVersion>1.0.0</ows:ServiceTypeVersion>\n");
        
        if (servInfo != null) {
            appendTag(str, "  ", "ows:Fees", servInfo.getFees(), null);
            appendTag(str, "  ", "ows:AccessConstraints", servInfo.getAccessConstraints(), null);
        }

        str.append("</ows:ServiceIdentification>\n");
    }
    
    private void serviceProvider(StringBuilder str) {
        ServiceInformation servInfo = tld.getServiceInformation();
        ServiceProvider servProv = null;
        if(servInfo != null) {
            servProv = servInfo.getServiceProvider();
        }
        str.append("<ows:ServiceProvider>\n");
        
        if(servProv != null) {
            appendTag(str, "  ", "ows:ProviderName", servProv.getProviderName(), null);
            
            if(servProv.getProviderSite() != null) {
                appendXlink(str, "  ", "ows:ProviderSite", servProv.getProviderSite());
            }
            
            ServiceContact servCont = servProv.getServiceContact();
            if(servCont != null) {
                str.append("  <ows:ServiceContact>\n");
                appendTag(str, "    ", "ows:IndividualName", servCont.getIndividualName(), null);
                appendTag(str, "    ", "ows:PositionName", servCont.getPositionName(), null);
                str.append("    <ows:ContactInfo>\n");
                
                if(servCont.getPhoneNumber() != null || servCont.getFaxNumber() != null) {
                    str.append("      <ows:Phone>\n");
                    appendTag(str, "      ", "ows:Voice", servCont.getPhoneNumber(), null);
                    appendTag(str, "      ", "ows:Facsimile", servCont.getFaxNumber(), null);
                    str.append("      </ows:Phone>\n");
                }
                
                str.append("      <ows:Address>\n");
                appendTag(str, "      ", "ows:DeliveryPoint", servCont.getAddressStreet(), null);
                appendTag(str, "      ", "ows:City", servCont.getAddressCity(), null);
                appendTag(str, "      ", "ows:AdministrativeArea", servCont.getAddressAdministrativeArea(), null);
                appendTag(str, "      ", "ows:PostalCode", servCont.getAddressPostalCode(), null);
                appendTag(str, "      ", "ows:Country", servCont.getAddressCountry(), null);
                appendTag(str, "      ", "ows:ElectronicMailAddress", servCont.getAddressEmail(), null);
                str.append("      </ows:Address>\n");
                
                str.append("    </ows:ContactInfo>\n");
                str.append("  </ows:ServiceContact>\n");
            }
        } else {
            appendTag(str, "  ", "ows:ProviderName", baseUrl, null);
            appendXlink(str, "  ", "ows:ProviderSite", baseUrl);
            str.append("  <ows:ServiceContact>\n");
            appendTag(str, "    ", "ows:IndividualName", "GeoWebCache User", null);
            str.append("  </ows:ServiceContact>\n");
        }
            
        str.append("</ows:ServiceProvider>\n"); 
    }
    
    private void operationsMetadata(StringBuilder str) {
        str.append("<ows:OperationsMetadata>\n");
        operation(str, "GetCapabilities", baseUrl);
        operation(str, "GetTile", baseUrl);
        operation(str, "GetFeatureInfo", baseUrl);
        str.append("</ows:OperationsMetadata>\n");
    }
        
     private void operation(StringBuilder str, String operationName, String baseUrl) {
        str.append("  <ows:Operation name=\""+operationName+"\">\n");
        str.append("    <ows:DCP>\n");
        str.append("      <ows:HTTP>\n");
        str.append("        <ows:Get xlink:href=\""+baseUrl+"?\">\n");
        str.append("          <ows:Constraint name=\"GetEncoding\">\n");
        str.append("            <ows:AllowedValues>\n");
        str.append("              <ows:Value>KVP</ows:Value>\n");
        str.append("            </ows:AllowedValues>\n");
        str.append("          </ows:Constraint>\n");
        str.append("        </ows:Get>\n");
        str.append("      </ows:HTTP>\n");
        str.append("    </ows:DCP>\n");
        str.append("  </ows:Operation>\n");
     }
     
     private void contents(StringBuilder str) {
         str.append("<Contents>\n");
         Iterable<TileLayer> iter = tld.getLayerList();
        for (TileLayer layer : iter) {
            if (!layer.isEnabled()) {
                continue;
            }
            layer(str, layer, baseUrl);
        }
         
        for (GridSet gset : gsb.getGridSets()) {
            tileMatrixSet(str, gset);
        }
         
         str.append("</Contents>\n");
     }
     
     private void layer(StringBuilder str, TileLayer layer, String baseurl) {
        str.append("  <Layer>\n");
        LayerMetaInformation layerMeta = layer.getMetaInformation();

        if (layerMeta == null) {
            appendTag(str, "    ", "ows:Title", layer.getName(), null);
        } else {
            appendTag(str, "    ", "ows:Title", layerMeta.getTitle(), null);
            appendTag(str, "    ", "ows:Abstract", layerMeta.getDescription(), null);
        }

        layerWGS84BoundingBox(str, layer);
        appendTag(str, "    ", "ows:Identifier", layer.getName(), null);
        
        // We need the filters for styles and dimensions
        List<ParameterFilter> filters = layer.getParameterFilters();
        
        layerStyles(str, layer, filters);
        
        layerFormats(str, layer);
        
        layerInfoFormats(str, layer);
        
        if(filters != null) {
            layerDimensions(str, layer, filters);
        }
        
        layerGridSubSets(str, layer);
        // TODO REST
        // str.append("    <ResourceURL format=\"image/png\" resourceType=\"tile\" template=\"http://www.maps.cat/wmts/BlueMarbleNextGeneration/default/BigWorldPixel/{TileMatrix}/{TileRow}/{TileCol}.png\"/>\n");
        str.append("  </Layer>\n");
    }
     
    private void layerWGS84BoundingBox(StringBuilder str, TileLayer layer) {
        GridSubset subset = layer.getGridSubsetForSRS(SRS.getEPSG4326());
        if(subset != null) {
            double[] coords = subset.getOriginalExtent().getCoords();
            str.append("    <ows:WGS84BoundingBox>\n");
            str.append("      <ows:LowerCorner>"+coords[0]+" "+coords[1]+"</ows:LowerCorner>\n");
            str.append("      <ows:UpperCorner>"+coords[2]+" "+coords[3]+"</ows:UpperCorner>\n");
            str.append("    </ows:WGS84BoundingBox>\n");   
        }
     }
     
     private void layerStyles(StringBuilder str, TileLayer layer, List<ParameterFilter> filters) {
         String defStyle = layer.getStyles();
         if(filters == null) {
             str.append("    <Style isDefault=\"true\">\n");
             str.append("      <ows:Identifier>"+TileLayer.encodeDimensionValue(defStyle)+"</ows:Identifier>\n");
             str.append("    </Style>\n");
         } else {
             ParameterFilter stylesFilter = null;
             Iterator<ParameterFilter> iter = filters.iterator();
             while(stylesFilter == null && iter.hasNext()) {
                 ParameterFilter filter = iter.next();
                 if(filter.getKey().equalsIgnoreCase("STYLES")) {
                     stylesFilter = filter;
                 }
             }
             
             if(stylesFilter != null) {
                 String defVal = stylesFilter.getDefaultValue(); 
                 if(defVal == null) {
                     if(defStyle != null) {
                         defVal = defStyle;
                     } else {
                         defVal = "";
                     }
                 }
                 
                 Iterator<String> valIter = stylesFilter.getLegalValues().iterator();
                 while(valIter.hasNext()) {
                     String value = valIter.next();
                         if(value.equals(defVal)) {
                             str.append("    <Style isDefault=\"true\">\n");
                         } else {
                             str.append("    <Style>\n");
                         }
                         str.append("      <ows:Identifier>"+TileLayer.encodeDimensionValue(value)+"</ows:Identifier>\n");
                         str.append("    </Style>\n");
                 }
             }
         }
     }
     
     private void layerFormats(StringBuilder str, TileLayer layer) {
         Iterator<MimeType> mimeIter = layer.getMimeTypes().iterator();
         
         while(mimeIter.hasNext()){
             str.append("    <Format>"+mimeIter.next().getFormat()+"</Format>\n");
         }
     }
     
     private void layerInfoFormats(StringBuilder str, TileLayer layer) {
         if (layer.isQueryable()) {
             Iterator<MimeType> mimeIter = layer.getInfoMimeTypes().iterator();
        	 while (mimeIter.hasNext()) {
        		 str.append("    <InfoFormat>"+mimeIter.next().getFormat()+"</InfoFormat>\n");
        	 }
         }
     }
     
     private void layerDimensions(StringBuilder str, TileLayer layer, List<ParameterFilter> filters) {
         
         Iterator<ParameterFilter> iter = filters.iterator();
         
         while(iter.hasNext()) {
             ParameterFilter filter = iter.next();
             
             if(! filter.getKey().equalsIgnoreCase("STYLES")) {
                 List<String> values = filter.getLegalValues();
             
                 if(values != null) {
                     dimensionDescription(str, filter, values);
                 }
             }
         }
     }
         
     private void dimensionDescription(StringBuilder str, ParameterFilter filter, List<String> values) {
         str.append("    <Dimension>");
         str.append("      <Identifier>"+filter.getKey()+"</Identifier>");
         String defaultStr = TileLayer.encodeDimensionValue(filter.getDefaultValue());
         str.append("      <Default>"+encodeXmlChars(defaultStr)+"</Default>");
         
         Iterator<String> iter = values.iterator();
         while(iter.hasNext()) {
             String value = TileLayer.encodeDimensionValue(iter.next());
             str.append("      <Value>"+encodeXmlChars(value)+"</Value>");
         }
         str.append("    </Dimension>");
     }
     
      
     private void layerGridSubSets(StringBuilder str, TileLayer layer) {

        for (String gridSetId : layer.getGridSubsets()) {
            GridSubset gridSubset = layer.getGridSubset(gridSetId);
         
             str.append("    <TileMatrixSetLink>\n");
             str.append("      <TileMatrixSet>" + gridSubset.getName() + "</TileMatrixSet>\n");
             
             if (! gridSubset.fullGridSetCoverage()) {
                String[] levelNames = gridSubset.getGridNames();
                long[][] wmtsLimits = gridSubset.getWMTSCoverages();

                str.append("      <TileMatrixSetLimits>\n");
                for (int i = 0; i < levelNames.length; i++) {
                    str.append("        <TileMatrixLimits>\n");
                    str.append("          <TileMatrix>" + levelNames[i] + "</TileMatrix>\n");
                    str.append("          <MinTileRow>" + wmtsLimits[i][1] + "</MinTileRow>\n");
                    str.append("          <MaxTileRow>" + wmtsLimits[i][3] + "</MaxTileRow>\n");
                    str.append("          <MinTileCol>" + wmtsLimits[i][0] + "</MinTileCol>\n");
                    str.append("          <MaxTileCol>" + wmtsLimits[i][2] + "</MaxTileCol>\n");
                    str.append("        </TileMatrixLimits>\n");
                }
                str.append("      </TileMatrixSetLimits>\n");
            }
            str.append("    </TileMatrixSetLink>\n");
         }
     }
     
     private void tileMatrixSet(StringBuilder str, GridSet gridSet) {
         str.append("  <TileMatrixSet>\n");
         str.append("    <ows:Identifier>"+gridSet.getName()+"</ows:Identifier>\n");
         // If the following is not good enough, please get in touch and we will try to fix it :)
         str.append("    <ows:SupportedCRS>urn:ogc:def:crs:EPSG::"+gridSet.getSrs().getNumber()+"</ows:SupportedCRS>\n");
         // TODO detect these str.append("    <WellKnownScaleSet>urn:ogc:def:wkss:GlobalCRS84Pixel</WellKnownScaleSet>\n");
         Grid[] grids = gridSet.getGridLevels();
         for(int i=0; i<grids.length; i++) {
             double[] tlCoordinates = gridSet.getOrderedTopLeftCorner(i);
             tileMatrix(str, grids[i], tlCoordinates, gridSet.getTileWidth(), gridSet.getTileHeight(), gridSet.isScaleWarning());
         }
         str.append("  </TileMatrixSet>\n");
     }
     
     private void tileMatrix(StringBuilder str, Grid grid, double[] tlCoordinates, int tileWidth, int tileHeight, boolean scaleWarning) {
         str.append("    <TileMatrix>\n");
         if(scaleWarning) {
             str.append("      <ows:Abstract>The grid was not well-defined, the scale therefore assumes 1m per map unit.</ows:Abstract>");
         }
         str.append("      <ows:Identifier>"+grid.getName()+"</ows:Identifier>\n");
         str.append("      <ScaleDenominator>"+grid.getScaleDenominator()+"</ScaleDenominator>\n");
         str.append("      <TopLeftCorner>"+ tlCoordinates[0] +" "+ tlCoordinates[1] +"</TopLeftCorner>\n");
         str.append("      <TileWidth>"+tileWidth+"</TileWidth>\n");    
         str.append("      <TileHeight>"+tileHeight+"</TileHeight>\n");      
         str.append("      <MatrixWidth>"+grid.getNumTilesWide()+"</MatrixWidth>\n");    
         str.append("      <MatrixHeight>"+grid.getNumTilesHigh()+"</MatrixHeight>\n");    
         str.append("    </TileMatrix>\n");
     }
     
     private void appendTag(StringBuilder str, String padding, String tagName, String value, String defaultValue) {
         if(defaultValue == null && value == null) {
             return;
         }
         
         String escapedValue;
         if(value == null) {
             escapedValue = defaultValue;
         } else {
             escapedValue = encodeXmlChars(value);
         }

         str.append(padding + "<"+tagName+">"+escapedValue+"</"+tagName+">\n");         
     }
     
     private void appendXlink(StringBuilder str, String padding, String tagName, String xlink) {         
         String escapedValue = encodeXmlChars(xlink);

         str.append(padding + "<"+tagName+" xlink:href=\""+escapedValue+"\" />\n");         
     }
     
     private String encodeXmlChars(String input) {
         return input
             .replaceAll("&", "&amp;")
             .replaceAll("%", "&#37;")
             .replaceAll("<", "&lt;")
             .replaceAll(">", "&gt;");
     }
}
