/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, OpenGeo, Copyright 2009
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.service.wmts;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.legends.LegendInfo;
import org.geowebcache.config.legends.LegendInfoBuilder;
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
import org.geowebcache.io.XMLBuilder;
import org.geowebcache.layer.TileJSONProvider;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;

public class WMTSGetCapabilities {

    private static Logger log = Logging.getLogger(WMTSGetCapabilities.class.getName());

    private TileLayerDispatcher tld;

    private GridSetBroker gsb;

    private String baseUrl;

    private String restBaseUrl;

    private final Collection<WMTSExtension> extensions;

    protected WMTSGetCapabilities(
            TileLayerDispatcher tld,
            GridSetBroker gsb,
            HttpServletRequest servReq,
            String baseUrl,
            String contextPath,
            URLMangler urlMangler) {
        this(tld, gsb, servReq, baseUrl, contextPath, urlMangler, Collections.emptyList());
    }

    protected WMTSGetCapabilities(
            TileLayerDispatcher tld,
            GridSetBroker gsb,
            HttpServletRequest servReq,
            String baseUrl,
            String contextPath,
            URLMangler urlMangler,
            Collection<WMTSExtension> extensions) {
        this.tld = tld;
        this.gsb = gsb;

        String forcedBaseUrl =
                ServletUtils.stringFromMap(
                        servReq.getParameterMap(), servReq.getCharacterEncoding(), "base_url");

        if (forcedBaseUrl != null) {
            this.baseUrl = forcedBaseUrl;
        } else {
            this.baseUrl = urlMangler.buildURL(baseUrl, contextPath, WMTSService.SERVICE_PATH);
        }

        this.restBaseUrl = urlMangler.buildURL(baseUrl, contextPath, WMTSService.REST_PATH);

        this.extensions = extensions;
    }

    protected void writeResponse(HttpServletResponse response, RuntimeStats stats) {
        final Charset encoding = StandardCharsets.UTF_8;
        byte[] data = generateGetCapabilities(encoding).getBytes(encoding);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/xml");
        response.setCharacterEncoding(encoding.name());
        response.setContentLength(data.length);
        response.setHeader("content-disposition", "inline;filename=wmts-getcapabilities.xml");

        stats.log(data.length, CacheResult.OTHER);

        try (OutputStream os = response.getOutputStream()) {
            os.write(data);
            os.flush();
        } catch (IOException ioe) {
            log.fine("Caught IOException" + ioe.getMessage());
        }
    }

    private String generateGetCapabilities(Charset encoding) {
        StringBuilder str = new StringBuilder();
        XMLBuilder xml = new XMLBuilder(str);

        try {
            xml.header("1.0", encoding);
            xml.indentElement("Capabilities");
            xml.attribute("xmlns", "http://www.opengis.net/wmts/1.0");
            xml.attribute("xmlns:ows", "http://www.opengis.net/ows/1.1");
            xml.attribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
            xml.attribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xml.attribute("xmlns:gml", "http://www.opengis.net/gml");
            // allow extensions to register their names spaces
            for (WMTSExtension extension : extensions) {
                extension.registerNamespaces(xml);
            }
            StringBuilder schemasLocations = new StringBuilder("http://www.opengis.net/wmts/1.0 ");
            schemasLocations.append(
                    "http://schemas.opengis.net/wmts/1.0/wmtsGetCapabilities_response.xsd ");
            // allow extensions to register their schemas locations
            for (WMTSExtension extension : extensions) {
                for (String schemaLocation : extension.getSchemaLocations()) {
                    schemasLocations.append(schemaLocation).append(" ");
                }
            }
            schemasLocations.delete(schemasLocations.length() - 1, schemasLocations.length());
            // add schemas locations
            xml.attribute("xsi:schemaLocation", schemasLocations.toString());
            xml.attribute("version", "1.0.0");
            // There were some contradictions in the draft schema, haven't checked whether they've
            // fixed those
            // str.append("xsi:schemaLocation=\"http://www.opengis.net/wmts/1.0
            // http://geowebcache.org/schema/opengis/wmts/1.0.0/wmtsGetCapabilities_response.xsd\"\n");

            ServiceInformation serviceInformation = getServiceInformation();

            serviceIdentification(xml, serviceInformation);
            serviceProvider(xml, serviceInformation);
            operationsMetadata(xml);

            contents(xml);

            xml.indentElement("ServiceMetadataURL")
                    .attribute("xlink:href", WMTSUtils.getKvpServiceMetadataURL(baseUrl))
                    .endElement();

            xml.indentElement("ServiceMetadataURL")
                    .attribute("xlink:href", restBaseUrl + "/WMTSCapabilities.xml")
                    .endElement();

            xml.endElement("Capabilities");

            return str.toString();
        } catch (IOException e) {
            // Should not happen as StringBuilder doesn't throw
            throw new IllegalStateException(e);
        }
    }

