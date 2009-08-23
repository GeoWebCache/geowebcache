package org.geowebcache.service.wmts;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.meta.ServiceContact;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.config.meta.ServiceProvider;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.mime.MimeType;

public class WMTSGetCapabilities {
    
    private static Log log = LogFactory.getLog(WMTSGetCapabilities.class);
    
    private TileLayerDispatcher tld;
    
    private GridSetBroker gsb;
    
    private String baseUrl;
    
    protected WMTSGetCapabilities(TileLayerDispatcher tld, GridSetBroker gsb, HttpServletRequest servReq) {
        this.tld = tld;
        this.gsb = gsb;
        // TODO Fix
        this.baseUrl = servReq.getRequestURL().toString() + "?SERVICE=WMS&amp;";
    }
    
    protected void writeResponse(HttpServletResponse response) {
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
        str.append("<Capabilities xmlns=\"http://www.opengis.net/wmsts/1.0\"\n");
        str.append("xmlns:ows=\"http://www.opengis.net/ows/1.1\"\n"); 
        str.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n");
        str.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        str.append("xmlns:gml=\"http://www.opengis.net/gml\" ");
        str.append("xsi:schemaLocation=\"http://www.opengis.net/wmts/1.0 http://schemas.opengis.net/wmts/1.0.0/wmtsGetCapabilities_response.xsd\"\n"); 
        str.append("xmlns:ows=\"http://opengis.net/ows");
        str.append("version=\"1.0.0\">\n");
        
        serviceIdentification(str);
        serviceProvider(str);
        operationsMetadata(str,"http://www.maps.cat/cgi-bin/MiraMon5_0.cgi?");
        contents(str, baseUrl);
        str.append("<ServiceMetadataURL xlink:href=\"http://www.maps.cat/wmts/1.0.0/WMTSCapabilities.xml\"/>\n");
        str.append("</Capabilities>");

        return str.toString();
    }

