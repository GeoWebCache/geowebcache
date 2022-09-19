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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service.kml;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.storage.StorageBroker;

public class KMLSiteMap {
    private ConveyorKMLTile tile = null;
    private TileLayerDispatcher tLD = null;
    private StorageBroker storageBroker;
    private GridSetBroker gridSetBroker;
    private SecurityDispatcher secDispatcher;

    public KMLSiteMap(
            ConveyorKMLTile tile,
            TileLayerDispatcher tLD,
            GridSetBroker gridSetBroker,
            SecurityDispatcher secDispatcher) {
        this.tile = tile;
        this.tLD = tLD;
        this.storageBroker = tile.getStorageBroker();
        this.gridSetBroker = gridSetBroker;
        this.secDispatcher = secDispatcher;
    }

    public void write() throws GeoWebCacheException, IOException {
        tile.servletResp.setCharacterEncoding("utf-8");
        tile.servletResp.setContentType("application/xml");
        tile.servletResp.setStatus(200);

        if (tile.getHint() == KMLService.HINT_SITEMAP_LAYER) {
            writeSiteMap();
        } else {
            writeSiteMapIndex();
        }
    }

    private void writeSiteMapIndex() throws IOException {
        @SuppressWarnings("PMD.CloseResource") // managed by servlet container
        OutputStream os = tile.servletResp.getOutputStream();

        String header =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n";
        os.write(header.getBytes());

        writeSiteMapIndexLoop();

        String footer = "</sitemapindex>";
        os.write(footer.getBytes());
    }

    private void writeSiteMapIndexLoop() throws IOException {
        @SuppressWarnings("PMD.CloseResource") // managed by servlet container
        OutputStream os = tile.servletResp.getOutputStream();
        String urlPrefix = tile.getUrlPrefix();

        Iterable<TileLayer> iter = tLD.getLayerListFiltered();

        for (TileLayer tl : iter) {
            if (!tl.isEnabled()) {
                continue;
            }
            Set<String> grids = tl.getGridSubsets();
            List<MimeType> mimeTypes = tl.getMimeTypes();

            if (grids != null
                    && grids.contains(gridSetBroker.getWorldEpsg4326().getName())
                    && mimeTypes != null
                    && mimeTypes.contains(XMLMime.kml)) {
                String smStr =
                        "<sitemap><loc>"
                                + urlPrefix
                                + tl.getName()
                                + "/sitemap.xml</loc></sitemap>";
                os.write(smStr.getBytes());
            }
        }
    }

    private void writeSiteMap() throws GeoWebCacheException, IOException {
        TileLayer layer = tile.getLayer();

        GridSubset gridSubset = layer.getGridSubset(gridSetBroker.getWorldEpsg4326().getName());

        writeSiteMapHeader();

        long[] gridRect = gridSubset.getCoverageBestFit();

        // Check whether we need two tiles for world bounds or not
        if (gridRect[4] > 0 && (gridRect[2] != gridRect[0] || gridRect[3] != gridRect[1])) {
            throw new GeoWebCacheException(
                    layer.getName()
                            + " is too big for the sub grid set for "
                            + gridSubset.getName()
                            + ", allow for smaller zoom levels.");
        } else if (gridRect[0] != gridRect[2]) {
            long[] gridLocWest = {0, 0, 0};
            long[] gridLocEast = {1, 0, 0};

            writeSiteMapLoop(gridLocWest);
            writeSiteMapLoop(gridLocEast);

        } else {
            long[] gridLoc = {gridRect[0], gridRect[1], gridRect[4]};
            writeSiteMapLoop(gridLoc);
        }

        writeSiteMapFooter();
    }

    private void writeSiteMapHeader() throws IOException {
        String header =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:geo=\"http://www.google.com/geo/schemas/sitemap/1.0\">\n";
        tile.servletResp.getOutputStream().write(header.getBytes());
    }

    private void writeSiteMapFooter() throws IOException {
        String footer = "</urlset>";
        tile.servletResp.getOutputStream().write(footer.getBytes());
    }

    public void writeSiteMapLoop(long[] gridLoc) throws GeoWebCacheException, IOException {
        @SuppressWarnings("PMD.CloseResource") // managed by servlet container
        OutputStream os = tile.servletResp.getOutputStream();
        TileLayer tileLayer = tile.getLayer();
        String urlPrefix = tile.getUrlPrefix();

        // Add a link to the super overlay first
        String superOverlayLoc =
                "<url><loc>"
                        + urlPrefix.substring(0, urlPrefix.length() - 1)
                        + ".kml.kml</loc><geo:geo><geo:format>kml</geo:format></geo:geo></url>\n";
        os.write(superOverlayLoc.getBytes());

        LinkedList<long[]> subTileList = new LinkedList<>();

        subTileList.addFirst(gridLoc);

        while (subTileList.peek() != null) {
            String gridSetId = gridSetBroker.getWorldEpsg4326().getName();
            long[] curLoc = subTileList.removeFirst();
            long[][] linkGridLocs = tileLayer.getGridSubset(gridSetId).getSubGrid(curLoc);
            linkGridLocs =
                    KMZHelper.filterGridLocs(
                            storageBroker,
                            secDispatcher,
                            tileLayer,
                            gridSetId,
                            XMLMime.kml,
                            linkGridLocs);

            // Save the links we still need to follow for later
            for (long[] subTile : linkGridLocs) {
                if (subTile[2] > 0) {
                    subTileList.addLast(subTile);
                }
            }

            // We need to link to the data tiles only, for now
            String tmp =
                    "<url><loc>"
                            + urlPrefix
                            + KMLService.gridLocString(curLoc)
                            + ".kml"
                            + "</loc><geo:geo><geo:format>kml</geo:format></geo:geo></url>\n";
            os.write(tmp.getBytes());

            // Could add priority as 1 / (zoomlevel + 1)
        }
    }
}
