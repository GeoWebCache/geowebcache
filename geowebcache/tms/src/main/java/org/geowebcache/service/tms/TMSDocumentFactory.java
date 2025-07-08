/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp / OpenGeo, Copyright 2009
 */
package org.geowebcache.service.tms;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.XMLBuilder;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.URLMangler;

/**
 * Basic implementation of the TMS documents. Not all of GWCs more advanced features can easily be accomodated by this
 * service.
 *
 * <p>The commented out sections are optional parts of the OSGeo standard
 */
public class TMSDocumentFactory {

    protected TileLayerDispatcher tld;

    protected GridSetBroker gsb;

    private String defaultBaseUrl;

    private String defaultContextPath;

    protected URLMangler urlMangler;

    Charset encoding;

    public static final String TILEMAPSERVICE_LEADINGPATH = "tms/1.0.0";

    public static final String SERVICE_PATH = "service/" + TILEMAPSERVICE_LEADINGPATH;

    protected TMSDocumentFactory(
            TileLayerDispatcher tld, GridSetBroker gsb, URLMangler urlMangler, String serviceName, Charset encoding) {
        this.tld = tld;
        this.gsb = gsb;
        this.urlMangler = urlMangler;
        this.encoding = encoding;
    }

    protected TMSDocumentFactory(
            TileLayerDispatcher tld, GridSetBroker gsb, String baseUrl, String contextPath, URLMangler urlMangler) {
        this(tld, gsb, baseUrl, contextPath, urlMangler, StandardCharsets.UTF_8);
    }

    protected TMSDocumentFactory(
            TileLayerDispatcher tld,
            GridSetBroker gsb,
            String baseUrl,
            String contextPath,
            URLMangler urlMangler,
            Charset encoding) {
        this.tld = tld;
        this.gsb = gsb;
        this.defaultBaseUrl = baseUrl;
        this.defaultContextPath = contextPath;
        this.urlMangler = urlMangler;
        this.encoding = encoding;
    }

    protected TMSDocumentFactory(TileLayerDispatcher tld, GridSetBroker gsb, URLMangler urlMangler) {
        this(tld, gsb, null, null, urlMangler);
    }

    protected String getTileMapServiceDoc() {
        return getTileMapServiceDoc(defaultBaseUrl, defaultContextPath);
    }

    protected String getTileMapServiceDoc(String baseUrl, String contextPath) {
        StringBuilder str = new StringBuilder();
        XMLBuilder xml = new XMLBuilder(str);
        try {
            xml.header("1.0", encoding);
            xml.indentElement("TileMapService")
                    .attribute("version", "1.0.0")
                    .attribute("services", urlMangler.buildURL(baseUrl, contextPath, ""));
            // TODO can have these set through Spring
            xml.simpleElement("Title", "Tile Map Service", true);
            xml.simpleElement("Abstract", "A Tile Map Service served by GeoWebCache", true);
            // TODO Optional stuff, note that there is some meta data stuff on the
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
            //
            // <ContactElectronicMailAddress>pramsey@refractions.net</ContactElectronicMailAddress>
            // </ContactInformation>
            xml.indentElement("TileMaps");
            Iterable<TileLayer> iter = tld.getLayerListFiltered();
            for (TileLayer layer : iter) {
                if (!layer.isEnabled() || !layer.isAdvertised()) {
                    continue;
                }
                tileMapsForLayer(xml, layer, baseUrl, contextPath);
            }
            xml.endElement();
            xml.endElement();

            return str.toString();
        } catch (IOException ex) {
            // Should not happen
            throw new IllegalStateException(ex);
        }
    }

    protected void tileMapsForLayer(XMLBuilder xml, TileLayer layer, String baseUrl, String contextPath)
            throws IOException {
        for (String gridSetId : layer.getGridSubsets()) {
            GridSubset gridSub = layer.getGridSubset(gridSetId);
            for (MimeType mimeType : layer.getMimeTypes()) {
                // GridSubset gridSub = iter.next();
                xml.indentElement("TileMap")
                        .attribute("title", tileMapTitle(layer))
                        .attribute("srs", gridSub.getSRS().toString())
                        .attribute("profile", profileForGridSet(gridSub.getGridSet()))
                        .attribute("href", tileMapUrl(layer, gridSub, mimeType, baseUrl, contextPath))
                        .endElement();
            }
        }
    }

    protected String getTileMapDoc(TileLayer layer, GridSubset gridSub, GridSetBroker gsb, MimeType mimeType) {
        return getTileMapDoc(layer, gridSub, gsb, mimeType, defaultBaseUrl, defaultContextPath);
    }

