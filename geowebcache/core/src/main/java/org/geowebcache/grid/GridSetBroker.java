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
package org.geowebcache.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayerDispatcher;

public class GridSetBroker {
    private static Log log = LogFactory.getLog(GridSetBroker.class);

    public final GridSet WORLD_EPSG4326;

    public final GridSet WORLD_EPSG3857;

    private Map<String, GridSet> gridSets;

    private Set<String> embeddedGridSets;

    public GridSetBroker(boolean useEPSG900913, boolean useGWC11xNames) {
        gridSets = new HashMap<String, GridSet>();

        String unprojectedName = "GlobalCRS84Geometric";
        String mercatorName = "GoogleMapsCompatible";

        if (useGWC11xNames) {
            unprojectedName = "EPSG:4326";
            if (useEPSG900913) {
                mercatorName = "EPSG:900913";
            } else {
                mercatorName = "EPSG:3857";
            }

        }

        log.debug("Adding " + unprojectedName);
        WORLD_EPSG4326 = GridSetFactory.createGridSet(unprojectedName, SRS.getEPSG4326(),
                BoundingBox.WORLD4326, false, GridSetFactory.DEFAULT_LEVELS, null,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 256, 256, true);
        gridSets.put(WORLD_EPSG4326.getName(), WORLD_EPSG4326);

        if (useEPSG900913) {
            log.debug("Adding EPSG:900913 grid set for Spherical Mercator / GoogleMapsCompatible");

            WORLD_EPSG3857 = GridSetFactory.createGridSet(mercatorName, SRS.getEPSG900913(),
                    BoundingBox.WORLD3857, false, GridSetFactory.DEFAULT_LEVELS, null,
                    GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 256, 256, false);
        } else {
            log.debug("Adding EPSG:3857 grid set for Spherical Mercator / GoogleMapsCompatible");

            WORLD_EPSG3857 = GridSetFactory.createGridSet(mercatorName, SRS.getEPSG3857(),
                    BoundingBox.WORLD3857, false, GridSetFactory.DEFAULT_LEVELS, null,
                    GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 256, 256, false);
        }
        gridSets.put(WORLD_EPSG3857.getName(), WORLD_EPSG3857);

        log.debug("Adding GlobalCRS84Pixel");
        GridSet GlobalCRS84Pixel = GridSetFactory.createGridSet("GlobalCRS84Pixel",
                SRS.getEPSG4326(), BoundingBox.WORLD4326, true, scalesCRS84PixelResolutions(),
                null, null, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, null, 256, 256, true);
        GlobalCRS84Pixel
                .setDescription("This well-known scale set has been defined for global cartographic products. "
                        + "Rounded pixel sizes have been chosen for intuitive cartographic representation of raster data. "
                        + "Some values have been chosen to coincide with original pixel size of commonly used global"
                        + "products like STRM (1\" and 3\"), GTOPO (30\") or ETOPO (2' and 5'). Scale denominator"
                        + "and approximated pixel size in meters are only accurate near the equator.");

        gridSets.put(GlobalCRS84Pixel.getName(), GlobalCRS84Pixel);

        log.debug("Adding GlobalCRS84Scale");
        GridSet GlobalCRS84Scale = GridSetFactory.createGridSet("GlobalCRS84Scale",
                SRS.getEPSG4326(), BoundingBox.WORLD4326, true, scalesCRS84ScaleResolutions(),
                null, null, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, null, 256, 256, true);
        GlobalCRS84Scale
                .setDescription("This well-known scale set has been defined for global cartographic products. "
                        + "Rounded scales have been chosen for intuitive cartographic representation of vector data. "
                        + "Scale denominator is only accurate near the equator.");

        gridSets.put(GlobalCRS84Scale.getName(), GlobalCRS84Scale);

        log.debug("Adding GoogleCRS84Quad");
        GridSet GoogleCRS84Quad = GridSetFactory.createGridSet("GoogleCRS84Quad",
                SRS.getEPSG4326(), BoundingBox.WORLD4326, true, null,
                scalesCRS84QuadScaleDenominators(), null, GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                null, 256, 256, true);
        GoogleCRS84Quad
                .setDescription("This well-known scale set has been defined to allow quadtree "
                        + "pyramids in CRS84. Level 0 allows representing the whole world "
                        + "in a single 256x256 pixels (where the first 64 and last 64 lines "
                        + "of the tile are left blank). The next level represents the whole world in 2x2"
                        + " tiles of 256x256 pixels and so on in powers of 2. Scale denominator is only accurate near the equator.");
        
        gridSets.put(GoogleCRS84Quad.getName(), GoogleCRS84Quad);
        embeddedGridSets = Collections.unmodifiableSet(new HashSet<String>(gridSets.keySet()));
    }

