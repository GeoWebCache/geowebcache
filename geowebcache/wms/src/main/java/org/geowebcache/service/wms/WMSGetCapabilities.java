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
 * @author Kevin Smith, Boundless, Copyright 2014
 */
package org.geowebcache.service.wms;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.meta.ServiceContact;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.config.meta.ServiceProvider;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.WMSDimensionProvider;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.XMLBuilder;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class WMSGetCapabilities {

    private static Log log = LogFactory.getLog(WMSGetCapabilities.class);

    private TileLayerDispatcher tld;

    private String urlStr;

    private boolean includeVendorSpecific = false;

    protected WMSGetCapabilities(TileLayerDispatcher tld, HttpServletRequest servReq, String baseUrl,
            String contextPath, URLMangler urlMangler) {
        this.tld = tld;
        
        urlStr = urlMangler.buildURL(baseUrl, contextPath, WMSService.SERVICE_PATH) + "?SERVICE=WMS&";

        String[] tiledKey = { "TILED" };
        Map<String, String> tiledValue = ServletUtils.selectedStringsFromMap(
                servReq.getParameterMap(), servReq.getCharacterEncoding(), tiledKey);

        if (tiledValue != null && tiledValue.size() > 0) {
            includeVendorSpecific = Boolean.parseBoolean(tiledValue.get("TILED"));
        }
    }

    protected void writeResponse(HttpServletResponse response) {

        final Charset encoding = StandardCharsets.UTF_8;
        byte[] data = generateGetCapabilities(encoding).getBytes(encoding);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/vnd.ogc.wms_xml");
        response.setCharacterEncoding(encoding.name());
        response.setContentLength(data.length);
        response.setHeader("content-disposition", "inline;filename=wms-getcapabilities.xml");

        try (OutputStream os = response.getOutputStream();){
            os.write(data);
            os.flush();
        } catch (IOException ioe) {
            log.debug("Caught IOException" + ioe.getMessage());
        }
    }

    String generateGetCapabilities(Charset encoding) {
        StringBuilder str = new StringBuilder();
        XMLBuilder xml = new XMLBuilder(str);
        
        try {
            xml.header("1.0", encoding);
            xml.appendUnescaped("<!DOCTYPE WMT_MS_Capabilities SYSTEM \"http://schemas.opengis.net/wms/1.1.1/capabilities_1_1_1.dtd\" ");
            if (includeVendorSpecific) {
                xml.appendUnescaped("[\n");
                xml.appendUnescaped("<!ELEMENT VendorSpecificCapabilities (TileSet*) >\n");
                xml.appendUnescaped("<!ELEMENT TileSet (SRS, BoundingBox?, Resolutions, Width, Height, Format, Layers*, Styles*) >\n");
                xml.appendUnescaped("<!ELEMENT Resolutions (#PCDATA) >\n");
                xml.appendUnescaped("<!ELEMENT Width (#PCDATA) >\n");
                xml.appendUnescaped("<!ELEMENT Height (#PCDATA) >\n");
                xml.appendUnescaped("<!ELEMENT Layers (#PCDATA) >\n");
                xml.appendUnescaped("<!ELEMENT Styles (#PCDATA) >\n");
                xml.appendUnescaped("]");
            }
            xml.appendUnescaped(">\n");
            xml.indentElement("WMT_MS_Capabilities").attribute("version", "1.1.1");
    
            // The actual meat
            service(xml);
            capability(xml);
            xml.endElement();
        } catch (IOException e) {
            // Should not happen as StringBuilder doesn't throw IOException
            throw new IllegalStateException(e);
        }

        return str.toString();
    }

    private void service(XMLBuilder xml) throws IOException {
        ServiceInformation servInfo = tld.getServiceInformation();
        xml.indentElement("Service");
        xml.indentElement("Name").text("OGC:WMS").endElement();

        if (servInfo == null) {
            xml.indentElement("Title").text("Web Map Service - GeoWebCache").endElement();
        } else {
            xml.indentElement("Title").text(servInfo.getTitle()).endElement();
            xml.indentElement("Abstract").text(servInfo.getDescription()).endElement();

            if (servInfo.getKeywords() != null) {
                xml.indentElement("KeywordList");
                Iterator<String> keywordIter = servInfo.getKeywords().iterator();
                while (keywordIter.hasNext()) {
                    xml.indentElement("Keyword").text(keywordIter.next()).endElement();
                }
                xml.endElement();
            }
        }
        onlineResource(xml, urlStr);

        serviceContact(xml);

        if (servInfo != null) {
            xml.indentElement("Fees").text(servInfo.getFees()).endElement();
            xml.indentElement("AccessConstraints").text(servInfo.getAccessConstraints()).endElement();
        }

        xml.endElement();
    }

    private void serviceContact(XMLBuilder xml) throws IOException {
        ServiceInformation servInfo = tld.getServiceInformation();
        if (servInfo == null) {
            return;
        }

        ServiceProvider servProv = servInfo.getServiceProvider();
        if (servProv == null) {
            return;
        }

        ServiceContact servCont = servProv.getServiceContact();

        xml.indentElement("ContactInformation");

        if (servProv.getProviderName() != null || servCont != null) {
            xml.indentElement("ContactPersonPrimary");
            if (servCont != null) {
                xml.simpleElement("ContactPerson",servCont.getIndividualName(), true);
            }
            xml.simpleElement("ContactOrganization",servProv.getProviderName(), true);
            xml.endElement();

            if (servCont != null) {
                xml.simpleElement("ContactPosition", servCont.getPositionName(), true);

                xml.indentElement("ContactAddress");
                xml.simpleElement("AddressType", servCont.getAddressType(), true);
                xml.simpleElement("Address", servCont.getAddressStreet(), true);
                xml.simpleElement("City", servCont.getAddressCity(), true);
                xml.simpleElement("StateOrProvince", servCont.getAddressAdministrativeArea(), true);
                xml.simpleElement("PostCode", servCont.getAddressPostalCode(), true);
                xml.simpleElement("Country", servCont.getAddressCountry(), true);
                xml.endElement();
                
                xml.simpleElement("ContactVoiceTelephone", servCont.getPhoneNumber(), true);
                xml.simpleElement("ContactFacsimileTelephone", servCont.getFaxNumber(), true);
                xml.simpleElement("ContactElectronicMailAddress", servCont.getAddressEmail(), true);
            }
        }
        xml.endElement();
    }

    private void capability(XMLBuilder xml) throws IOException {
        xml.indentElement("Capability");
        xml.indentElement("Request");
        capabilityRequestGetCapabilities(xml);
        capabilityRequestGetMap(xml);
        capabilityRequestGetFeatureInfo(xml);
        capabilityRequestDescribeLayer(xml);
        capabilityRequestGetLegendGraphic(xml);
        xml.endElement();
        capabilityException(xml);
        if (this.includeVendorSpecific) {
            capabilityVendorSpecific(xml);
        }
        capabilityLayerOuter(xml);
        xml.endElement();

    }

    XMLBuilder onlineResource(XMLBuilder xml, String url) throws IOException {
        return xml.indentElement("OnlineResource")
        .attribute("xmlns:xlink", "http://www.w3.org/1999/xlink")
        .attribute("xlink:type", "simple")
        .attribute("xlink:href", url)
        .endElement();
    }
    XMLBuilder dcpType(XMLBuilder xml, String url) throws IOException {
        xml.indentElement("DCPType");
        xml.indentElement("HTTP");
        xml.indentElement("Get");
        
        onlineResource(xml, url);
        
        xml.endElement();
        xml.endElement();
        xml.endElement();
        return xml;
    }
    
    XMLBuilder capability(XMLBuilder xml, String name, Collection<String> formats, String url) throws IOException {
        xml.indentElement(name);
        
        for(String format:formats) {
            xml.simpleElement("Format", format, true);
        }
        
        dcpType(xml, url);
        
        xml.endElement();
        return xml;
    }
    private void capabilityRequestGetCapabilities(XMLBuilder xml) throws IOException {
        capability(xml, "GetCapabilities", Collections.singleton("application/vnd.ogc.wms_xml"), urlStr);
    }

    private void capabilityRequestGetMap(XMLBuilder xml) throws IOException {
        // Find all the formats we support
        Iterable<TileLayer> layerIter = tld.getLayerList();

        HashSet<String> formats = new HashSet<String>();

        for (TileLayer layer : layerIter) {
            if (!layer.isEnabled()  || !layer.isAdvertised()) {
                continue;
            }
            if (layer.getMimeTypes() != null) {
                Iterator<MimeType> mimeIter = layer.getMimeTypes().iterator();
                while (mimeIter.hasNext()) {
                    MimeType mime = mimeIter.next();
                    formats.add(mime.getFormat());
                }
            } else {
                formats.add("image/png");
                formats.add("image/jpeg");
            }
            
        }
        
        capability(xml, "GetMap", formats, urlStr);
        
    }

    private void capabilityRequestGetFeatureInfo(XMLBuilder xml) throws IOException {
    	
        // Find all the info formats we support
        Iterable<TileLayer> layerIter = tld.getLayerList();

        HashSet<String> formats = new HashSet<String>();

        for (TileLayer layer : layerIter) {
            if (!layer.isEnabled()  || !layer.isAdvertised()) {
                continue;
            }
            if (layer.getMimeTypes() != null) {
                Iterator<MimeType> mimeIter = layer.getInfoMimeTypes().iterator();
                while (mimeIter.hasNext()) {
                    MimeType mime = mimeIter.next();
                    formats.add(mime.getFormat());
                }
            } else {
                formats.add("text/plain");
                formats.add("text/html");
                formats.add("application/vnd.ogc.gml");
            }
            
        }
        
        
        capability(xml, "GetFeatureInfo", formats, urlStr);
    }

    private void capabilityRequestDescribeLayer(XMLBuilder xml) throws IOException {
        capability(xml, "DescribeLayer", Collections.singleton("application/vnd.ogc.wms_xml"), urlStr);
    }

    private void capabilityRequestGetLegendGraphic(XMLBuilder xml) throws IOException {
        capability(xml, "GetLegendGraphic", Arrays.asList("image/png", "image/jpeg", "image/gif"), urlStr);
    }

    private void capabilityException(XMLBuilder xml) throws IOException {
        xml.indentElement("Exception");
        xml.simpleElement("Format","application/vnd.ogc.se_xml", true);
        xml.endElement();
    }

    private void capabilityVendorSpecific(XMLBuilder xml) throws IOException {
        xml.indentElement("VendorSpecificCapabilities");
        Iterable<TileLayer> layerIter = tld.getLayerList();
        for (TileLayer layer : layerIter) {
            if (!layer.isEnabled()  || !layer.isAdvertised()) {
                continue;
            }

            for (String gridSetId : layer.getGridSubsets()) {
                GridSubset grid = layer.getGridSubset(gridSetId);

                List<String> formats = new ArrayList<String>(2);

                if (layer.getMimeTypes() != null) {
                    for (MimeType mime : layer.getMimeTypes()) {
                        formats.add(mime.getFormat());
                    }
                } else {
                    formats.add(ImageMime.png.getFormat());
                    formats.add(ImageMime.jpeg.getFormat());
                }

                List<String> styles = getStyles(layer.getParameterFilters());
                for (String format : formats) {
                    for (String style : styles) {
                        try {
                            capabilityVendorSpecificTileset(xml, layer, grid, format, style);
                        } catch (GeoWebCacheException e) {
                            log.error(e.getMessage());
                        }
                    }
                }
            }
        }
        xml.endElement();
    }

    /**
     * @return a list with an empty string for the default style, and any other style name verbatim
     */
    private List<String> getStyles(List<ParameterFilter> parameterFilters) {
        List<String> styles = new ArrayList<String>(2);
        styles.add("");// the default style

        if (parameterFilters != null) {
            for (ParameterFilter filter : parameterFilters) {
                if (!"STYLES".equalsIgnoreCase(filter.getKey())) {
                    continue;
                }
                final String defaultStyle = filter.getDefaultValue();
                for (String style : filter.getLegalValues()) {
                    if (!defaultStyle.equals(style)) {
                        styles.add(style);
                    }
                }
            }
        }

        return styles;
    }

    private void capabilityVendorSpecificTileset(XMLBuilder xml, TileLayer layer,
            GridSubset grid, String formatStr, String styleName) throws GeoWebCacheException, IOException {

        String srsStr = grid.getSRS().toString();
        StringBuilder resolutionsStr = new StringBuilder();
        double[] res = grid.getResolutions();
        for (int i = 0; i < res.length; i++) {
            resolutionsStr.append(Double.toString(res[i]) + " ");
        }

        String[] bs = boundsPrep(grid.getCoverageBestFitBounds());

        xml.indentElement("TileSet");
        
        xml.simpleElement("SRS", srsStr, true);
        
        xml.boundingBox(srsStr, bs[0], bs[1], bs[2], bs[3]);
        
        xml.simpleElement("Resolutions", resolutionsStr.toString(), true);
        xml.simpleElement("Width", Integer.toString(grid.getTileWidth()), true);
        xml.simpleElement("Height", Integer.toString(grid.getTileHeight()), true);
        xml.simpleElement("Format", formatStr, true);
        xml.simpleElement("Layers", layer.getName(), true);
        xml.simpleElement("Styles", ServletUtils.URLEncode(styleName), true);

        xml.endElement();
    }

    private void capabilityLayerOuter(XMLBuilder xml) throws IOException {
        xml.indentElement("Layer");
        xml.simpleElement("Title", "GeoWebCache WMS", true);
        xml.simpleElement("Abstract", "Note that not all GeoWebCache instances provide a full WMS service.", true);
        xml.latLonBoundingBox(-180.0, -90.0, 180.0, 90.0);

        Iterable<TileLayer> layerIter = tld.getLayerList();
        for (TileLayer layer : layerIter) {
            if (!layer.isEnabled()  || !layer.isAdvertised()) {
                continue;
            }
            try {
                capabilityLayerInner(xml, layer);
            } catch (GeoWebCacheException e) {
                log.error(e.getMessage());
            }
        }

        xml.endElement();
    }

    private void capabilityLayerInner(XMLBuilder xml, TileLayer layer)
            throws GeoWebCacheException, IOException {
        xml.indentElement("Layer");
        
        if (layer.isQueryable()) {
            xml.attribute("queryable", "1");
        }

        xml.simpleElement("Name", layer.getName(), true);

        if (layer.getMetaInformation() != null) {
            LayerMetaInformation metaInfo = layer.getMetaInformation();
            xml.simpleElement("Title", metaInfo.getTitle(), true);
            xml.simpleElement("Abstract", metaInfo.getDescription(), true);
        } else {
            xml.simpleElement("Title", layer.getName(), true);
        }

        if (layer.getMetadataURLs() != null) {
            for (MetadataURL metadataURL : layer.getMetadataURLs()) {
                xml.indentElement("MetadataURL");
                xml.attribute("type", metadataURL.getType());
                xml.simpleElement("Format", metadataURL.getFormat(), true);
                onlineResource(xml, metadataURL.getUrl().toString()); // TODO should this be URLEncoded?
                xml.endElement();
            }
        }

        {
            TreeSet<SRS> srsSet = new TreeSet<>();
            HashSet<GridSubset> gridSubsetSet = new HashSet<>();
            for (String gridSetId : layer.getGridSubsets()) {
                GridSubset curGridSubSet = layer.getGridSubset(gridSetId);
                SRS curSRS = curGridSubSet.getSRS();
                if (!srsSet.contains(curSRS)) {
                    srsSet.add(curSRS);
                    gridSubsetSet.add(curGridSubSet);
                }
            }
            for(SRS curSRS: srsSet) {
                xml.simpleElement("SRS", curSRS.toString(), true);
            }
    
            GridSubset epsg4326GridSubSet = layer.getGridSubsetForSRS(SRS.getEPSG4326());
            if (null != epsg4326GridSubSet) {
                String[] bs = boundsPrep(epsg4326GridSubSet.getCoverageBestFitBounds());
                xml.latLonBoundingBox(bs[0], bs[1], bs[2], bs[3]);
            }
            
            for(GridSubset curGridSubSet: gridSubsetSet) {
                String[] bs = boundsPrep(curGridSubSet.getCoverageBestFitBounds());
                xml.boundingBox(curGridSubSet.getSRS().toString(), bs[0], bs[1], bs[2], bs[3]);
            }
        }

        // WMS 1.1 Dimensions
        // TODO change API to not use string builder.  Pass an XML Builder, or ask for a model
        // object. KS
        if (layer.getParameterFilters() != null) {
            StringBuilder dims = new StringBuilder();
            StringBuilder extents = new StringBuilder();
            for (ParameterFilter parameterFilter : layer.getParameterFilters()) {
                if (parameterFilter instanceof WMSDimensionProvider) {
                    ((WMSDimensionProvider) parameterFilter).appendDimensionElement(dims, "      ");
                    ((WMSDimensionProvider) parameterFilter).appendExtentElement(extents, "      ");
                }
            }

            if (dims.length() > 0 && extents.length() > 0) {
                xml.appendUnescaped(dims.toString());
                xml.appendUnescaped(extents.toString());
            }
        }

        // TODO style?
        xml.endElement();
    }

    String[] boundsPrep(BoundingBox bbox) {
        String[] bs = { Double.toString(bbox.getMinX()), Double.toString(bbox.getMinY()),
                Double.toString(bbox.getMaxX()), Double.toString(bbox.getMaxY()) };
        return bs;
    }
}