    protected String getTileMapDoc(
            TileLayer layer, GridSubset gridSub, MimeType mimeType, String baseUrl, String contextPath) {
        return getTileMapDoc(layer, gridSub, gsb, mimeType, baseUrl, contextPath);
    }

    protected String getTileMapDoc(
            TileLayer layer,
            GridSubset gridSub,
            GridSetBroker gsb,
            MimeType mimeType,
            String baseUrl,
            String contextPath) {
        StringBuilder str = new StringBuilder();
        XMLBuilder xml = new XMLBuilder(str);
        try {
            xml.header("1.0", encoding);
            xml.indentElement("TileMap")
                    .attribute("version", "1.0.0")
                    .attribute("tilemapservice", urlMangler.buildURL(baseUrl, contextPath, SERVICE_PATH));
            xml.simpleElement("Title", tileMapTitle(layer), true);
            xml.simpleElement("Abstract", tileMapDescription(layer), true);

            // <KeywordList></KeywordList>
            // <Metadata type="TC211" mime-type="text/xml" href="http://www.org" />
            // <Attribution>
            //   <Title>National Geospatial Intelligence Agency</Title>
            //   <Logo width="10" height="10" href="http://nga.mil/logo.gif" mime-type="image/gif"
            // />
            // </Attribution>
            // <WebMapContext href="http://wms.org" />
            // <Face>0</Face>

            // Check with tschaub whether we actually have to provide this as OSGEO:40041
            // No.
            xml.simpleElement("SRS", gridSub.getSRS().toString(), true);
            double[] coords = gridSub.getCoverageBestFitBounds().getCoords();
            xml.boundingBox(null, coords[0], coords[1], coords[2], coords[3]);
            xml.indentElement("Origin")
                    .attribute("x", Double.toString(coords[0]))
                    .attribute("y", Double.toString(coords[1]))
                    .endElement();
            // Can we have multiple formats? NO
            xml.indentElement("TileFormat")
                    .attribute("width", Integer.toString(gridSub.getTileWidth()))
                    .attribute("height", Integer.toString(gridSub.getTileHeight()))
                    .attribute("mime-type", mimeType.getMimeType())
                    .attribute("extension", mimeType.getFileExtension())
                    .endElement();
            xml.indentElement("TileSets").attribute("profile", profileForGridSet(gridSub.getGridSet()));
            double[] resolutions = gridSub.getResolutions();
            int resIdx = 0;

            for (int zoom = gridSub.getZoomStart(); zoom <= gridSub.getZoomStop(); zoom++) {
                xml.indentElement("TileSet");
                xml.attribute("href", tileMapUrl(layer, gridSub, mimeType, zoom, baseUrl, contextPath));
                xml.attribute("units-per-pixel", Double.toString(resolutions[resIdx]));
                xml.attribute("order", Integer.toString(resIdx));
                xml.endElement();
                resIdx++;
            }

            xml.endElement();
            xml.endElement();

            return str.toString();
        } catch (IOException ex) {
            // Should not happen
            throw new IllegalStateException(ex);
        }
    }

    protected String profileForGridSet(GridSet gridSet) {
        if (gsb.getWorldEpsg4326().equals(gridSet)) {
            return "global-geodetic";
        } else if (gsb.getWorldEpsg3857().equals(gridSet)) {
            return "global-mercator";
        } else {
            return "local";
        }
    }

    protected String tileMapUrl(
            TileLayer tl, GridSubset gridSub, MimeType mimeType, String baseUrl, String contextPath) {
        return urlMangler.buildURL(baseUrl, contextPath, SERVICE_PATH + "/" + tileMapName(tl, gridSub, mimeType));
    }

    protected String tileMapUrl(
            TileLayer tl, GridSubset gridSub, MimeType mimeType, int z, String baseUrl, String contextPath) {
        return tileMapUrl(tl, gridSub, mimeType, baseUrl, contextPath) + "/" + z;
    }

    protected String tileMapName(TileLayer tl, GridSubset gridSub, MimeType mimeType) {
        try {
            String name = URLEncoder.encode(tl.getName(), "UTF-8");
            String gridSubset = URLEncoder.encode(gridSub.getName(), "UTF-8");
            return name + "@" + gridSubset + "@" + mimeType.getFileExtension();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected String tileMapTitle(TileLayer tl) {
        LayerMetaInformation metaInfo = tl.getMetaInformation();
        if (metaInfo != null && metaInfo.getTitle() != null) {
            return metaInfo.getTitle();
        }

        return tl.getName();
    }

    protected String tileMapDescription(TileLayer tl) {
        LayerMetaInformation metaInfo = tl.getMetaInformation();
        if (metaInfo != null && metaInfo.getDescription() != null) {
            return metaInfo.getDescription();
        }
        return "";
    }
}
