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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service.kml;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.BadTileException;
import org.geowebcache.mime.MimeType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/** Creates a grid of tiles and puts the grid index on each of them */
public class KMLDebugGridLayer extends AbstractTileLayer {

    public static final String LAYERNAME = "debugGrid";

    public static final int IS_KMZ = 100;

    // private static Logger log =
    // Logging.getLogger(org.geowebcache.service.kml.KMLDebugGridLayer.class);

    // This is completely isolated anyway
    private static GridSetBroker gridSetBroker =
            new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));

    private static KMLDebugGridLayer instance;

    private KMLDebugGridLayer() {
        super.subSets = new HashMap<>();
        subSets.put(
                gridSetBroker.getWorldEpsg4326().getName(),
                GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), BoundingBox.WORLD4326, 0, 3));
    }

    public static synchronized KMLDebugGridLayer getInstance() {
        if (instance == null) {
            instance = new KMLDebugGridLayer();
        }
        return instance;
    }

    public void acquireLayerLock() {}

    @Override
    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        return null;
    }

    public BoundingBox getBounds(SRS srs) {
        return new BoundingBox(-180.0, -90.0, 180.0, 90.0);
    }

    @Override
    public MimeType getDefaultMimeType() {
        return null;
    }

    @Override
    public int[] getMetaTilingFactors() {
        return null;
    }

    @Override
    public List<MimeType> getMimeTypes() {
        return null;
    }

    @Override
    public String getName() {
        return "Debug grid";
    }

    public SRS[] getProjections() {
        SRS[] srsList = {SRS.getEPSG4326()};
        return srsList;
    }

    public double[] getResolutions(int srsIdx) {
        return null;
    }

    @Override
    public ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException, IOException {
        long[] gridLoc = tile.getTileIndex();

        BoundingBox bbox = tile.getGridSubset().boundsFromIndex(gridLoc);

        String data = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://earth.google.com/kml/2.1\">\n"
                + "<Document>\n"
                // +"<!-- Name>DocumentName</Name --->"
                + "<Placemark id=\"PlaceMarkId\">\n"
                // +"<styleUrl>#square</styleUrl>\n"
                + "<name>"
                + gridLoc[0]
                + ","
                + gridLoc[1]
                + ","
                + gridLoc[2]
                + "</name>"
                + "<Style id=\"square\">\n"
                + "<PolyStyle><color>7fffffff</color><colorMode>random</colorMode>\n"
                + "</PolyStyle>\n"
                + "<IconStyle><Icon><href>http://icons.opengeo.org/dynamic/circle/aaffaa_aaffaa_2.png</href></Icon></IconStyle>\n"
                + "<LabelStyle id=\"name\"><color>ffffffff</color><colorMode>normal</colorMode><scale>1.0</scale></LabelStyle>\n"
                + "</Style>\n"
                + "<MultiGeometry>\n"
                + "<Point><coordinates>"
                + ((bbox.getMinX() + bbox.getMaxX()) / 2)
                + ","
                + ((bbox.getMinY() + bbox.getMaxY()) / 2)
                + ",0</coordinates></Point>\n"
                + "<Polygon><outerBoundaryIs><LinearRing>\n"
                + "<coordinates decimal=\".\" cs=\",\" ts=\" \">\n"
                + bbox.getMinX()
                + ","
                + bbox.getMinY()
                + " "
                + bbox.getMaxX()
                + ","
                + bbox.getMinY()
                + " "
                + bbox.getMaxX()
                + ","
                + bbox.getMaxY()
                + " "
                + bbox.getMinX()
                + ","
                + bbox.getMaxY()
                + "</coordinates>\n"
                + "</LinearRing></outerBoundaryIs></Polygon>\n"
                + "</MultiGeometry>\n"
                + "</Placemark>\n"
                + "</Document>\n"
                + "</kml>";

        tile.setBlob(new ByteArrayResource(data.getBytes()));
        tile.setStatus(200);
        return tile;
    }

    @Override
    public String getStyles() {
        return null;
    }

    public int[][] getZoomInGridLoc(SRS srs, int[] gridLoc) {
        // log.warning("done - getZoomInGridLoc(srsIdx, gridLoc)");

        int[][] retVal = new int[4][3];

        int x = gridLoc[0] * 2;
        int y = gridLoc[1] * 2;
        int z = gridLoc[2] + 1;

        // Don't link to tiles past the last zoomLevel
        if (z > 25) {
            z = -1;
        }

        // Now adjust where appropriate
        retVal[0][0] = retVal[2][0] = x;
        retVal[1][0] = retVal[3][0] = x + 1;

        retVal[0][1] = retVal[1][1] = y;
        retVal[2][1] = retVal[3][1] = y + 1;

        retVal[0][2] = retVal[1][2] = retVal[2][2] = retVal[3][2] = z;

        return retVal;
    }

    public int getZoomStart() {
        return 0;
    }

    public int getZoomStop() {
        return 25;
    }

    @Override
    public boolean initializeInternal(GridSetBroker gridSetBroker) {
        return true;
    }

    public void releaseLayerLock() {}

    @Override
    public void setExpirationHeader(HttpServletResponse response, int zoomLevel) {}

    public String supportsBbox(SRS srs, BoundingBox bounds) throws GeoWebCacheException {
        return null;
    }

    @Override
    public boolean supportsFormat(String formatStr) throws GeoWebCacheException {
        return false;
    }

    public boolean supportsSRS(SRS srs) throws GeoWebCacheException {
        return false;
    }

    public void setApplicationContext(ApplicationContext arg0) throws BeansException {}

    //
    // public Object createKey(Tile tile) {
    // Vector<Integer> lst = new Vector<Integer>();
    // if(tile.getMimeType() == XMLMime.kmz) {
    // lst.add(KMLDebugGridLayer.IS_KMZ);
    // } else {
    // lst.add(0);
    // }
    // int[] tileIndex = tile.getTileIndex();
    // lst.add(tileIndex[0]);
    // lst.add(tileIndex[1]);
    // lst.add(tileIndex[2]);
    //
    // return (Object) lst;
    // }

    public int getType() {

        return 0;
    }

    public void init() {}

    public BoundingBox getBboxForGridLoc(SRS srs, int[] gridLoc) {
        double tileWidth = 180.0 / Math.pow(2, gridLoc[2]);

        BoundingBox bbox = new BoundingBox(
                -180.0 + tileWidth * gridLoc[0],
                -90.0 + tileWidth * gridLoc[1],
                -180.0 + tileWidth * (gridLoc[0] + 1),
                -90.0 + tileWidth * (gridLoc[1] + 1));

        return bbox;
    }

    public int[][] getCoveredGridLevels(SRS srs, BoundingBox bounds) {
        return null;
    }

    public int[] getGridLocForBounds(SRS srs, BoundingBox bounds) throws BadTileException {
        return null;
    }

    public int[] getZoomedOutGridLoc(SRS srs) {
        // log.warning("done - getZoomedOutGridLoc");
        int[] zoomedOutGridLoc = new int[3];
        zoomedOutGridLoc[0] = -1;
        zoomedOutGridLoc[1] = -1;
        zoomedOutGridLoc[2] = -1;

        return zoomedOutGridLoc;
    }

    @Override
    public Integer getBackendTimeout() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Boolean isCacheBypassAllowed() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException, IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void setBackendTimeout(int seconds) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setCacheBypassAllowed(boolean allowed) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // TODO Auto-generated method stub

    }
}