    /** Composes service information using information provided by extensions. */
    private ServiceInformation getServiceInformation() {
        ServiceInformation servInfo = tld.getServiceInformation();
        for (WMTSExtension extension : extensions) {
            ServiceInformation serviceInformation = extension.getServiceInformation();
            if (serviceInformation != null) {
                if (servInfo == null) {
                    servInfo = new ServiceInformation();
                }
                mergeServiceInformation(servInfo, serviceInformation);
            }
        }
        return servInfo;
    }

    /** Substitute serviceA information with no NULL information from serviceB. */
    private void mergeServiceInformation(ServiceInformation serviceA, ServiceInformation serviceB) {
        if (serviceB.getTitle() != null) {
            serviceA.setTitle(serviceB.getTitle());
        }
        if (serviceB.getDescription() != null) {
            serviceA.setDescription(serviceB.getDescription());
        }
        if (serviceB.getKeywords() != null) {
            serviceA.getKeywords().addAll(serviceB.getKeywords());
        }
        if (serviceB.getServiceProvider() != null) {
            serviceA.setServiceProvider(serviceB.getServiceProvider());
        }
        if (serviceB.getFees() != null) {
            serviceA.setFees(serviceB.getFees());
        }
        if (serviceB.getAccessConstraints() != null) {
            serviceA.setAccessConstraints(serviceB.getAccessConstraints());
        }
        if (serviceB.getProviderName() != null) {
            serviceA.setProviderName(serviceB.getProviderName());
        }
        if (serviceB.getProviderSite() != null) {
            serviceA.setProviderSite(serviceB.getProviderSite());
        }
        if (serviceB.getServiceProvider() != null) {
            if (serviceA.getServiceProvider() != null) {
                mergeProviderInformation(
                        serviceA.getServiceProvider(), serviceB.getServiceProvider());
            } else {
                serviceA.setServiceProvider(serviceB.getServiceProvider());
            }
        }
    }

    /** Substitute providerA information with no NULL information from providerB. */
    private void mergeProviderInformation(ServiceProvider providerA, ServiceProvider providerB) {
        if (providerB.getProviderName() != null) {
            providerA.setProviderName(providerB.getProviderName());
        }
        if (providerB.getProviderSite() != null) {
            providerA.setProviderSite(providerB.getProviderSite());
        }
        if (providerB.getServiceContact() != null) {
            if (providerA.getServiceContact() != null) {
                mergeContactInformation(
                        providerA.getServiceContact(), providerB.getServiceContact());
            } else {
                providerA.setServiceContact(providerB.getServiceContact());
            }
        }
    }

