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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import java.util.Arrays;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensionPriority;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;

/**
 * {@link GridSetConfiguration} implementation bean for managing default {@link GridSet}s
 *
 * <p>Includes preconfigured EPSG:4326 and EPSG:3857 (or EPSG:900913) gridsets
 */
public class DefaultGridsets extends SimpleGridSetConfiguration {
    private static Log log = LogFactory.getLog(DefaultGridsets.class);

    private final GridSet WORLD_EPSG4326;

    private final GridSet WORLD_EPSG4326x2;

    private final GridSet WORLD_EPSG3857;

    private final GridSet WORLD_EPSG3857x2;

    public GridSet worldEpsg4326() {
        return new GridSet(WORLD_EPSG4326);
    }

    public GridSet worldEpsg4326x2() {
        return new GridSet(WORLD_EPSG4326x2);
    }

    public GridSet worldEpsg3857() {
        return new GridSet(WORLD_EPSG3857);
    }

    public GridSet worldEpsg3857x2() {
        return new GridSet(WORLD_EPSG3857x2);
    }

    /**
     * Construct the default gridsets bean
     *
     * @param useEPSG900913 Whether or not to use "EPSG:900913" as the name of the default web
     *     mercator instead if "EPSG:3857". Only used if useGWC11xNames is true
     * @param useGWC11xNames Whether to use the legacy GeoWebCache 1.1.x naming scheme (EPSG codes)
     *     for the default gridset names. Otherwise
     */
    public DefaultGridsets(boolean useEPSG900913, boolean useGWC11xNames) {

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

        WORLD_EPSG4326 =
                GridSetFactory.createGridSet(
                        unprojectedName,
                        SRS.getEPSG4326(),
                        BoundingBox.WORLD4326,
                        false,
                        GridSetFactory.DEFAULT_LEVELS,
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        256,
                        256,
                        true);
        WORLD_EPSG4326.setDescription(
                "A default WGS84 tile matrix set where the first zoom level "
                        + "covers the world with two tiles on the horizontal axis and one tile "
                        + "over the vertical axis and each subsequent zoom level is calculated by half "
                        + "the resolution of its previous one. Tiles are 256px wide.");
        addInternal(WORLD_EPSG4326);

        WORLD_EPSG4326x2 =
                GridSetFactory.createGridSet(
                        unprojectedName + "x2",
                        SRS.getEPSG4326(),
                        BoundingBox.WORLD4326,
                        false,
                        GridSetFactory.DEFAULT_LEVELS,
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        512,
                        512,
                        true);
        WORLD_EPSG4326x2.setDescription(
                "A default WGS84 tile matrix set where the first zoom level "
                        + "covers the world with two tiles on the horizontal axis and one tile "
                        + "over the vertical axis and each subsequent zoom level is calculated by half "
                        + "the resolution of its previous one. Tiles are 512px wide.");
        addInternal(WORLD_EPSG4326x2);

        final SRS googleMapsCompatibleSRS = useEPSG900913 ? SRS.getEPSG900913() : SRS.getEPSG3857();
        log.debug(
                "Adding "
                        + googleMapsCompatibleSRS
                        + " grid set for Spherical Mercator / GoogleMapsCompatible");

        WORLD_EPSG3857 =
                GridSetFactory.createGridSet(
                        mercatorName,
                        googleMapsCompatibleSRS,
                        BoundingBox.WORLD3857,
                        false,
                        commonPractice900913Resolutions(),
                        null,
                        1.0D,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        null,
                        256,
                        256,
                        false);
        WORLD_EPSG3857.setDescription(
                "This well-known scale set has been defined to be compatible with Google Maps and"
                        + " Microsoft Live Map projections and zoom levels. Level 0 allows representing the whole "
                        + "world in a single 256x256 pixels. The next level represents the whole world in 2x2 tiles "
                        + "of 256x256 pixels and so on in powers of 2. Scale denominator is only accurate near the equator.");

        addInternal(WORLD_EPSG3857);

        WORLD_EPSG3857x2 =
                GridSetFactory.createGridSet(
                        mercatorName + "x2",
                        googleMapsCompatibleSRS,
                        BoundingBox.WORLD3857,
                        false,
                        halveResolutions(commonPractice900913Resolutions()),
                        null,
                        1.0D,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        null,
                        512,
                        512,
                        false);
        WORLD_EPSG3857x2.setDescription(
                "This well-known scale set has been defined to be compatible with Google Maps and"
                        + " Microsoft Live Map projections and zoom levels. Level 0 allows representing the whole "
                        + "world in a single 512x512 pixels. The next level represents the whole world in 2x2 tiles "
                        + "of 512x512 pixels and so on in powers of 2. Scale denominator is only accurate near the equator.");
        addInternal(WORLD_EPSG3857x2);

        log.debug("Adding GlobalCRS84Pixel");
        GridSet GlobalCRS84Pixel =
                GridSetFactory.createGridSet(
                        "GlobalCRS84Pixel",
                        SRS.getEPSG4326(),
                        BoundingBox.WORLD4326,
                        true,
                        scalesCRS84PixelResolutions(),
                        null,
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        null,
                        256,
                        256,
                        true);
        GlobalCRS84Pixel.setDescription(
                "This well-known scale set has been defined for global cartographic products. "
                        + "Rounded pixel sizes have been chosen for intuitive cartographic representation of raster data. "
                        + "Some values have been chosen to coincide with original pixel size of commonly used global"
                        + "products like STRM (1\" and 3\"), GTOPO (30\") or ETOPO (2' and 5'). Scale denominator"
                        + "and approximated pixel size in meters are only accurate near the equator.");

        addInternal(GlobalCRS84Pixel);

        log.debug("Adding GlobalCRS84Scale");
        GridSet GlobalCRS84Scale =
                GridSetFactory.createGridSet(
                        "GlobalCRS84Scale",
                        SRS.getEPSG4326(),
                        BoundingBox.WORLD4326,
                        true,
                        null,
                        scalesCRS84ScaleDenominators(),
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        null,
                        256,
                        256,
                        true);
        GlobalCRS84Scale.setDescription(
                "This well-known scale set has been defined for global cartographic products. "
                        + "Rounded scales have been chosen for intuitive cartographic representation of vector data. "
                        + "Scale denominator is only accurate near the equator.");

        addInternal(GlobalCRS84Scale);

        log.debug("Adding GoogleCRS84Quad");
        GridSet GoogleCRS84Quad =
                GridSetFactory.createGridSet(
                        "GoogleCRS84Quad",
                        SRS.getEPSG4326(),
                        BoundingBox.WORLD4326,
                        true,
                        null,
                        scalesCRS84QuadScaleDenominators(),
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        null,
                        256,
                        256,
                        true);
        GoogleCRS84Quad.setDescription(
                "This well-known scale set has been defined to allow quadtree "
                        + "pyramids in CRS84. Level 0 allows representing the whole world "
                        + "in a single 256x256 pixels (where the first 64 and last 64 lines "
                        + "of the tile are left blank). The next level represents the whole world in 2x2"
                        + " tiles of 256x256 pixels and so on in powers of 2. Scale denominator is only accurate near the equator.");

        addInternal(GoogleCRS84Quad);
    }

