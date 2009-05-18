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
 * 
 */
package org.geowebcache.security.wms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.wms.WMSRequests;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.wms.Dimension;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class AuthWMSRequests extends WMSRequests {
    private TileLayerDispatcher tileLayerDispatcher;
    private Tile tile;

    private static final String NS_GWC = "http://geowebcache.org/";

    private DataAccessManager dataAccessManager;
    private String version;

    public AuthWMSRequests(DataAccessManager dataAccessManager) {
        this.dataAccessManager = dataAccessManager;
    }

    public void handleGetCapabilities(TileLayerDispatcher tLD,
            Tile t, String version) throws GeoWebCacheException {
        this.version = version;
        tileLayerDispatcher = tLD;
        tile = t;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        OutputFormat of = new OutputFormat("XML","UTF-8",true);
        of.setIndent(1);
        of.setIndenting(true);
        //		<!DOCTYPE WMT_MS_Capabilities SYSTEM
        //			 "http://schemas.opengis.net/wms/1.1.1/WMS_MS_Capabilities.dtd" 
        //			[
        //				<!ELEMENT VendorSpecificCapabilities EMPTY>
        //			]>
        if (AuthWMSService.WMS_VERSION_1_1_1.equals(version)){
            of.setDoctype(null, "http://schemas.opengis.net/wms/1.1.1/WMS_MS_Capabilities.dtd");
        } 

        XMLSerializer serializer = new XMLSerializer(outputStream, of);
        ContentHandler handler;
        try {
            handler = serializer.asContentHandler();
            handler.startDocument();
            writeWMT_MS_Capabilities(handler);
            handler.endDocument();

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            writeData(tile.servletResp, outputStream.toByteArray());
        } catch (IOException ioe) {
            throw new GeoWebCacheException("Error doing getCapabilities: "
                    + ioe.getMessage());
        }
    }

    /**
     * <WMT_MS_Capabilities version="1.1.1" updateSequence="250">
     * 
     * <WMS_Capabilities 
     **  version="1.3.0" 
     *  xmlns="http://www.opengis.net/wms"
     **  xmlns:xlink="http://www.w3.org/1999/xlink"
     **  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     **  xsi:schemaLocation="http://www.opengis.net/wms http://schemas.opengis.net/wms/1.3.0/capabilities_1_3_0.xsd">
     * @param handler
     * @throws SAXException
     */
    private void writeWMT_MS_Capabilities(ContentHandler handler) throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        if (AuthWMSService.WMS_VERSION_1_1_1.equals(version)) {
            atts.addAttribute(NS_GWC, "version", "version", "String", AuthWMSService.WMS_VERSION_1_1_1);
        } else if (AuthWMSService.WMS_VERSION_1_3_0.equals(version)){
            atts.addAttribute(NS_GWC, "version", "version", "String", AuthWMSService.WMS_VERSION_1_3_0);
            atts.addAttribute(NS_GWC, "xmlns", "xmlns", "String", "http://www.opengis.net/wms");
            atts.addAttribute(NS_GWC, "xsi:schemaLocation", "xsi:schemaLocation", "String", "http://www.opengis.net/wms http://schemas.opengis.net/wms/1.3.0/capabilities_1_3_0.xsd");
        }

        handler.startPrefixMapping("xlink", "http://www.w3.org/1999/xlink");
        handler.startPrefixMapping("xsi", "http://www.w3.org/2001/XMLSchema-instance");

        handler.startElement(NS_GWC,"","WMT_MS_Capabilities",atts);
        writeService(handler);
        writeCapability(handler);
        handler.endElement(NS_GWC, "WMT_MS_Capabilities", "WMT_MS_Capabilities");
        handler.endPrefixMapping("xsi");
        handler.endPrefixMapping("xlink");
    }

    /**
     * 	<Service>
     * 		<Name>MetOc WMS</Name>
     * 		<Title></Title>
     * 		<OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://yourhost.com/wms"/>
     * 	</Service>
     */
    private void writeService(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"Service","Service", null);
        writeSimpleElement(handler, "Name", "GeoWebCache");
        writeSimpleElement(handler, "Title", null);

        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute(NS_GWC, "type", "xlink:type", "String", "simple");
        String url = tile.servletReq.getRequestURL().toString();
        atts.addAttribute(NS_GWC, "href", "xlink:href", "String", url);

        handler.startElement(NS_GWC,"","OnlineResource", atts);
        handler.endElement(NS_GWC,"","OnlineResource");

        handler.endElement(NS_GWC, "Service", "Service");
    }

    /**	<Capability>
     * 		<Request>...</Request>
     *     	<Exception>...</Exception>
     *		<Layer>...</Layer>
     *		<VendorSpecificCapabilities>...</VendorSpecificCapabilities>
     *  </Capability>
     */
    private void writeCapability(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"Capability","Capability", null);
        writeRequest(handler);
        writeException(handler);
        writeLayers(handler);
        //writeVendorSpecificCapabilities(handler);
        handler.endElement(NS_GWC, "Capability", "Capability");
    }

    /**	<Request>
     * 		<GetCapabilities>...</GetCapabilities>
     * 		<GetMap>...</GetMap>
     * 		<GetFeatureInfo>...</GetFeatureInfo>
     * 	</Request>
     */
    private void writeRequest(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"Request","Request", null);
        writeGetCapability(handler);
        writeGetMap(handler);
        handler.endElement(NS_GWC, "Request", "Request");
    }

    /**	<Exception>
     * 		<Format>...</Format>
     * 	</Exception>
     */
    private void writeException(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"Exception","Exception", null);
        writeSimpleElement(handler, "Format", "application/vnd.ogc.se_xml");
        writeSimpleElement(handler, "Format", "application/vnd.ogc.se_inimage");
        writeSimpleElement(handler, "Format", "application/vnd.ogc.se_blank");
        handler.endElement(NS_GWC, "Exception", "Exception");
    }

    private void writeLayers(ContentHandler handler) throws SAXException {
        Map<String, TileLayer> layerMap = tileLayerDispatcher.getLayers();
        Iterator<TileLayer> iter = layerMap.values().iterator();

        while (iter.hasNext()) {
            TileLayer layer = iter.next();
            if (userCanSeeLayer(SecurityContextHolder.getContext().getAuthentication(), layer)) {
                layer.isInitialized();
                writeLayer(handler, layer);
            }
        }
    }

    /** <Layer>
     *          <Title>...</Title>
     *          <Name>...</Name>
     *          <Abstract>...</Abstract>
     *          <Style>...</Style>
     *  </Layer>
     */
    private void writeLayer(ContentHandler handler, TileLayer layer) throws SAXException {
        handler.startElement(NS_GWC, "Layer", "Layer", null);
        writeSimpleElement(handler, "Name", layer.getName());
        writeSimpleElement(handler, "Title", layer.getTitle());
        if (layer.get_abstract() != null) {
            writeSimpleElement(handler, "Abstract", layer.get_abstract());
        }

        try {
            Grid grid = layer.getGrid(SRS.getEPSG4326());
            String[] latLongBounds = doublesToStrings(grid.getBounds().coords);
            String minx = latLongBounds[0];
            String miny = latLongBounds[1];
            String maxx = latLongBounds[2];
            String maxy = latLongBounds[3];

            AttributesImpl srsAtts = new AttributesImpl();
            srsAtts.addAttribute(NS_GWC, "minx", "minx", "String", minx);
            srsAtts.addAttribute(NS_GWC, "miny", "miny", "String", miny);
            srsAtts.addAttribute(NS_GWC, "maxx", "maxx", "String", maxx);
            srsAtts.addAttribute(NS_GWC, "maxy", "maxy", "String", maxy);
            handler.startElement(NS_GWC,"LatLonBoundingBox","LatLonBoundingBox", srsAtts);
            handler.endElement(NS_GWC,"LatLonBoundingBox","LatLonBoundingBox");

        } catch (Exception e) {
            // TODO Auto-generated catch block
        }

        Iterator<Grid> iter = layer.getGrids().values().iterator();
        while (iter.hasNext()) {
            Grid grid = iter.next();
            String srs = grid.getSRS().toString();
            writeSimpleElement(handler, "SRS", srs);

            String[] strBounds = doublesToStrings(grid.getBounds().coords);
            String minx = strBounds[0];
            String miny = strBounds[1];
            String maxx = strBounds[2];
            String maxy = strBounds[3];

            AttributesImpl srsAtts = new AttributesImpl();
            srsAtts.addAttribute(NS_GWC, "srs", "srs", "String", srs);
            srsAtts.addAttribute(NS_GWC, "minx", "minx", "String", minx);
            srsAtts.addAttribute(NS_GWC, "miny", "miny", "String", miny);
            srsAtts.addAttribute(NS_GWC, "maxx", "maxx", "String", maxx);
            srsAtts.addAttribute(NS_GWC, "maxy", "maxy", "String", maxy);
            handler.startElement(NS_GWC,"BoundingBox","BoundingBox", srsAtts);
            handler.endElement(NS_GWC,"BoundingBox","BoundingBox");

            //                      String resolutions = getResolutionString(grid.getResolutions());
            //                      writeSimpleElement(handler, "Resolution", resolutions);
        }

        if (layer instanceof WMSLayer) {
            WMSLayer wmsLayer = (WMSLayer) layer;
            Map<String, Dimension> dims = wmsLayer.getDimensions();
            if (dims != null) {
                Iterator<Dimension> dimsIter = dims.values().iterator();
                while (dimsIter.hasNext()) {
                    if (AuthWMSService.WMS_VERSION_1_1_1.equals(version)){
                        Dimension dim = dimsIter.next();
                        AttributesImpl dimAtts = new AttributesImpl();
                        dimAtts.addAttribute(NS_GWC, "name", "name", "String", dim.getName());
                        dimAtts.addAttribute(NS_GWC, "units", "units", "String", dim.getUnits());
                        dimAtts.addAttribute(NS_GWC, "unitSymbol", "unitSymbol", "String", dim.getUnitSymbol());
                        handler.startElement(NS_GWC,"Dimension","Dimension", dimAtts);
                        handler.endElement(NS_GWC, "Dimension", "Dimension");

                        AttributesImpl extAtts = new AttributesImpl();
                        extAtts.addAttribute(NS_GWC, "name", "name", "String", dim.getName());
                        if (dim.getDefaultValue() != null) {
                            extAtts.addAttribute(NS_GWC, "default", "default", "String", dim.getDefaultValue());
                        }
                        handler.startElement(NS_GWC,"Extent","Extent", extAtts);
                        String extent = dim.getExtent();
                        handler.characters(extent.toCharArray(), 0, extent.length());
                        handler.endElement(NS_GWC, "Extent", "Extent");

                    } else if (AuthWMSService.WMS_VERSION_1_3_0.equals(version)) {
                        Dimension dim = dimsIter.next();
                        AttributesImpl dimAtts = new AttributesImpl();
                        dimAtts.addAttribute(NS_GWC, "name", "name", "String", dim.getName());
                        dimAtts.addAttribute(NS_GWC, "units", "units", "String", dim.getUnits());
                        dimAtts.addAttribute(NS_GWC, "unitSymbol", "unitSymbol", "String", dim.getUnitSymbol());
                        handler.startElement(NS_GWC,"Dimension","Dimension", dimAtts);
                        String extent = dim.getExtent();
                        handler.characters(extent.toCharArray(), 0, extent.length());
                        handler.endElement(NS_GWC, "Dimension", "Dimension");
                    }
                }
            }
        }

        List<MimeType> mimeList = layer.getMimeTypes();
        for (MimeType mime : mimeList) {
            String format = mime.getFormat();
            writeSimpleElement(handler, "Format", format);
        }

        String style = layer.getStyles();
        style = style == null ? "default" : style;
        handler.startElement(NS_GWC, "Style", "Style", null);
        writeSimpleElement(handler, "Name", style);
        writeSimpleElement(handler, "Title", style);
        handler.endElement(NS_GWC, "Style", "Style");

        handler.endElement(NS_GWC, "Layer", "Layer");
    }

    /**	<VendorSpecificCapabilities>
     * 		<TileSet>...</TileSet>
     * 		...
     * 		<TileSet>...</TileSet>
     * 	</VendorSpecificCapabilities>
     */
    private void writeVendorSpecificCapabilities(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"VendorSpecificCapabilities","VendorSpecificCapabilities", null);
        writeTileSets(handler);
        handler.endElement(NS_GWC, "VendorSpecificCapabilities", "VendorSpecificCapabilities");
    }

    /**	<GetCapabilities>
     * 		<Format>...</Format>
     * 		<DCPType>...</DCPType>
     * 	</GetCapabilities>
     */
    private void writeGetCapability(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"GetCapability","GetCapability", null);
        writeSimpleElement(handler, "Format", "application/vnd.ogc.wms_xml");
        writeDCPType(handler);
        handler.endElement(NS_GWC, "GetCapability", "GetCapability");
    }

    /**	<GetMap>
     * 		<Format>...</Format>
     * 		<DCPType>...</DCPType>
     * 	</GetMap>
     */
    private void writeGetMap(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"GetMap","GetMap", null);
        writeDCPType(handler);
        handler.endElement(NS_GWC, "GetMap", "GetMap");
    }

    /**	<DCPType>
     * 		<HTTP>
     * 			<Get>
     * 				<OnlineResource xlink:type="simple" xlink:href="http://yourhost.com/wms"/>
     * 			</Get>
     * 		</HTTP>
     * 	</DCPType>
     */
    private void writeDCPType(ContentHandler handler) throws SAXException {
        handler.startElement(NS_GWC,"DCPType","DCPType", null);
        handler.startElement(NS_GWC,"Get","Get", null);
        AttributesImpl atts = new AttributesImpl();
        String url = tile.servletReq.getRequestURL().toString();
        atts.addAttribute(NS_GWC, "href", "xlink:href", "String", url);
        atts.addAttribute(NS_GWC, "type", "xlink:type", "String", "simple");
        handler.startElement(NS_GWC,"OnlineResource","OnlineResource", atts);
        handler.endElement(NS_GWC, "OnlineResource", "OnlineResource");
        handler.endElement(NS_GWC, "Get", "Get");
        handler.endElement(NS_GWC, "DCPType", "DCPType");
    }

    private void writeTileSets(ContentHandler handler) throws SAXException {
        Map<String, TileLayer> layerMap = tileLayerDispatcher.getLayers();
        Iterator<TileLayer> iter = layerMap.values().iterator();

        while (iter.hasNext()) {
            TileLayer layer = iter.next();
            if (userCanSeeLayer(SecurityContextHolder.getContext().getAuthentication(), layer)) {
                layer.isInitialized();
                writeTileSet(handler, layer);
            }
        }
    }

    /**	<TileSet>
     * 		<SRS>...</SRS>
     * 		<BoundingBox srs="..." minx="..." miny="..." maxx="..." maxy="..." />
     * 		<Resolutions>...</Resolutions>
     * 		<Width>...</Width>
     * 		<Height>...</Height>
     * 		<Format>...</Format>
     * 		<Layers>...</Layers>
     * 		<Styles>...</Styles>
     * 		<Dimension>...</Dimension>
     * 	</TileSet>
     */
    private void writeTileSet(ContentHandler handler, TileLayer layer) throws SAXException {
        List<MimeType> mimeList = layer.getMimeTypes();
        String strStyles = layer.getStyles();
        if (strStyles == null) {
            strStyles = "";
        }

        Iterator<Grid> iter = layer.getGrids().values().iterator();
        while (iter.hasNext()) {
            try {
                Grid grid = iter.next();

                String srs = grid.getSRS().toString();

                String[] strBounds = doublesToStrings(grid.getBounds().coords);
                String minx = strBounds[0];
                String miny = strBounds[1];
                String maxx = strBounds[2];
                String maxy = strBounds[3];

                String resolutions = getResolutionString(grid.getResolutions());
                String name = layer.getName();

                for (MimeType mime : mimeList) {
                    String format = mime.getFormat();
                    handler.startElement(NS_GWC,"TileSet","TileSet", null);
                    writeSimpleElement(handler, "SRS", srs);

                    AttributesImpl srsAtts = new AttributesImpl();
                    srsAtts.addAttribute(NS_GWC, "srs", "srs", "String", srs);
                    srsAtts.addAttribute(NS_GWC, "minx", "minx", "String", minx);
                    srsAtts.addAttribute(NS_GWC, "miny", "miny", "String", miny);
                    srsAtts.addAttribute(NS_GWC, "maxx", "maxx", "String", maxx);
                    srsAtts.addAttribute(NS_GWC, "maxy", "maxy", "String", maxy);
                    handler.startElement(NS_GWC,"BoundingBox","BoundingBox", srsAtts);
                    handler.endElement(NS_GWC, "BoundingBox", "BoundingBox");
                    writeSimpleElement(handler, "Resolution", resolutions);
                    writeSimpleElement(handler, "Width", "256");
                    writeSimpleElement(handler, "Height", "256");
                    writeSimpleElement(handler, "Format", format);
                    writeSimpleElement(handler, "Layers", name);
                    writeSimpleElement(handler, "Styles", "default");
                    if (layer instanceof WMSLayer) {
                        WMSLayer wmsLayer = (WMSLayer) layer;
                        Map<String, Dimension> dims = wmsLayer.getDimensions();
                        if (dims != null) {
                            Iterator<Dimension> dimsIter = dims.values().iterator();
                            while (dimsIter.hasNext()) {
                                Dimension dim = dimsIter.next();
                                AttributesImpl dimAtts = new AttributesImpl();
                                dimAtts.addAttribute(NS_GWC, "name", "name", "String", dim.getName());
                                dimAtts.addAttribute(NS_GWC, "units", "units", "String", dim.getUnits());
                                dimAtts.addAttribute(NS_GWC, "unitSymbol", "unitSymbol", "String", dim.getUnitSymbol());
                                handler.startElement(NS_GWC,"Dimension","Dimension", dimAtts);
                                String extent = dim.getExtent();
                                handler.characters(extent.toCharArray(), 0, extent.length());
                                handler.endElement(NS_GWC, "Dimension", "Dimension");
                            }
                        }
                    }
                    handler.endElement(NS_GWC, "TileSet", "TileSet");
                }
            } catch (GeoWebCacheException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * <name>value</name>
     */
    private void writeSimpleElement(ContentHandler handler, String name, String value) throws SAXException {
        handler.startElement(NS_GWC, name, name, null);
        if (value != null) {
            handler.characters(value.toCharArray(), 0, value.length());
        }
        handler.endElement(NS_GWC, name, name);
    }    

    private boolean userCanSeeLayer(Authentication user, TileLayer layer) {
        return dataAccessManager.canAccess(user, layer.getName());
    }

}
