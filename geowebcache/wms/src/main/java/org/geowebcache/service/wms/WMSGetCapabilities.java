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
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ServletUtils;

public class WMSGetCapabilities {

    private static Log log = LogFactory.getLog(WMSGetCapabilities.class);

    private TileLayerDispatcher tld;

    private String urlStr;

    private boolean includeVendorSpecific = false;

    protected WMSGetCapabilities(TileLayerDispatcher tld, HttpServletRequest servReq) {
        this.tld = tld;
        urlStr = servReq.getRequestURL().toString() + "?SERVICE=WMS&amp;";

        String[] tiledKey = { "TILED" };
        Map<String, String> tiledValue = ServletUtils.selectedStringsFromMap(
                servReq.getParameterMap(), servReq.getCharacterEncoding(), tiledKey);

        if (tiledValue != null && tiledValue.size() > 0) {
            includeVendorSpecific = Boolean.parseBoolean(tiledValue.get("TILED"));
        }
    }

    protected void writeResponse(HttpServletResponse response) {

        byte[] data = generateGetCapabilities().getBytes();

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/vnd.ogc.wms_xml");
        response.setCharacterEncoding("UTF-8");
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
        str.append("<!DOCTYPE WMT_MS_Capabilities SYSTEM \"http://schemas.opengis.net/wms/1.1.1/capabilities_1_1_1.dtd\" ");
        if (includeVendorSpecific) {
            str.append("[\n");
            str.append("<!ELEMENT VendorSpecificCapabilities (TileSet*) >\n");
            str.append("<!ELEMENT TileSet (SRS, BoundingBox?, Resolutions, Width, Height, Format, Layers*, Styles*) >\n");
            str.append("<!ELEMENT Resolutions (#PCDATA) >\n");
            str.append("<!ELEMENT Width (#PCDATA) >\n");
            str.append("<!ELEMENT Height (#PCDATA) >\n");
            str.append("<!ELEMENT Layers (#PCDATA) >\n");
            str.append("<!ELEMENT Styles (#PCDATA) >\n");
            str.append("]");
        }
        str.append(">\n");
        str.append("<WMT_MS_Capabilities version=\"1.1.1\">\n");

        // The actual meat
        service(str);
        capability(str);

        str.append("</WMT_MS_Capabilities>\n");

        return str.toString();
    }

    private void service(StringBuilder str) {
        ServiceInformation servInfo = tld.getServiceInformation();
        str.append("<Service>\n");
        str.append("  <Name>OGC:WMS</Name>\n");

        if (servInfo == null) {
            str.append("  <Title>Web Map Service - GeoWebCache</Title>\n");
        } else {
            str.append("  <Title>" + servInfo.getTitle() + "</Title>\n");
            str.append("  <Abstract>" + servInfo.getDescription() + "</Abstract>\n");

            if (servInfo.getKeywords() != null) {
                str.append("  <KeywordList>\n");
                Iterator<String> keywordIter = servInfo.getKeywords().iterator();
                while (keywordIter.hasNext()) {
                    str.append("    <Keyword>" + keywordIter.next() + "</Keyword>\n");
                }
                str.append("  </KeywordList>\n");
            }
        }

        str.append("  <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""
                + urlStr + "\"/>\n");

        serviceContact(str);

        if (servInfo != null) {
            str.append("  <Fees>" + servInfo.getFees() + "</Fees>\n");
            str.append("  <AccessConstraints>" + servInfo.getAccessConstraints()
                    + "</AccessConstraints>\n");
        }

        str.append("</Service>\n");
    }