    /** Substitute contactA information with no NULL information from contactB. */
    private void mergeContactInformation(ServiceContact contactA, ServiceContact contactB) {
        if (contactB.getIndividualName() != null) {
            contactA.setIndividualName(contactB.getIndividualName());
        }
        if (contactB.getPositionName() != null) {
            contactA.setPositionName(contactB.getPositionName());
        }
        if (contactB.getAddressType() != null) {
            contactA.setAddressType(contactB.getAddressType());
        }
        if (contactB.getAddressStreet() != null) {
            contactA.setAddressStreet(contactB.getAddressStreet());
        }
        if (contactB.getAddressCity() != null) {
            contactA.setAddressCity(contactB.getAddressCity());
        }
        if (contactB.getAddressAdministrativeArea() != null) {
            contactA.setAddressAdministrativeArea(contactB.getAddressAdministrativeArea());
        }
        if (contactB.getAddressPostalCode() != null) {
            contactA.setAddressPostalCode(contactB.getAddressPostalCode());
        }
        if (contactB.getAddressCountry() != null) {
            contactA.setAddressCountry(contactB.getAddressCountry());
        }
        if (contactB.getPhoneNumber() != null) {
            contactA.setPhoneNumber(contactB.getPhoneNumber());
        }
        if (contactB.getFaxNumber() != null) {
            contactA.setFaxNumber(contactB.getFaxNumber());
        }
        if (contactB.getAddressEmail() != null) {
            contactA.setAddressEmail(contactB.getAddressEmail());
        }
    }

    private void serviceIdentification(XMLBuilder xml, ServiceInformation servInfo)
            throws IOException {

        xml.indentElement("ows:ServiceIdentification");

        if (servInfo != null) {
            appendTag(xml, "ows:Title", servInfo.getTitle(), "Web Map Tile Service - GeoWebCache");
            appendTag(xml, "ows:Abstract", servInfo.getDescription(), null);

            if (servInfo != null && servInfo.getKeywords() != null) {
                xml.indentElement("ows:Keywords");
                Iterator<String> keywordIter = servInfo.getKeywords().iterator();
                while (keywordIter.hasNext()) {
                    appendTag(xml, "ows:Keyword", keywordIter.next(), null);
                }
                xml.endElement();
            }
        } else {
            xml.simpleElement("ows:Title", "Web Map Tile Service - GeoWebCache", true);
        }
        xml.simpleElement("ows:ServiceType", "OGC WMTS", true);
        xml.simpleElement("ows:ServiceTypeVersion", "1.0.0", true);

        if (servInfo != null) {
            appendTag(xml, "ows:Fees", servInfo.getFees(), null);
            appendTag(xml, "ows:AccessConstraints", servInfo.getAccessConstraints(), null);
        }

        xml.endElement("ows:ServiceIdentification");
    }

    private void serviceProvider(XMLBuilder xml, ServiceInformation servInfo) throws IOException {
        ServiceProvider servProv = null;
        if (servInfo != null) {
            servProv = servInfo.getServiceProvider();
        }
        xml.indentElement("ows:ServiceProvider");

        if (servProv != null) {
            appendTag(xml, "ows:ProviderName", servProv.getProviderName(), null);

            if (servProv.getProviderSite() != null) {
                xml.indentElement("ows:ProviderSite")
                        .attribute("xlink:href", servProv.getProviderSite())
                        .endElement();
            }

            ServiceContact servCont = servProv.getServiceContact();
            if (servCont != null) {
                xml.indentElement("ows:ServiceContact");
                appendTag(xml, "ows:IndividualName", servCont.getIndividualName(), null);
                appendTag(xml, "ows:PositionName", servCont.getPositionName(), null);
                xml.indentElement("ows:ContactInfo");

                if (servCont.getPhoneNumber() != null || servCont.getFaxNumber() != null) {
                    xml.indentElement("ows:Phone");
                    appendTag(xml, "ows:Voice", servCont.getPhoneNumber(), null);
                    appendTag(xml, "ows:Facsimile", servCont.getFaxNumber(), null);
                    xml.endElement();
                }

                xml.indentElement("ows:Address");
                appendTag(xml, "ows:DeliveryPoint", servCont.getAddressStreet(), null);
                appendTag(xml, "ows:City", servCont.getAddressCity(), null);
                appendTag(
                        xml,
                        "ows:AdministrativeArea",
                        servCont.getAddressAdministrativeArea(),
                        null);
                appendTag(xml, "ows:PostalCode", servCont.getAddressPostalCode(), null);
                appendTag(xml, "ows:Country", servCont.getAddressCountry(), null);
                appendTag(xml, "ows:ElectronicMailAddress", servCont.getAddressEmail(), null);
                xml.endElement("ows:Address");

                xml.endElement();
                xml.endElement();
            }
        } else {
            appendTag(xml, "ows:ProviderName", baseUrl, null);
            xml.indentElement("ows:ProviderSite").attribute("xlink:href", baseUrl).endElement();
            xml.indentElement("ows:ServiceContact");
            appendTag(xml, "ows:IndividualName", "GeoWebCache User", null);
            xml.endElement();
        }

        xml.endElement("ows:ServiceProvider");
    }