    private void serviceIdentification(StringBuilder str) {
        ServiceInformation servInfo = tld.getServiceInformation();
        
        str.append("<ows:ServiceIdentification>\n");
        
        if (servInfo != null) {
            appendTag(str, "  ", "ows:Title", servInfo.title, "Web Map Tile Service - GeoWebCache");
            appendTag(str, "  ", "ows:Abstract", servInfo.description, null);
            
            if (servInfo != null && servInfo.keywords != null) {
                str.append("  <ows:Keywords>\n");
                Iterator<String> keywordIter = servInfo.keywords.iterator();
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
            appendTag(str, "  ", "ows:Fees", servInfo.fees, null);
            appendTag(str, "  ", "ows:AccessConstraints", servInfo.accesConstraints, null);
        }

        str.append("</ows:ServiceIdentification>\n");
    }
    
    private void serviceProvider(StringBuilder str) {
        ServiceInformation servInfo = tld.getServiceInformation();
        ServiceProvider servProv = null;
        if(servInfo != null) {
            servProv = servInfo.serviceProvider;
        }
        str.append("<ows:ServiceProvider>\n");
        
        if(servProv != null) {
            appendTag(str, "  ", "ows:ProviderName", servProv.providerName, null);
            // This is an xlink ,,, appendTag(str, "  ", "ows:ProviderSite", servProv.providerSite, null);
            
            ServiceContact servCont = servProv.serviceContact;
            if(servCont != null) {
                str.append("  <ows:ServiceContact>\n");
                appendTag(str, "    ", "ows:IndividualName", servCont.individualName, null);
                appendTag(str, "    ", "ows:PositionName", servCont.positionName, null);
                str.append("    <ows:ContactInfo>\n");
                
                if(servCont.phoneNumber != null || servCont.faxNumber != null) {
                    str.append("      <ows:Phone>\n");
                    appendTag(str, "      ", "ows:Voice", servCont.phoneNumber, null);
                    appendTag(str, "      ", "ows:Facsimile", servCont.faxNumber, null);
                    str.append("      </ows:Phone>\n");
                }
                
                str.append("      <ows:Address>\n");
                appendTag(str, "      ", "ows:DeliveryPoint", servCont.addressStreet, null);
                appendTag(str, "      ", "ows:City", servCont.addressCity, null);
                appendTag(str, "      ", "ows:AdministrativeArea", servCont.addressAdministrativeArea, null);
                appendTag(str, "      ", "ows:PostalCode", servCont.addressPostalCode, null);
                appendTag(str, "      ", "ows:Country", servCont.addressCountry, null);
                appendTag(str, "      ", "ows:ElectronicMailAddress", servCont.addressEmail, null);
                str.append("      </ows:Address>\n");
                
                str.append("    </ows:ContactInfo>\n");
                str.append("  </ows:ServiceContact>\n");
            }
        }
            
        str.append("</ows:ServiceProvider>\n"); 
    }
        
    private void operationsMetadata(StringBuilder str, String baseUrl) {
        str.append("<ows:OperationsMetadata>\n");
        operation(str, "GetCapabilities", baseUrl);
        operation(str, "GetTile", baseUrl);
        str.append("<ows:OperationsMetadata>\n");
    }
        
     private void operation(StringBuilder str, String operationName, String baseUrl) {
        str.append("  <ows:Operation name=\""+operationName+"\">\n");
        str.append("    <ows:DCP>\n");
        str.append("      <ows:HTTP>\n");
        str.append("        <ows:Get xlink:href=\""+baseUrl+"\">\n");
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
     
     private void contents(StringBuilder str, String baseUrl) {
         str.append("<Contents>\n");
         Iterator<TileLayer> iter = tld.getLayers().values().iterator();
         while(iter.hasNext()) {
             layer(str, iter.next(), baseUrl);
         }
         
         Iterator<GridSet> gridSetIter = gsb.getGridSets().values().iterator();
         while(gridSetIter.hasNext()) {
             tileMatrixSet(str, gridSetIter.next());
         }
         
         str.append("</Contents>\n");
     }
     
     private void layer(StringBuilder str, TileLayer layer, String baseurl) {
         str.append("  <Layer>\n");
         LayerMetaInformation layerMeta = layer.getMetaInformation();
         
         if(layerMeta == null) {
             appendTag(str, "    ", "ows:Title", layer.getName(), null);
         } else {
             appendTag(str, "    ", "ows:Title", layerMeta.getTitle(), null);
             appendTag(str, "    ", "ows:Abstract", layerMeta.getDescription(), null);
         }

         layerWGS84BoundingBox(str, layer);
         appendTag(str, "    ", "ows:Identifier", layer.getName(), null);
         layerStyle(str, layer);
         layerFormats(str, layer);
         layerGridSubSets(str, layer);
         // TODO REST str.append("    <ResourceURL format=\"image/png\" resourceType=\"tile\" template=\"http://www.maps.cat/wmts/BlueMarbleNextGeneration/default/BigWorldPixel/{TileMatrix}/{TileRow}/{TileCol}.png\"/>\n");
         str.append("  </Layer>\n");
     }
     
     private void layerWGS84BoundingBox(StringBuilder str, TileLayer layer) {
         // TODO this is optional, but quite useful
         //str.append("    <ows:WGS84BoundingBox>\n");
         //str.append("      <ows:LowerCorner>-180 -90</ows:LowerCorner>\n");
         //str.append("      <ows:UpperCorner>180 90</ows:UpperCorner>\n");
         //str.append("    </ows:WGS84BoundingBox>\n");
     }
     
     private void layerStyle(StringBuilder str, TileLayer layer) {
         //str.append("    <Style isDefault=\"true\">\n");
         //str.append("      <ows:Identifier>Default</ows:Identifier>\n");
         //str.append("    </Style>\n");
     }
     
     private void layerFormats(StringBuilder str, TileLayer layer) {
         Iterator<MimeType> mimeIter = layer.getMimeTypes().iterator();
         
         while(mimeIter.hasNext()){
             str.append("    <Format>"+mimeIter.next().getFormat()+"</Format>\n");
         }
     }
     
     private void layerGridSubSets(StringBuilder str, TileLayer layer) {
         str.append("    <TileMatrixSetLink>");
         Iterator<GridSubSet> gridSubSets = layer.getGridSubSets().values().iterator();
         while(gridSubSets.hasNext()) {
             GridSubSet gridSubSet = gridSubSets.next();
             str.append("      <TileMatrixSet>" + gridSubSet.getName() + "</TileMatrixSet>\n");
             
             String[] levelNames = gridSubSet.getGridNames();
             long[][] wmtsLimit = gridSubSet.getWMTSCoverages();
             
             str.append("      <TileMatrixSetLimits>\n");
             for(int i=0; i < levelNames.length; i++) {
                 str.append("        <TileMatrix>"+levelNames[i]+"</TileMatrix>\n");
                 str.append("        <MinTileRow>"+wmtsLimit[1]+"</MinTileRow>\n");
                 str.append("        <MaxTileRow>"+wmtsLimit[3]+"</MaxTileRow>\n");
                 str.append("        <MinTileCol>"+wmtsLimit[0]+"</MinTileCol>\n");
                 str.append("        <MaxTileCol>"+wmtsLimit[2]+"</MaxTileCol>\n");
             }
             str.append("      </TileMatrixSetLimits>\n");
         }
         str.append("    </TileMatrixSetLink>");     
     }
     
     private void tileMatrixSet(StringBuilder str, GridSet gridSet) {
         str.append("  <TileMatrixSet>\n");
         str.append("    <ows:Identifier>"+gridSet.getName()+"</ows:Identifier>\n");
         // If the following is not good enough, please get in touch and we will try to fix it :)
         str.append("    <ows:SupportedCRS>urn:ogc:def:crs:EPSG::"+gridSet.getSRS().getNumber()+"</ows:SupportedCRS>\n");
         // TODO detect these str.append("    <WellKnownScaleSet>urn:ogc:def:wkss:GlobalCRS84Pixel</WellKnownScaleSet>\n");
         Grid[] grids = gridSet.getGrids();
         for(int i=0; i<grids.length; i++) {
             tileMatrix(str, grids[i], gridSet.getTileWidth(), gridSet.getTileHeight());
         }
         
         str.append("  </TileMatrixSet>\n");
     }
     
     private void tileMatrix(StringBuilder str, Grid grid, int tileWidth, int tileHeight) {
         str.append("    <TileMatrix>\n");
         str.append("      <ows:Identifier>"+grid.getName()+"</ows:Identifier>\n");         
         str.append("      <ScaleDenominator>"+grid.getScale()+"</ScaleDenominator>\n");
         str.append("      <TileWidth>"+tileWidth+"</TileWidth>\n");    
         str.append("      <TileHeight>"+tileHeight+"</TileHeight>\n");      
         str.append("      <MatrixWidth>"+grid.getExtent()[0]+"</MatrixWidth>\n");    
         str.append("      <MatrixHeight>"+grid.getExtent()[1]+"</MatrixHeight>\n");    
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
     
     private String encodeXmlChars(String input) {
         return input
             .replaceAll("&", "&amp;")
             .replaceAll("%", "&#37;")
             .replaceAll("<", "&lt;")
             .replaceAll(">", "&gt;");
     }
}