    private double[] halveResolutions(double[] resolutions) {
        return Arrays.stream(resolutions).map(r -> r / 2).toArray();
    }

    @Override
    public void afterPropertiesSet() throws GeoWebCacheException {}

    @Override
    public String getIdentifier() {
        return "DefaultGridsets";
    }

    @Override
    public String getLocation() {
        return "Default";
    }

    private double[] scalesCRS84QuadScaleDenominators() {
        double[] scalesCRS84QuadScaleResolutions = {
            559082264.0287178,
            279541132.0143589,
            139770566.0071794,
            69885283.00358972,
            34942641.50179486,
            17471320.75089743,
            8735660.375448715,
            4367830.187724357,
            2183915.093862179,
            1091957.546931089,
            545978.7734655447,
            272989.3867327723,
            136494.6933663862,
            68247.34668319309,
            34123.67334159654,
            17061.83667079827,
            8530.918335399136,
            4265.459167699568,
            2132.729583849784
        };
        return scalesCRS84QuadScaleResolutions;
    }

    private double[] commonPractice900913Resolutions() {
        return new double[] { //
            156543.03390625,
            78271.516953125,
            39135.7584765625,
            19567.87923828125,
            9783.939619140625,
            4891.9698095703125,
            2445.9849047851562,
            1222.9924523925781,
            611.4962261962891,
            305.74811309814453,
            152.87405654907226,
            76.43702827453613,
            38.218514137268066,
            19.109257068634033,
            9.554628534317017,
            4.777314267158508,
            2.388657133579254,
            1.194328566789627,
            0.5971642833948135,
            0.29858214169740677,
            0.14929107084870338,
            0.07464553542435169,
            0.037322767712175846,
            0.018661383856087923,
            0.009330691928043961,
            0.004665345964021981,
            0.0023326729820109904,
            0.0011663364910054952,
            5.831682455027476E-4,
            2.915841227513738E-4,
            1.457920613756869E-4
        };
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

    private double[] scalesCRS84ScaleDenominators() {
        // double[] scalesCRS84Pixel = { 1.25764139776733, 0.628820698883665, 0.251528279553466,
        // 0.125764139776733, 6.28820698883665E-2, 2.51528279553466E-2, 1.25764139776733E-2,
        // 6.28820698883665E-3, 2.51528279553466E-3, 1.25764139776733E-3, 6.28820698883665E-4,
        // 2.51528279553466E-4, 1.25764139776733E-4, 6.28820698883665E-5, 2.51528279553466E-5,
        // 1.25764139776733E-5, 6.28820698883665E-6, 2.51528279553466E-6, 1.25764139776733E-6,
        // 6.28820698883665E-7, 2.51528279553466E-7 };
        //
        // return scalesCRS84Pixel;
        double[] scalesCRS84Pixel = {
            500E6, 250E6, 100E6, 50E6, 25E6, 10E6, 5E6, 2.5E6, 1E6, 500E3, 250E3, 100E3, 50E3, 25E3,
            10E3, 5E3, 2.5E3, 1000, 500, 250, 100
        };

        return scalesCRS84Pixel;
    }

    @Override
    public int getPriority(Class<? extends BaseConfiguration> clazz) {
        return GeoWebCacheExtensionPriority.LOWEST;
    }

    @Override
    public void deinitialize() throws Exception {}

    @Override
    public int getPriority() {
        return GeoWebCacheExtensionPriority.LOWEST;
    }
}