    private void operationsMetadata(XMLBuilder xml) throws IOException {
        xml.indentElement("ows:OperationsMetadata");
        operation(xml, "GetCapabilities", baseUrl);
        operation(xml, "GetTile", baseUrl);
        operation(xml, "GetFeatureInfo", baseUrl);
        // allow extension to inject their own metadata
        for (WMTSExtension extension : extensions) {
            List<WMTSExtension.OperationMetadata> operationsMetaData =
                    extension.getExtraOperationsMetadata();
            if (operationsMetaData != null) {
                for (WMTSExtension.OperationMetadata operationMetadata : operationsMetaData) {
                    operation(
                            xml,
                            operationMetadata.getName(),
                            operationMetadata.getBaseUrl() == null
                                    ? baseUrl
                                    : operationMetadata.getBaseUrl());
                }
            }
            extension.encodedOperationsMetadata(xml);
        }
        xml.endElement("ows:OperationsMetadata");
    }

    private void operation(XMLBuilder xml, String operationName, String baseUrl)
            throws IOException {
        xml.indentElement("ows:Operation").attribute("name", operationName);
        xml.indentElement("ows:DCP");
        xml.indentElement("ows:HTTP");
        if (baseUrl.contains("?")) {
            xml.indentElement("ows:Get").attribute("xlink:href", baseUrl + "&");
        } else {
            xml.indentElement("ows:Get").attribute("xlink:href", baseUrl + "?");
        }
        xml.indentElement("ows:Constraint").attribute("name", "GetEncoding");
        xml.indentElement("ows:AllowedValues");
        xml.simpleElement("ows:Value", "KVP", true);
        xml.endElement();
        xml.endElement();
        xml.endElement();
        xml.endElement();
        xml.endElement();
        xml.endElement("ows:Operation");
    }

    private void contents(XMLBuilder xml) throws IOException {
        xml.indentElement("Contents");
        Iterable<TileLayer> iter = tld.getLayerListFiltered();
        Set<GridSet> usedGridsets = new HashSet<>();
        for (TileLayer layer : iter) {
            if (!layer.isEnabled() || !layer.isAdvertised()) {
                continue;
            }
            layer(xml, layer, baseUrl, usedGridsets);
        }

        // only dump the gridsets actually used, as the OGC TMS spec introduced many default ones
        List<GridSet> capabilitiesGridsets = new ArrayList<>(gsb.getGridSets());
        capabilitiesGridsets.retainAll(usedGridsets);
        // sorting makes it easier to find a gridset now that levels do not repeat the gridset name
        capabilitiesGridsets.sort(Comparator.comparing(GridSet::getName));
        for (GridSet gset : capabilitiesGridsets) {
            tileMatrixSet(xml, gset);
        }

        xml.endElement("Contents");
    }