    private void serviceContact(StringBuilder str) {
        ServiceInformation servInfo = tld.getServiceInformation();
        if (servInfo == null) {
            return;
        }

        ServiceProvider servProv = servInfo.getServiceProvider();
        if (servProv == null) {
            return;
        }

        ServiceContact servCont = servProv.getServiceContact();

        str.append("  <ContactInformation>\n");

        if (servProv.getProviderName() != null || servCont != null) {
            str.append("    <ContactPersonPrimary>\n");
            if (servCont != null) {
                str.append("      <ContactPerson>" + servCont.getIndividualName()
                        + "</ContactPerson>\n");
            }
            str.append("      <ContactOrganization>" + servProv.getProviderName()
                    + "</ContactOrganization>\n");
            str.append("    </ContactPersonPrimary>\n");

            if (servCont != null) {
                str.append("    <ContactPosition>" + servCont.getPositionName()
                        + "</ContactPosition>\n");

                str.append("    <ContactAddress>\n");
                str.append("      <AddressType>" + servCont.getAddressType() + "</AddressType>\n");
                str.append("      <Address>" + servCont.getAddressStreet() + "</Address>\n");
                str.append("      <City>" + servCont.getAddressCity() + "</City>\n");
                str.append("      <StateOrProvince>" + servCont.getAddressAdministrativeArea()
                        + "</StateOrProvince>\n");
                str.append("      <PostCode>" + servCont.getAddressPostalCode() + "</PostCode>\n");
                str.append("      <Country>" + servCont.getAddressCountry() + "</Country>\n");
                str.append("    </ContactAddress>\n");
                str.append("    <ContactVoiceTelephone>" + servCont.getPhoneNumber()
                        + "</ContactVoiceTelephone>\n");
                str.append("    <ContactFacsimileTelephone>").append(servCont.getFaxNumber())
                        .append("</ContactFacsimileTelephone>\n");
                str.append("    <ContactElectronicMailAddress>" + servCont.getAddressEmail()
                        + "</ContactElectronicMailAddress>\n");
            }
        }

        str.append("  </ContactInformation>\n");
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
        if (this.includeVendorSpecific) {
            capabilityVendorSpecific(str);
        }
        capabilityLayerOuter(str);
        str.append("</Capability>\n");

    }

    private void capabilityRequestGetCapabilities(StringBuilder str) {
        str.append("    <GetCapabilities>\n");
        str.append("      <Format>application/vnd.ogc.wms_xml</Format>\n");
        str.append("      <DCPType>\n");
        str.append("        <HTTP>\n");
        str.append("          <Get>\n");
        str.append("            <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""
                + urlStr + "\"/>\n");
        str.append("          </Get>\n");
        str.append("        </HTTP>\n");
        str.append("      </DCPType>\n");
        str.append("    </GetCapabilities>\n");
    }

    private void capabilityRequestGetMap(StringBuilder str) {
        // Find all the formats we support
        Iterable<TileLayer> layerIter = tld.getLayerList();

        HashSet<String> formats = new HashSet<String>();

        for (TileLayer layer : layerIter) {
            if (!layer.isEnabled()) {
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

        str.append("    <GetMap>\n");
        Iterator<String> formatIter = formats.iterator();
        while (formatIter.hasNext()) {
            str.append("      <Format>" + formatIter.next() + "</Format>\n");
        }
        str.append("      <DCPType>\n");
        str.append("        <HTTP>\n");
        str.append("          <Get>\n");
        str.append("            <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""
                + urlStr + "\"/>\n");
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
        str.append("        <HTTP>\n");
        str.append("        <Get>\n");
        str.append("          <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""
                + urlStr + "\"/>\n");
        str.append("        </Get>\n");
        str.append("        </HTTP>\n");
        str.append("      </DCPType>\n");
        str.append("    </GetFeatureInfo>\n");

    }

    private void capabilityRequestDescribeLayer(StringBuilder str) {
        str.append("    <DescribeLayer>\n");
        str.append("      <Format>application/vnd.ogc.wms_xml</Format>\n");
        str.append("      <DCPType>\n");
        str.append("        <HTTP>\n");
        str.append("        <Get>\n");
        str.append("          <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""
                + urlStr + "\"/>\n");
        str.append("        </Get>\n");
        str.append("        </HTTP>\n");
        str.append("      </DCPType>\n");
        str.append("    </DescribeLayer>\n");
    }

    private void capabilityRequestGetLegendGraphic(StringBuilder str) {
        str.append("    <GetLegendGraphic>\n");
        str.append("      <Format>image/png</Format>\n");
        str.append("      <Format>image/jpeg</Format>\n");
        str.append("      <Format>image/gif</Format>\n");
        str.append("      <DCPType>\n");
        str.append("        <HTTP>\n");
        str.append("        <Get>\n");
        str.append("          <OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\" xlink:type=\"simple\" xlink:href=\""
                + urlStr + "\"/>\n");
        str.append("        </Get>\n");
        str.append("        </HTTP>\n");
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
        Iterable<TileLayer> layerIter = tld.getLayerList();
        for (TileLayer layer : layerIter) {
            if (!layer.isEnabled()) {
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
                            capabilityVendorSpecificTileset(str, layer, grid, format, style);
                        } catch (GeoWebCacheException e) {
                            log.error(e.getMessage());
                        }
                    }
                }
            }
        }
        str.append("  </VendorSpecificCapabilities>\n");
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

