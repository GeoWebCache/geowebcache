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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import java.util.Arrays;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
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
    private static Logger log = Logging.getLogger(DefaultGridsets.class.getName());

    private final GridSet WORLD_EPSG4326;

    private final GridSet WORLD_EPSG4326x2;

    private final GridSet WORLD_EPSG3857;

    private final GridSet WORLD_EPSG3857x2;

    private final GridSet WEB_MERCATOR_QUAD;

    private final GridSet WORLD_CRS84_QUAD;

    private final GridSet WORLD_MERCATOR_WGS84_QUAD;

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

    public GridSet webMercatorQuad() {
        return new GridSet(WEB_MERCATOR_QUAD);
    }

    public GridSet worldCRS84Quad() {
        return new GridSet(WORLD_CRS84_QUAD);
    }

    public GridSet worldMercatorWGS84Quad() {
        return new GridSet(WORLD_MERCATOR_WGS84_QUAD);
    }

    /**
     * Construct the default gridsets bean
     *
     * @param useEPSG900913 Whether or not to use "EPSG:900913" as the name of the default web mercator instead if
     *     "EPSG:3857". Only used if useGWC11xNames is true
     * @param useGWC11xNames Whether to use the legacy GeoWebCache 1.1.x naming scheme (EPSG codes) for the default
     *     gridset names. Otherwise
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

        WORLD_EPSG4326 = GridSetFactory.createGridSet(
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
        WORLD_EPSG4326.setDescription("A default WGS84 tile matrix set where the first zoom level "
                + "covers the world with two tiles on the horizontal axis and one tile "
                + "over the vertical axis and each subsequent zoom level is calculated by half "
                + "the resolution of its previous one. Tiles are 256px wide.");
        addInternal(WORLD_EPSG4326);

        WORLD_EPSG4326x2 = GridSetFactory.createGridSet(
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
        WORLD_EPSG4326x2.setDescription("A default WGS84 tile matrix set where the first zoom level "
                + "covers the world with two tiles on the horizontal axis and one tile "
                + "over the vertical axis and each subsequent zoom level is calculated by half "
                + "the resolution of its previous one. Tiles are 512px wide.");
        addInternal(WORLD_EPSG4326x2);

        final SRS googleMapsCompatibleSRS = useEPSG900913 ? SRS.getEPSG900913() : SRS.getEPSG3857();
        log.fine("Adding " + googleMapsCompatibleSRS + " grid set for Spherical Mercator / GoogleMapsCompatible");

        WORLD_EPSG3857 = GridSetFactory.createGridSet(
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
        WORLD_EPSG3857.setDescription("This well-known scale set has been defined to be compatible with Google Maps and"
                + " Microsoft Live Map projections and zoom levels. Level 0 allows representing the whole "
                + "world in a single 256x256 pixels. The next level represents the whole world in 2x2 tiles "
                + "of 256x256 pixels and so on in powers of 2. Scale denominator is only accurate near the equator.");

        addInternal(WORLD_EPSG3857);

        WORLD_EPSG3857x2 = GridSetFactory.createGridSet(
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

        log.fine("Adding GlobalCRS84Pixel");
        GridSet GlobalCRS84Pixel = GridSetFactory.createGridSet(
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
        GlobalCRS84Pixel.setDescription("This well-known scale set has been defined for global cartographic products. "
                + "Rounded pixel sizes have been chosen for intuitive cartographic representation of raster data. "
                + "Some values have been chosen to coincide with original pixel size of commonly used global"
                + "products like STRM (1\" and 3\"), GTOPO (30\") or ETOPO (2' and 5'). Scale denominator"
                + "and approximated pixel size in meters are only accurate near the equator.");
        addInternal(GlobalCRS84Pixel);

        log.fine("Adding GlobalCRS84Scale");
        GridSet GlobalCRS84Scale = GridSetFactory.createGridSet(
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
        GlobalCRS84Scale.setDescription("This well-known scale set has been defined for global cartographic products. "
                + "Rounded scales have been chosen for intuitive cartographic representation of vector data. "
                + "Scale denominator is only accurate near the equator.");

        addInternal(GlobalCRS84Scale);

        log.fine("Adding GoogleCRS84Quad");
        GridSet GoogleCRS84Quad = GridSetFactory.createGridSet(
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

        log.fine("Adding OGC TMS WebMercatorQuad");
        WEB_MERCATOR_QUAD = GridSetFactory.createGridSet(
                "WebMercatorQuad",
                SRS.getEPSG3857(),
                BoundingBox.WORLD3857,
                true,
                null,
                new double[] {
                    559082264.028717,
                    279541132.014358,
                    139770566.007179,
                    69885283.0035897,
                    34942641.5017948,
                    17471320.7508974,
                    8735660.37544871,
                    4367830.18772435,
                    2183915.09386217,
                    1091957.54693108,
                    545978.773465544,
                    272989.386732772,
                    136494.693366386,
                    68247.346683193,
                    34123.6733415964,
                    17061.8366707982,
                    8530.91833539913,
                    4265.45916769956,
                    2132.72958384978,
                    1066.36479192489,
                    533.182395962445,
                    266.591197981222,
                    133.295598990611,
                    66.6477994953056,
                    33.3238997476528
                },
                1d,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                new String[] {
                    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                    "18", "19", "20", "21", "22", "23", "24"
                },
                256,
                256,
                true);
        // copied from the OGC TMS spec
        WEB_MERCATOR_QUAD.setDescription(
                "This tile matrix set is the most used tile matrix set in the mass market: for example, by Google Maps, Microsoft Bing Maps and Open Street Map tiles. Nevertheless, it has been long criticized because it is a based on a spherical Mercator instead of an ellipsoid. The use of WebMercatorQuad should be limited to visualization. Any additional use (including distance measurements, routing etc.) needs to use the Mercator spherical expressions to transform the coordinate to an appropriate CRS first. The risks caused by imprecision in the use of Web Mercator is also emphasized by the US National Geospatial Agency (NGA). NGA has issued an Advisory Notice on web Mercator (http://earth-info.nga.mil/GandG/wgs84/web_mercator/index.html) that says that “it may cause geo-location / geo-coordinate errors up to 40,000 meters. This erroneous geospatial positioning information poses an unacceptable risk to global safety of navigation activities, and department of defense, intelligence community, and allied partner systems, missions, and operations that require accurate and precise positioning and navigation information.” The use of WorldMercatorWGS84Quad is recommended.");
        addInternal(WEB_MERCATOR_QUAD);
        addx2Gridset(WEB_MERCATOR_QUAD);

        log.fine("Adding OGC TMS WorldCRS84Quad");
        WORLD_CRS84_QUAD = GridSetFactory.createGridSet(
                "WorldCRS84Quad",
                SRS.getEPSG4326(),
                BoundingBox.WORLD4326,
                true,
                null,
                new double[] {
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
                    2132.729583849784,
                },
                null,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                new String[] {
                    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                    "18"
                },
                256,
                256,
                true);
        // copied from the OGC TMS spec
        WORLD_CRS84_QUAD.setDescription(
                "This Tile Matrix Set defines tiles in the Equirectangular Plate Carrée projection in the CRS84 CRS for the whole world.");
        addInternal(WORLD_CRS84_QUAD);
        addx2Gridset(WORLD_CRS84_QUAD);

        log.fine("Adding OGC TMS WorldMercatorWGS84Quad");
        WORLD_MERCATOR_WGS84_QUAD = GridSetFactory.createGridSet(
                "WorldMercatorWGS84Quad",
                SRS.getSRS(3395),
                new BoundingBox(-20037508.3427892, -20037508.3427892, 20037508.3427892, 20037508.3427892),
                true,
                null,
                new double[] {
                    559082264.028717,
                    279541132.014358,
                    139770566.007179,
                    69885283.0035897,
                    34942641.5017948,
                    17471320.7508974,
                    8735660.37544871,
                    4367830.18772435,
                    2183915.09386217,
                    1091957.54693108,
                    545978.773465544,
                    272989.386732772,
                    136494.693366386,
                    68247.346683193,
                    34123.6733415964,
                    17061.8366707982,
                    8530.91833539913,
                    4265.45916769956,
                    2132.72958384978,
                    1066.36479192489,
                    533.182395962445,
                    266.591197981222,
                    133.295598990611,
                    66.6477994953056,
                    33.3238997476528,
                },
                1d,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                new String[] {
                    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                    "18", "19", "20", "21", "22", "23", "24"
                },
                256,
                256,
                true);
        // not from the spec, it does not have one
        WORLD_MERCATOR_WGS84_QUAD.setDescription(
                "This Tile Matrix Set defines tiles in the Mercator projection in the WGS84 CRS for the whole world.");
        addInternal(WORLD_MERCATOR_WGS84_QUAD);
        addx2Gridset(WORLD_MERCATOR_WGS84_QUAD);

        // family of UTM WGS84 quads from OGC TMS spec. Covers 60 zones, refers to the north CRSs
        double[] UTM_SCALES = {
            279072704.500914,
            139536352.250457,
            69768176.1252285,
            34884088.0626143,
            17442044.0313071,
            8721022.01565356,
            4360511.00782678,
            2180255.50391339,
            1090127.7519567,
            545063.875978348,
            272531.937989174,
            136265.968994587,
            68132.9844972935,
            34066.4922486467,
            17033.2461243234,
            8516.62306216168,
            4258.31153108084,
            2129.15576554042,
            1064.57788277021,
            532.288941385105,
            266.144470692553,
            133.072235346276,
            66.5361176731382,
            33.2680588365691
        };

        // the 60 UTM zones from the OGC TMS specification
        for (int i = 1; i <= 60; i++) {
            String id = "UTM" + (i < 10 ? "0" : "") + i + "WGS84Quad";
            GridSet utmGridset = GridSetFactory.createGridSet(
                    id,
                    SRS.getSRS(32600 + i),
                    new BoundingBox(-9501965.72931276, -20003931.4586255, 10501965.7293128, 20003931.4586255),
                    true,
                    null,
                    UTM_SCALES,
                    1d,
                    GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                    new String[] {
                        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                        "17", "18", "19", "20", "21", "22", "23", "24"
                    },
                    256,
                    256,
                    true);
            // not from the spec, it does not have one
            utmGridset.setDescription(
                    "This Tile Matrix Set defines tiles in the Universal Transverse Mercator, zone " + i);
            addInternal(utmGridset);
            addx2Gridset(utmGridset);
        }

        // UPS Artic
        log.fine("Adding OGC TMS UPSArcticWGS84Quad");
        double[] upsScales = {
            458726544.4,
            229363272.2,
            114681636.1,
            57340818.05,
            28670409.02,
            14335204.51,
            7167602.256,
            3583801.128,
            1791900.564,
            895950.282,
            447975.141,
            223987.5705,
            111993.7852,
            55996.89262,
            27998.44631,
            13999.22316,
            6999.611578,
            3499.805789,
            1749.902894,
            874.9514472,
            437.4757236,
            218.7378618,
            109.3689309,
            54.68446545,
            27.34223273
        };
        BoundingBox upsBounds = new BoundingBox(-14440759.350252, -14440759.350252, 18440759.350252, 18440759.350252);
        final GridSet upsArctic = GridSetFactory.createGridSet(
                "UPSArcticWGS84Quad",
                SRS.getSRS(5041),
                upsBounds,
                true,
                null,
                upsScales,
                1d,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                new String[] {
                    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                    "18", "19", "20", "21", "22", "23", "24"
                },
                256,
                256,
                true);
        // not from the spec, it does not have one
        upsArctic.setDescription(
                "This Tile Matrix Set defines tiles in the Universal Polar Stereographics for the arctic");
        addInternal(upsArctic);
        addx2Gridset(upsArctic);

        // UPS antarctic
        log.fine("Adding OGC TMS UPSAntarcticWGS84Quad");
        final GridSet upsAntarctic = GridSetFactory.createGridSet(
                "UPSAntarcticWGS84Quad",
                SRS.getSRS(5042),
                upsBounds,
                true,
                null,
                upsScales,
                1d,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                new String[] {
                    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                    "18", "19", "20", "21", "22", "23", "24"
                },
                256,
                256,
                true);
        // not from the spec, it does not have one
        upsAntarctic.setDescription(
                "This Tile Matrix Set defines tiles in the Universal Polar Stereographics for the Antarctic");
        addInternal(upsAntarctic);
        addx2Gridset(upsAntarctic);

        log.fine("Adding OGC TMS EuropeanETRS89_LAEAQuad");
        GridSet euETRS89LaeaQuad = GridSetFactory.createGridSet(
                "EuropeanETRS89_LAEAQuad",
                SRS.getSRS(3035),
                new BoundingBox(2000000.0, 1000000.0, 6500000, 5500000.0),
                true,
                null,
                new double[] {
                    62779017.857142866,
                    31389508.928571433,
                    15694754.464285716,
                    7847377.232142858,
                    3923688.616071429,
                    1961844.3080357146,
                    980922.1540178573,
                    490461.07700892864,
                    245230.53850446432,
                    122615.26925223216,
                    61307.63462611608,
                    30653.81731305804,
                    15326.90865652902,
                    7663.45432826451,
                    3831.727164132255,
                    1915.8635820661275,
                },
                1d,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                new String[] {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"},
                256,
                256,
                true);
        // not from the spec, it does not have one
        euETRS89LaeaQuad.setDescription("Lambert Azimuthal Equal Area ETRS89 for Europe");
        addInternal(euETRS89LaeaQuad);
        addx2Gridset(euETRS89LaeaQuad);

        log.fine("Adding OGC TMS CanadianNAD83_LCC");
        GridSet canadianNAD83Lcc = GridSetFactory.createGridSet(
                "CanadianNAD83_LCC",
                SRS.getSRS(3978),
                new BoundingBox(-7786476.885838887, -5153821.09213678, 7148753.233541353, 7928343.534071138),
                true,
                null,
                new double[] {
                    137016643.080905,
                    80320101.1163925,
                    47247118.3037603,
                    28348270.9822562,
                    16536491.4063161,
                    9449423.66075207,
                    5669654.19645125,
                    3307298.28126323,
                    1889884.73215041,
                    1133930.83929025,
                    661459.656252643,
                    396875.793751586,
                    236235.591518802,
                    137016.643080905,
                    80320.1011163925,
                    47247.1183037603,
                    28348.2709822562,
                    16536.4914063161,
                    9449.42366075207,
                    5669.65419645125,
                    3307.29828126323,
                    1889.88473215041,
                    1133.93083929025,
                    661.459656252643,
                    396.875793751586,
                    236.235591518802
                },
                1d,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                new String[] {
                    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
                    "18", "19", "20", "21", "22", "23", "24", "25"
                },
                256,
                256,
                true);
        // not from the spec, it does not have one
        canadianNAD83Lcc.setDescription("Lambert Conformal Conic for Canada");
        addInternal(canadianNAD83Lcc);
        addx2Gridset(canadianNAD83Lcc);
    }

    private void addx2Gridset(GridSet base) {
        assert base.getTileWidth() == 256 && base.getTileHeight() == 256;

        // twice the real estate, half the scale (it's like zooming in, assuming same DPI)
        int levels = base.getNumLevels();
        double[] scales = new double[levels];
        String[] scaleNames = new String[levels];
        for (int i = 0; i < levels; i++) {
            scales[i] = base.getGrid(i).getScaleDenominator() / 2;
            scaleNames[i] = base.getGrid(i).getName();
        }

        GridSet x2 = GridSetFactory.createGridSet(
                base.getName() + "x2",
                base.getSrs(),
                base.getOriginalExtent(),
                base.isTopLeftAligned(),
                null,
                scales,
                base.getMetersPerUnit(),
                base.getPixelSize(),
                scaleNames,
                512,
                512,
                base.isyCoordinateFirst());
        addInternal(x2);
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
            500E6, 250E6, 100E6, 50E6, 25E6, 10E6, 5E6, 2.5E6, 1E6, 500E3, 250E3, 100E3, 50E3, 25E3, 10E3, 5E3, 2.5E3,
            1000, 500, 250, 100
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