    private void layer(XMLBuilder xml, TileLayer layer, String baseurl, Set<GridSet> usedGridsets)
            throws IOException {
        xml.indentElement("Layer");
        LayerMetaInformation layerMeta = layer.getMetaInformation();

        if (layerMeta == null) {
            appendTag(xml, "ows:Title", layer.getName(), null);
        } else {
            appendTag(xml, "ows:Title", layerMeta.getTitle(), null);
            appendTag(xml, "ows:Abstract", layerMeta.getDescription(), null);
        }

        layerWGS84BoundingBox(xml, layer);

        appendTag(xml, "ows:Identifier", layer.getName(), null);

        if (layer.getMetadataURLs() != null) {
            for (MetadataURL metadataURL : layer.getMetadataURLs()) {
                xml.indentElement("MetadataURL");
                xml.attribute("type", metadataURL.getType());
                xml.simpleElement("Format", metadataURL.getFormat(), true);
                xml.indentElement("OnlineResource")
                        .attribute("xmlns:xlink", "http://www.w3.org/1999/xlink")
                        .attribute("xlink:type", "simple")
                        .attribute("xlink:href", metadataURL.getUrl().toString())
                        .endElement();
                xml.endElement();
            }
        }

        // We need the filters for styles and dimensions
        List<ParameterFilter> filters = layer.getParameterFilters();

        layerStyles(xml, layer, filters);

        layerFormats(xml, layer);

        layerInfoFormats(xml, layer);

        if (filters != null) {
            layerDimensions(xml, layer, filters);
        }

        layerGridSubSets(xml, layer, usedGridsets);

        layerResourceUrls(xml, layer, filters, restBaseUrl);

        // allow extensions to contribute extra metadata to this layer
        for (WMTSExtension extension : extensions) {
            extension.encodeLayer(xml, layer);
        }

        xml.endElement("Layer");
    }

    private void layerWGS84BoundingBox(XMLBuilder xml, TileLayer layer) throws IOException {
        GridSubset subset = layer.getGridSubsetForSRS(SRS.getEPSG4326());
        if (subset != null) {
            double[] coords = subset.getOriginalExtent().getCoords();
            xml.indentElement("ows:WGS84BoundingBox");
            xml.simpleElement("ows:LowerCorner", coords[0] + " " + coords[1], true);
            xml.simpleElement("ows:UpperCorner", coords[2] + " " + coords[3], true);
            xml.endElement("ows:WGS84BoundingBox");
            return;
        }
        subset = layer.getGridSubsetForSRS(SRS.getEPSG900913());
        if (subset != null) {
            double[] coords = subset.getOriginalExtent().getCoords();
            double originShift = 2 * Math.PI * 6378137 / 2.0;
            double mx = coords[0];
            double my = coords[1];
            double lon = (mx / originShift) * 180.0;
            double lat = (my / originShift) * 180.0;

            lat = 180 / Math.PI * (2 * Math.atan(Math.exp(lat * Math.PI / 180.0)) - Math.PI / 2.0);
            xml.indentElement("ows:WGS84BoundingBox");
            xml.simpleElement("ows:LowerCorner", lon + " " + lat, true);

            mx = coords[2];
            my = coords[3];
            lon = (mx / originShift) * 180.0;
            lat = (my / originShift) * 180.0;

            lat = 180 / Math.PI * (2 * Math.atan(Math.exp(lat * Math.PI / 180.0)) - Math.PI / 2.0);

            xml.simpleElement("ows:UpperCorner", lon + " " + lat, true);
            xml.endElement("ows:WGS84BoundingBox");
        }
    }

    /**
     * Helper method that get layer legends info by merging deprecated legends info objects with the
     * new ones.
     */
    private Map<String, LegendInfo> getLegendsInfo(TileLayer layer) {
        Map<String, LegendInfo> legendsInfo = new HashMap<>();
        for (Map.Entry<String, LegendInfo> entry : layer.getLayerLegendsInfo().entrySet()) {
            // convert deprecated model to new model
            String styleName = entry.getKey();
            LegendInfo legend = entry.getValue();
            legendsInfo.put(
                    styleName,
                    new LegendInfoBuilder()
                            .withWidth(legend.getWidth())
                            .withHeight(legend.getHeight())
                            .withFormat(legend.getFormat())
                            .withCompleteUrl(legend.getLegendUrl())
                            .withStyleName(styleName)
                            .build());
        }
        // add the new legend info model objects
        legendsInfo.putAll(layer.getLayerLegendsInfo());
        return legendsInfo;
    }