    private void capabilityVendorSpecificTileset(StringBuilder str, TileLayer layer,
            GridSubset grid, String formatStr, String styleName) throws GeoWebCacheException {

        String srsStr = grid.getSRS().toString();
        StringBuilder resolutionsStr = new StringBuilder();
        double[] res = grid.getResolutions();
        for (int i = 0; i < res.length; i++) {
            resolutionsStr.append(Double.toString(res[i]) + " ");
        }

        String[] bs = boundsPrep(grid.getCoverageBestFitBounds());

        str.append("    <TileSet>\n");
        str.append("      <SRS>" + srsStr + "</SRS>\n");
        str.append("      <BoundingBox SRS=\"" + srsStr + "\" minx=\"" + bs[0] + "\" miny=\""
                + bs[1] + "\"  maxx=\"" + bs[2] + "\"  maxy=\"" + bs[3] + "\" />\n");
        str.append("      <Resolutions>" + resolutionsStr.toString() + "</Resolutions>\n");
        str.append("      <Width>" + grid.getTileWidth() + "</Width>\n");
        str.append("      <Height>" + grid.getTileHeight() + "</Height>\n");
        str.append("      <Format>" + formatStr + "</Format>\n");
        str.append("      <Layers>" + layer.getName() + "</Layers>\n");
        str.append("      <Styles>").append(ServletUtils.URLEncode(styleName))
                .append("</Styles>\n");
        str.append("    </TileSet>\n");
    }

    private void capabilityLayerOuter(StringBuilder str) {
        str.append("  <Layer>\n");
        str.append("    <Title>GeoWebCache WMS</Title>\n");
        str.append("    <Abstract>Note that not all GeoWebCache instances provide a full WMS service.</Abstract>\n");
        str.append("    <LatLonBoundingBox minx=\"-180.0\" miny=\"-90.0\" maxx=\"180.0\" maxy=\"90.0\"/>\n");

        Iterable<TileLayer> layerIter = tld.getLayerList();
        for (TileLayer layer : layerIter) {
            if (!layer.isEnabled()) {
                continue;
            }
            try {
                capabilityLayerInner(str, layer);
            } catch (GeoWebCacheException e) {
                log.error(e.getMessage());
            }
        }

        str.append("  </Layer>\n");
    }

    private void capabilityLayerInner(StringBuilder str, TileLayer layer)
            throws GeoWebCacheException {
        if (layer.isQueryable()) {
            str.append("    <Layer queryable=\"1\">\n");
        } else {
            str.append("    <Layer>\n");
        }

        str.append("      <Name>" + layer.getName() + "</Name>\n");

        if (layer.getMetaInformation() != null) {
            LayerMetaInformation metaInfo = layer.getMetaInformation();
            str.append("      <Title>" + metaInfo.getTitle() + "</Title>\n");
            str.append("      <Abstract>" + metaInfo.getDescription() + "</Abstract>\n");
        } else {
            str.append("      <Title>" + layer.getName() + "</Title>\n");
        }

        TreeSet<SRS> srsSet = new TreeSet<SRS>();
        StringBuilder boundingBoxStr = new StringBuilder();
        for (String gridSetId : layer.getGridSubsets()) {
            GridSubset curGridSubSet = layer.getGridSubset(gridSetId);
            SRS curSRS = curGridSubSet.getSRS();
            if (!srsSet.contains(curSRS)) {
                str.append("      <SRS>" + curSRS.toString() + "</SRS>\n");

                // Save bounding boxes for later
                String[] bs = boundsPrep(curGridSubSet.getCoverageBestFitBounds());
                boundingBoxStr.append("      <BoundingBox SRS=\""
                        + curGridSubSet.getSRS().toString() + "\" minx=\"" + bs[0] + "\" miny=\""
                        + bs[1] + "\" maxx=\"" + bs[2] + "\" maxy=\"" + bs[3] + "\"/>\n");

                srsSet.add(curSRS);
            }
        }

        GridSubset epsg4326GridSubSet = layer.getGridSubsetForSRS(SRS.getEPSG4326());
        if (null != epsg4326GridSubSet) {
            String[] bs = boundsPrep(epsg4326GridSubSet.getCoverageBestFitBounds());
            str.append("      <LatLonBoundingBox minx=\"" + bs[0] + "\" miny=\"" + bs[1]
                    + "\" maxx=\"" + bs[2] + "\" maxy=\"" + bs[3] + "\"/>\n");
        }

        // Bounding boxes gathered earlier
        str.append(boundingBoxStr);

        // WMS 1.1 Dimensions
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
                str.append(dims);
                str.append(extents);
            }
        }

        // TODO style?
        str.append("    </Layer>\n");
    }

    String[] boundsPrep(BoundingBox bbox) {
        String[] bs = { Double.toString(bbox.getMinX()), Double.toString(bbox.getMinY()),
                Double.toString(bbox.getMaxX()), Double.toString(bbox.getMaxY()) };
        return bs;
    }
}