    public GridSet get(String gridSetId) {
        return gridSets.get(gridSetId);
    }

    /**
     * @return the names of the gridsets that are internally defined
     */
    public Set<String> getEmbeddedNames() {
        return embeddedGridSets;
    }

    public Set<String> getNames() {
        return new TreeSet<String>(gridSets.keySet());
    }

    public List<GridSet> getGridSets() {
        return new ArrayList<GridSet>(gridSets.values());
    }

    public void put(GridSet gridSet) {
        if (gridSets.containsKey(gridSet.getName())) {
            log.warn("Duplicate grid set " + gridSet.getName() + ", "
                    + "removing previous instance, but it may still be referenced by layers.");

            gridSets.remove(gridSet.getName());
        }

        log.debug("Adding " + gridSet.getName());
        gridSets.put(gridSet.getName(), gridSet);
    }

    /**
     * Blindly removes a gridset from this gridset broker.
     * <p>
     * This method doesn't check whether there's any layer referencing the gridset nor removes it
     * from the {@link XMLConfiguration}. For such a thing, check
     * {@link TileLayerDispatcher#removeGridset(String)}, which cascades to thid method.
     * </p>
     * 
     * @param gridSetName
     * @return
     */
    public GridSet remove(final String gridSetName) {
        GridSet removed = gridSets.remove(gridSetName);
        return removed;
    }

    private double[] scalesCRS84PixelResolutions() {
        double[] scalesCRS84Pixel = new double[18];
        scalesCRS84Pixel[0] = 2;
        scalesCRS84Pixel[1] = 1;
        scalesCRS84Pixel[2] = 0.5; // 30
        scalesCRS84Pixel[3] = scalesCRS84Pixel[2] * (2.0 / 3.0); // 20
        scalesCRS84Pixel[4] = scalesCRS84Pixel[2] / 3.0; // 10
        scalesCRS84Pixel[5] = scalesCRS84Pixel[4] / 2.0; // 5
        scalesCRS84Pixel[6] = scalesCRS84Pixel[4] / 5.0; // 2
        scalesCRS84Pixel[7] = scalesCRS84Pixel[4] / 10.0; // 1
        scalesCRS84Pixel[8] = (5.0 / 6.0) * 1E-2; // 30'' = 8.33E-3
        scalesCRS84Pixel[9] = scalesCRS84Pixel[8] / 2.0; // 15''
        scalesCRS84Pixel[10] = scalesCRS84Pixel[9] / 3.0; // 5''
        scalesCRS84Pixel[11] = scalesCRS84Pixel[9] / 5.0; // 3''
        scalesCRS84Pixel[12] = scalesCRS84Pixel[11] / 3.0; // 1''
        scalesCRS84Pixel[13] = scalesCRS84Pixel[12] / 2.0; // 0.5''
        scalesCRS84Pixel[14] = scalesCRS84Pixel[13] * (3.0 / 5.0); // 0.3''
        scalesCRS84Pixel[15] = scalesCRS84Pixel[14] / 3.0; // 0.1''
        scalesCRS84Pixel[16] = scalesCRS84Pixel[15] * (3.0 / 10.0); // 0.03''
        scalesCRS84Pixel[17] = scalesCRS84Pixel[16] / 3.0; // 0.01''

        return scalesCRS84Pixel;
    }

    private double[] scalesCRS84ScaleResolutions() {
        double[] scalesCRS84Pixel = { 1.25764139776733, 0.628820698883665, 0.251528279553466,
                0.125764139776733, 6.28820698883665E-2, 2.51528279553466E-2, 1.25764139776733E-2,
                6.28820698883665E-3, 2.51528279553466E-3, 1.25764139776733E-3, 6.28820698883665E-4,
                2.51528279553466E-4, 1.25764139776733E-4, 6.28820698883665E-5, 2.51528279553466E-5,
                1.25764139776733E-5, 6.28820698883665E-6, 2.51528279553466E-6, 1.25764139776733E-6,
                6.28820698883665E-7, 2.51528279553466E-7 };

        return scalesCRS84Pixel;
    }

    private double[] scalesCRS84QuadScaleDenominators() {
        double[] scalesCRS84QuadScaleResolutions = { 559082264.0287178, 279541132.0143589,
                139770566.0071794, 69885283.00358972, 34942641.50179486, 17471320.75089743,
                8735660.375448715, 4367830.187724357, 2183915.093862179, 1091957.546931089,
                545978.7734655447, 272989.3867327723, 136494.6933663862, 68247.34668319309,
                34123.67334159654, 17061.83667079827, 8530.918335399136, 4265.459167699568,
                2132.729583849784

        };
        return scalesCRS84QuadScaleResolutions;
    }
}