    private void layerStyles(XMLBuilder xml, TileLayer layer, List<ParameterFilter> filters)
            throws IOException {
        String defStyle = layer.getStyles();
        Map<String, LegendInfo> legendsInfo = getLegendsInfo(layer);
        if (filters == null) {
            xml.indentElement("Style");
            xml.attribute("isDefault", "true");
            if (defStyle == null) {
                xml.simpleElement("ows:Identifier", "", true);
            } else {
                xml.simpleElement("ows:Identifier", TileLayer.encodeDimensionValue(defStyle), true);
            }
            encodeStyleLegendGraphic(xml, legendsInfo.get(defStyle));
            xml.endElement("Style");
        } else {
            ParameterFilter stylesFilter = null;
            Iterator<ParameterFilter> iter = filters.iterator();
            while (stylesFilter == null && iter.hasNext()) {
                ParameterFilter filter = iter.next();
                if (filter.getKey().equalsIgnoreCase("STYLES")) {
                    stylesFilter = filter;
                }
            }

            List<String> legalStyles = null;
            if (stylesFilter != null) legalStyles = stylesFilter.getLegalValues();

            if (legalStyles != null && !legalStyles.isEmpty()) {
                // There's a style filter listing at least one value
                String defVal = stylesFilter.getDefaultValue();
                if (defVal == null) {
                    if (defStyle != null) {
                        defVal = defStyle;
                    } else {
                        defVal = "";
                    }
                }

                for (String value : legalStyles) {
                    xml.indentElement("Style");
                    if (value.equals(defVal)) {
                        xml.attribute("isDefault", "true");
                    }
                    xml.simpleElement(
                            "ows:Identifier", TileLayer.encodeDimensionValue(value), true);
                    encodeStyleLegendGraphic(xml, legendsInfo.get(value));
                    xml.endElement();
                }
            } else {
                // Couldn't get a list of styles so just say there's a default.
                xml.indentElement("Style");
                xml.attribute("isDefault", "true");
                xml.simpleElement("ows:Identifier", "", true);
                if (defStyle != null) {
                    encodeStyleLegendGraphic(xml, legendsInfo.get(defStyle));
                }
                xml.endElement();
            }
        }
    }

    /**
     * XML encodes the provided legend information. If the provided information legend is NULL
     * nothing is done.
     */
    private void encodeStyleLegendGraphic(XMLBuilder xml, LegendInfo legendInfo)
            throws IOException {
        if (legendInfo == null) {
            // nothing to do
            return;
        }
        xml.indentElement("LegendURL");
        // validate mandatory attributes
        checkNotNull(legendInfo.getFormat(), "Legend format is mandatory in WMTS.");
        checkNotNull(legendInfo.getLegendUrl(), "Legend URL is mandatory in WMTS.");
        // add mandatory attributes
        xml.attribute("format", legendInfo.getFormat());
        xml.attribute("xlink:href", legendInfo.getLegendUrl());
        // add optional attributes
        if (legendInfo.getWidth() != null) {
            xml.attribute("width", String.valueOf(legendInfo.getWidth()));
        }
        if (legendInfo.getHeight() != null) {
            xml.attribute("height", String.valueOf(legendInfo.getHeight()));
        }
        if (legendInfo.getMinScale() != null) {
            xml.attribute("minScaleDenominator", String.valueOf(legendInfo.getMinScale()));
        }
        if (legendInfo.getMaxScale() != null) {
            xml.attribute("maxScaleDenominator", String.valueOf(legendInfo.getMaxScale()));
        }
        xml.endElement("LegendURL");
    }

    private void layerFormats(XMLBuilder xml, TileLayer layer) throws IOException {
        List<String> mimeFormats = WMTSUtils.getLayerFormats(layer);
        for (String format : mimeFormats) {
            xml.simpleElement("Format", format, true);
        }
    }

    private void layerInfoFormats(XMLBuilder xml, TileLayer layer) throws IOException {
        if (layer.isQueryable()) {
            List<String> infoFormats = WMTSUtils.getInfoFormats(layer);
            for (String format : infoFormats) {
                xml.simpleElement("InfoFormat", format, true);
            }
        }
    }

    private void layerDimensions(XMLBuilder xml, TileLayer layer, List<ParameterFilter> filters)
            throws IOException {
        List<ParameterFilter> layerDimensions = WMTSUtils.getLayerDimensions(filters);
        for (ParameterFilter dimension : layerDimensions) {
            dimensionDescription(xml, dimension, dimension.getLegalValues());
        }
    }

    private void dimensionDescription(XMLBuilder xml, ParameterFilter filter, List<String> values)
            throws IOException {
        xml.indentElement("Dimension");
        xml.simpleElement("ows:Identifier", filter.getKey(), false);
        String defaultStr = TileLayer.encodeDimensionValue(filter.getDefaultValue());
        xml.simpleElement("Default", defaultStr, false);

        Iterator<String> iter = values.iterator();
        while (iter.hasNext()) {
            String value = TileLayer.encodeDimensionValue(iter.next());
            xml.simpleElement("Value", value, false);
        }
        xml.endElement("Dimension");
    }

    private void layerGridSubSets(XMLBuilder xml, TileLayer layer, Set<GridSet> usedGridSets)
            throws IOException {

        for (String gridSetId : layer.getGridSubsets()) {
            GridSubset gridSubset = layer.getGridSubset(gridSetId);

            xml.indentElement("TileMatrixSetLink");
            xml.simpleElement("TileMatrixSet", gridSubset.getName(), true);
            usedGridSets.add(gridSubset.getGridSet());

            if (!gridSubset.fullGridSetCoverage()) {
                String[] levelNames = gridSubset.getGridNames();
                long[][] wmtsLimits = gridSubset.getWMTSCoverages();

                xml.indentElement("TileMatrixSetLimits");
                for (int i = 0; i < levelNames.length; i++) {
                    xml.indentElement("TileMatrixLimits");
                    xml.simpleElement("TileMatrix", levelNames[i], true);
                    xml.simpleElement("MinTileRow", Long.toString(wmtsLimits[i][1]), true);
                    xml.simpleElement("MaxTileRow", Long.toString(wmtsLimits[i][3]), true);
                    xml.simpleElement("MinTileCol", Long.toString(wmtsLimits[i][0]), true);
                    xml.simpleElement("MaxTileCol", Long.toString(wmtsLimits[i][2]), true);
                    xml.endElement();
                }
                xml.endElement();
            }
            xml.endElement("TileMatrixSetLink");
        }
    }
    /**
     * For each layer discovers the available image formats, feature info formats and dimensions and
     * produce the necessary <ResourceURL> elements.
     */
    private void layerResourceUrls(
            XMLBuilder xml, TileLayer layer, List<ParameterFilter> filters, String baseurl)
            throws IOException {
        String baseTemplate = baseurl + "/" + layer.getName();
        String commonTemplate =
                baseTemplate + "/{style}/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}";
        String commonDimensions = "";
        // Extracts layer dimension
        List<ParameterFilter> layerDimensions = WMTSUtils.getLayerDimensions(filters);
        if (!layerDimensions.isEmpty()) {
            commonDimensions =
                    "&"
                            + layerDimensions.stream()
                                    .map(d -> d.getKey() + "={" + d.getKey() + "}")
                                    .collect(Collectors.joining("&"));
        }
        // Extracts image formats
        List<String> mimeFormats = WMTSUtils.getLayerFormats(layer);
        for (String format : mimeFormats) {
            String template = commonTemplate + "?format=" + format + commonDimensions;
            layerResourceUrlsGen(xml, format, "tile", template);
        }
        // Extracts feature info formats
        List<String> infoFormats = WMTSUtils.getInfoFormats(layer);
        for (String format : infoFormats) {
            String template = commonTemplate + "/{J}/{I}?format=" + format + commonDimensions;
            layerResourceUrlsGen(xml, format, "FeatureInfo", template);
        }
        if (layer instanceof TileJSONProvider) {
            List<String> formatExtensions = WMTSUtils.getLayerFormatsExtensions(layer);
            TileJSONProvider provider = (TileJSONProvider) layer;
            String outputFormat = ApplicationMime.json.getFormat();
            if (provider.supportsTileJSON()) {
                for (String tileJsonFormat : formatExtensions) {
                    String template =
                            baseTemplate
                                    + "/{style}/tilejson/"
                                    + tileJsonFormat
                                    + "?format="
                                    + outputFormat;
                    layerResourceUrlsGen(xml, outputFormat, "TileJSON", template);
                }
            }
        }
    }

    /** Generate the <ResourceURL> element into XML. */
    private void layerResourceUrlsGen(XMLBuilder xml, String format, String type, String template)
            throws IOException {
        xml.indentElement("ResourceURL");
        xml.attribute("format", format);
        xml.attribute("resourceType", type);
        xml.attribute("template", template);
        xml.endElement("ResourceURL");
    }

    private void tileMatrixSet(XMLBuilder xml, GridSet gridSet) throws IOException {
        xml.indentElement("TileMatrixSet");
        xml.simpleElement("ows:Identifier", gridSet.getName(), true);
        // If the following is not good enough, please get in touch and we will try to fix it :)
        xml.simpleElement(
                "ows:SupportedCRS", "urn:ogc:def:crs:EPSG::" + gridSet.getSrs().getNumber(), true);
        // TODO detect these str.append("
        // <WellKnownScaleSet>urn:ogc:def:wkss:GlobalCRS84Pixel</WellKnownScaleSet>\n");
        for (int i = 0; i < gridSet.getNumLevels(); i++) {
            double[] tlCoordinates = gridSet.getOrderedTopLeftCorner(i);
            tileMatrix(
                    xml,
                    gridSet.getGrid(i),
                    tlCoordinates,
                    gridSet.getTileWidth(),
                    gridSet.getTileHeight(),
                    gridSet.isScaleWarning());
        }
        xml.endElement("TileMatrixSet");
    }

    private void tileMatrix(
            XMLBuilder xml,
            Grid grid,
            double[] tlCoordinates,
            int tileWidth,
            int tileHeight,
            boolean scaleWarning)
            throws IOException {
        xml.indentElement("TileMatrix");
        if (scaleWarning) {
            xml.simpleElement(
                    "ows:Abstract",
                    "The grid was not well-defined, the scale therefore assumes 1m per map unit.",
                    true);
        }
        xml.simpleElement("ows:Identifier", grid.getName(), true);
        xml.simpleElement("ScaleDenominator", Double.toString(grid.getScaleDenominator()), true);
        xml.indentElement("TopLeftCorner")
                .text(Double.toString(tlCoordinates[0]))
                .text(" ")
                .text(Double.toString(tlCoordinates[1]))
                .endElement();
        xml.simpleElement("TileWidth", Integer.toString(tileWidth), true);
        xml.simpleElement("TileHeight", Integer.toString(tileHeight), true);
        xml.simpleElement("MatrixWidth", Long.toString(grid.getNumTilesWide()), true);
        xml.simpleElement("MatrixHeight", Long.toString(grid.getNumTilesHigh()), true);
        xml.endElement("TileMatrix");
    }

    private void appendTag(XMLBuilder xml, String tagName, String value, String defaultValue)
            throws IOException {
        if (value == null) {
            if (defaultValue == null) return;
            else value = defaultValue;
        }
        xml.simpleElement(tagName, value, true);
    }
}
