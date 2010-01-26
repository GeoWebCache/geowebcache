package org.geowebcache.georss;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.LiteShape;
import org.geotools.referencing.operation.builder.GridToEnvelopeMapper;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * An object that builds a mask of tiles affected by geometries
 * 
 * @author Gabriel Roldan (OpenGeo)
 * @see GeoRSSTileRangeBuilder
 */
public class TileGridFilterMatrix {

    private static final Log logger = LogFactory.getLog(TileGridFilterMatrix.class);

    private static final AffineTransform IDENTITY = new AffineTransform();

    /**
     * By zoom level bitmasked images where every pixel represents a tile in the level's
     * {@link GridSubset#getCoverages() grid coverage}
     */
    private final BufferedImage[] byLevelMasks;

    private Graphics2D[] graphics;

    /**
     * Used to calculate maskBounds
     */
    private Envelope aggregatedBounds;

    private long[][] maskBounds;

    private final MathTransform[] transformCache;

    private final GridSubset gridSubset;

    private long totalTilesSet;

    private final int maxMaskLevel;

    public TileGridFilterMatrix(final GridSubset gridSubset, final int maxMaskLevel) {

        this.gridSubset = gridSubset;
        this.totalTilesSet = -1;// compute on demand
        this.maxMaskLevel = maxMaskLevel;

        final int numLevels = gridSubset.getCoverages().length;

        byLevelMasks = new BufferedImage[numLevels];
        transformCache = new MathTransform[numLevels];

        for (int level = 0; level < numLevels; level++) {
            if (level > maxMaskLevel) {
                byLevelMasks[level] = null;
            } else {
                final long[] levelBounds = gridSubset.getCoverage(level);
                final long tilesX = (levelBounds[2] + 1) - levelBounds[0];
                final long tilesY = (levelBounds[3] + 1) - levelBounds[1];
                final long numTiles = tilesX * tilesY;

                if (tilesX >= Integer.MAX_VALUE || tilesY >= Integer.MAX_VALUE
                        || numTiles >= Integer.MAX_VALUE) {
                    // this is so because the image's sample model can't cope up with more than
                    // Integer.MAX_VALUE pixels
                    throw new IllegalStateException("Masking level " + level
                            + " would produce a backing image of too many tiles!"
                            + " Consider setting a lower maxMaskLevel ");
                }

                // BufferedImage with 1-bit per pixel sample model
                BufferedImage mask = new BufferedImage((int) tilesX, (int) tilesY,
                        BufferedImage.TYPE_BYTE_BINARY);
                byLevelMasks[level] = mask;
            }
        }
    }

    public boolean hasTilesSet() {
        long[][] coveredBounds = getCoveredBounds();
        for (int i = 0; i < coveredBounds.length; i++) {
            if (coveredBounds[i] != null) {
                return true;
            }
        }
        return false;
    }

    public synchronized long getTotalTilesSet() {
        if (totalTilesSet == -1) {
            totalTilesSet = computeTotalTilesSet();
        }
        return totalTilesSet;
    }

    private long computeTotalTilesSet() {
        final int startLevel = getStartLevel();
        final int endLevel = startLevel + getNumLevels() - 1;

        long tilesSet = 0;

        for (int level = startLevel; level <= endLevel; level++) {
            long[] levelBounds = getCoveredBounds(level);
            if (levelBounds == null) {
                continue;
            }
            final Raster raster = getMaskFor(level);
            for (long y = levelBounds[1]; y <= levelBounds[3]; y++) {
                for (long x = levelBounds[0]; x <= levelBounds[2]; x++) {
                    if (isTileSet(raster, x, y, level)) {
                        tilesSet++;
                    }
                }
            }
        }
        return tilesSet;
    }

    private Raster getMaskFor(final int level) {
        final int actualLevel = Math.min(level, maxMaskLevel);
        return byLevelMasks[actualLevel].getRaster();
    }

    /**
     * 
     * @param geom
     *            a geometry to mask the affected tiles for, in this matrix's gridSubSet coordinate
     *            reference system
     */
    void setMasksForGeometry(final Geometry geom) {
        if (geom == null || geom.isEmpty()) {
            return;
        }

        if (aggregatedBounds == null) {
            aggregatedBounds = new Envelope(geom.getEnvelopeInternal());
        } else {
            aggregatedBounds.expandToInclude(geom.getEnvelopeInternal());
        }

        final int startLevel = getStartLevel();
        final int maxLevel = startLevel + getNumLevels() - 1;

        // loop over only up to the configured max masking level
        final int endLevel = Math.min(maxLevel, this.maxMaskLevel);

        if (logger.isDebugEnabled()) {
            logger.debug("Geom: " + geom);
        }
        for (int level = startLevel; level <= endLevel; level++) {
            final Geometry geometryInGridCrs = transformToGridCrs(geom, level);
            if (logger.isDebugEnabled()) {
                logger.debug("Geom in grid CRS: " + geometryInGridCrs);
            }

            final Geometry bufferedGeomInGridCrs = geometryInGridCrs.buffer(1.5);

            if (logger.isDebugEnabled()) {
                logger.debug("Buffered Geom in grid CRS: " + bufferedGeomInGridCrs);
            }

            // do not generalize in LiteShape, it affects the expected masked pixels
            boolean generalize = false;
            // shape used identity transform, as the geometry is already projected
            Shape shape = new LiteShape(bufferedGeomInGridCrs, IDENTITY, generalize);

            Graphics2D graphics = getGraphics(level);
            graphics.setColor(Color.WHITE);
            graphics.fill(shape);
        }
    }

    private Geometry transformToGridCrs(final Geometry geometryInLayerCrs, final int zoomLevel) {
        final MathTransform worldToGrid;
        if (transformCache[zoomLevel] == null) {
            final BoundingBox coverageBounds = gridSubset.getCoverageBounds(zoomLevel);
            final long[] coverage = gridSubset.getCoverage(zoomLevel);
            worldToGrid = getWorldToGridTransform(coverageBounds, coverage);
            transformCache[zoomLevel] = worldToGrid;
        } else {
            worldToGrid = transformCache[zoomLevel];
        }

        Geometry geomInGridCrs;
        try {
            geomInGridCrs = JTS.transform(geometryInLayerCrs, worldToGrid);
        } catch (MismatchedDimensionException e) {
            throw new IllegalArgumentException(e);
        } catch (TransformException e) {
            throw new IllegalArgumentException(e);
        }

        return geomInGridCrs;
    }

    private MathTransform getWorldToGridTransform(final BoundingBox coverageBounds,
            final long[] coverage) {

        // //
        //
        // Convert the JTS envelope and get the transform
        //
        // //
        final Envelope2D genvelope = new Envelope2D();
        {
            // genvelope.setCoordinateReferenceSystem(layerCrs);
            double coords[] = coverageBounds.coords.clone();
            double x = coords[0];
            double y = coords[1];
            double width = coords[2] - x;
            double height = coords[3] - y;
            genvelope.setFrame(x, y, width, height);
        }
        final Rectangle paintArea = new Rectangle();
        {
            int x = (int) coverage[0];
            int y = (int) coverage[1];
            int width = (int) (1 + coverage[2] - x);
            int height = (int) (1 + coverage[3] - y);
            paintArea.setBounds(x, y, width, height);
            // System.out
            // .println("Grid: " + JTS.toGeometry(new Envelope(x, x + width, y, y + height)));
        }

        final MathTransform worldToScreen;
        // //
        //
        // Get the transform
        //
        // //
        final GridToEnvelopeMapper mapper = new GridToEnvelopeMapper();
        mapper.setPixelAnchor(PixelInCell.CELL_CORNER);

        mapper.setGridRange(new GridEnvelope2D(paintArea));
        mapper.setEnvelope(genvelope);
        mapper.setSwapXY(false);
        try {
            worldToScreen = mapper.createTransform().inverse();
        } catch (org.opengis.referencing.operation.NoninvertibleTransformException e) {
            throw new IllegalArgumentException(e);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }

        return worldToScreen;
    }

    private Graphics2D getGraphics(int level) {
        return graphics[level];
    }

    void disposeGraphics() {
        if (graphics == null) {
            return;
        }
        final int numLevels = getNumLevels();
        for (int level = 0; level < numLevels; level++) {
            if (graphics[level] != null) {
                graphics[level].dispose();
            }
        }
        graphics = null;
    }

    void createGraphics() {
        final int numLevels = getNumLevels();
        graphics = new Graphics2D[numLevels];
        for (int level = 0; level < numLevels; level++) {
            BufferedImage bufferedImage = byLevelMasks[level];
            if (bufferedImage != null) {
                graphics[level] = bufferedImage.createGraphics();
            }
        }
    }

    public int getStartLevel() {
        // hardcoded to zero for now, we can see whether level filtering is going to be needed
        // afterwards
        return 0;
    }

    public int getNumLevels() {
        return byLevelMasks.length;
    }

    public synchronized long[][] getCoveredBounds() {
        long[][] coveredBounds = new long[getNumLevels()][4];

        for (int i = 0; i < coveredBounds.length; i++) {
            coveredBounds[i] = getCoveredBounds(i);
        }
        return coveredBounds;
    }

    /**
     * Returns the tile range of the mask bounding box at a specific zoom level.
     * 
     * @param i
     * @return the bounds of the set tiles for the given level, or {@code null} if none is set
     */
    public synchronized long[] getCoveredBounds(final int level) {
        if (aggregatedBounds == null) {
            return null;
        }
        if (maskBounds == null) {
            double minx = aggregatedBounds.getMinX();
            double miny = aggregatedBounds.getMinY();
            double maxx = aggregatedBounds.getMaxX();
            double maxy = aggregatedBounds.getMaxY();
            BoundingBox reqBounds = new BoundingBox(minx, miny, maxx, maxy);
            long[][] coverageIntersections = gridSubset.getCoverageIntersections(reqBounds);
            maskBounds = coverageIntersections;
        }
        long[] bounds = maskBounds[level];
        return bounds == null ? null : bounds.clone();
    }

    private long[] calculateMaskBounds(final int level) {
        final Raster raster = getMaskFor(level);
        return calculateMaskBounds(raster, level);
    }

    private long[] calculateMaskBounds(final Raster raster, final int level) {

        if (level > maxMaskLevel) {
            return estimateNonMaskedCoveredBounds(level);
        }

        final long[] coverage = gridSubset.getCoverage(level);

        long minx = Long.MAX_VALUE;
        long miny = Long.MAX_VALUE;
        long maxx = Long.MIN_VALUE;
        long maxy = Long.MIN_VALUE;

        final long minBoundedX = coverage[0];
        final long minBoundedY = coverage[1];
        final long maxBoundedX = coverage[2] - minBoundedX;
        final long maxBoundedY = coverage[3] - minBoundedY;

        boolean tileSet;

        for (long y = minBoundedY; y <= maxBoundedY; y++) {
            for (long x = minBoundedX; x <= maxBoundedX; x++) {
                try {
                    tileSet = isTileSet(raster, x, y, level);
                } catch (ArrayIndexOutOfBoundsException e) {
                    logger.fatal("AIOBE at " + x + ", " + y + ", " + level, e);
                    throw e;
                }
                if (tileSet) {
                    minx = Math.min(minx, x);
                    miny = Math.min(miny, y);
                    maxx = Math.max(maxx, x);
                    maxy = Math.max(maxy, y);
                }
            }
        }
        if (minx == Long.MAX_VALUE) {
            return null;
        }

        return new long[] { minx, miny, maxx, maxy };
    }

    private long[] estimateNonMaskedCoveredBounds(final int level) {
        assert level > maxMaskLevel;
        return null;
    }

    private boolean isTileSet(final Raster raster, long tileX, long tileY, int level) {

        long[] coverage = gridSubset.getCoverage(level);
        if (tileX < coverage[0] || tileX > coverage[2] || tileY < coverage[1]
                || tileY > coverage[3]) {
            return false;
        }

        if (level > maxMaskLevel) {
            // downsample
            long[] requestedCoverage = gridSubset.getCoverage(level);
            long[] lastMaskedCoverage = gridSubset.getCoverage(maxMaskLevel);

            double requestedW = 1 + requestedCoverage[2] - requestedCoverage[0];
            double requestedH = 1 + requestedCoverage[3] - requestedCoverage[1];

            double availableW = 1 + lastMaskedCoverage[2] - lastMaskedCoverage[0];
            double availableH = 1 + lastMaskedCoverage[3] - lastMaskedCoverage[1];

            tileX = Math.round(tileX * (availableW / requestedW));
            tileY = Math.round(tileY * (availableH / requestedH));
            tileX = Math.max(Math.min(tileX, lastMaskedCoverage[2]), lastMaskedCoverage[0]);
            tileY = Math.max(Math.min(tileY, lastMaskedCoverage[3]), lastMaskedCoverage[1]);

            level = maxMaskLevel;
            coverage = gridSubset.getCoverage(level);
        }
        final long deltaX = coverage[0];
        final long deltaY = coverage[1];
        tileX -= deltaX;
        tileY -= deltaY;

        final int nBands = 1;
        int[] buff = new int[nBands];
        int x = (int) tileX;
        // raster is rendered to Y axis is inverted
        int height = raster.getHeight();
        int y = height - 1 - (int) tileY;
        try {
            raster.getPixel(x, y, buff);
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.fatal("AIOBE at " + x + ", " + y + ", " + level, e);
            throw e;
        }
        int sample = buff[0];
        return sample == 1;
    }

    // private void setTiles(WritableRaster matrix, final long[] rect, final boolean active,
    // final int level) {
    // final long[] coverage = gridSubset.getCoverage(level);
    // final long deltaX = coverage[0];
    // final long deltaY = coverage[1];
    // final int minx = (int) (rect[0] - deltaX);
    // final int maxx = (int) (rect[2] - deltaX);
    //
    // final int height = matrix.getHeight();
    // final int miny = height - 1 - (int) (rect[3] - deltaY);
    // final int maxy = height - 1 - (int) (rect[1] - deltaY);
    //
    // int[] value = { active ? 1 : 0 };
    // for (int y = miny; y <= maxy; y++) {
    // for (int x = minx; x <= maxx; x++) {
    // matrix.setPixel(x, y, value);
    // }
    // }
    // }

    // private void setTile(WritableRaster matrix, final boolean set, long tileX, long tileY,
    // final int level) {
    //
    // final long[] coverage = gridSubset.getCoverage(level);
    // final long deltaX = coverage[0];
    // final long deltaY = coverage[1];
    // tileX -= deltaX;
    // tileY -= deltaY;
    // int[] value = new int[] { set ? 1 : 0 };
    // matrix.setPixel((int) tileX, (int) tileY, value);
    // }

    /**
     * Checks and return whether a given tile is marked as "present" in the matrix.
     * <p>
     * That is, whether a given geometry sent to {@link #setMasksForGeometry} affects the provided
     * tile
     * </p>
     * 
     * @param x
     * @param y
     * @param level
     * @return
     */
    public boolean isTileSet(final long x, final long y, final int level) {
        final Raster raster = getMaskFor(level);
        return isTileSet(raster, x, y, level);
    }

    /**
     * Package visible method for testing purposes only!
     * 
     * @return
     */
    public BufferedImage[] getByLevelMasks() {
        final int numMaskedLevels = Math.min(getNumLevels(), maxMaskLevel);
        BufferedImage[] maskedLevels = new BufferedImage[numMaskedLevels];
        for (int level = 0; level < numMaskedLevels; level++) {
            maskedLevels[level] = byLevelMasks[level];
        }
        return maskedLevels;
    }

    // TODO: get back to life (and finish testing) if we decide to use this approach for truncation

    // public long[][] splitAffectedTileRanges(final int zoomLevel) {
    // WritableRaster matrix = copyOfMatrix(zoomLevel);
    //
    // boolean tileSet;
    //
    // List<long[]> rects = new ArrayList<long[]>();
    //
    // // - find the first set pixel, then follow the maximum rectangle that contains it
    // // - add the rectangle to the list, mark all its pixels as not set
    // //
    // long[] bounds;
    //
    // while ((bounds = calculateMaskBounds(matrix, zoomLevel)) != null) {
    // for (long y = bounds[1]; y <= bounds[3]; y++) {
    // for (long x = bounds[0]; x <= bounds[2]; x++) {
    // tileSet = isTileSet(matrix, x, y, zoomLevel);
    // if (tileSet) {
    // long[] rect = findRectangleOf(x, y, matrix, bounds, zoomLevel);
    // setTiles(matrix, rect, false, zoomLevel);
    // // Geometry remainingSetTiles = createAffectedTilesGeometry(geomFac, matrix,
    // // zoomLevel);
    // // System.out.println("remainingSetTiles: " + remainingSetTiles + "\nGeom: "
    // // + polygon(geomFac, rect));
    // rects.add(rect);
    // bounds[0] = Math.min(bounds[0], rect[0]);
    // bounds[1] = Math.min(bounds[1], rect[1]);
    // bounds[2] = Math.max(bounds[2], rect[2]);
    // bounds[3] = Math.max(bounds[3], rect[3]);
    // // polygons.add(polygon(geomFac, rect));
    // break;
    // }
    // }
    // }
    // }
    //
    // // final Geometry geom = createAffectedTilesGeometry(geomFac, zoomLevel);
    // // System.err.println("Affected tiles: " + geom);
    // // MultiPolygon multiPolygon = geomFac.createMultiPolygon(polygons
    // // .toArray(new Polygon[polygons.size()]));
    // // System.err.println("split bounds: " + multiPolygon);
    //
    // return rects.toArray(new long[rects.size()][]);
    // }
    //
    // private long[] findRectangleOf(final long tileX, final long tileY, final Raster matrix,
    // final long[] bounds, final int zoomLevel) {
    //
    // final long minx = tileX;
    // final long miny = tileY;
    // long maxx = bounds[2];
    // long maxy = bounds[3];
    //
    // long[] rect = { minx, miny, minx, miny };
    //
    // for (long y = miny; y <= maxy; y++) {
    // if (!isTileSet(minx, y, zoomLevel)) {
    // // new row is not set at minx, new Y, we're done
    // break;
    // }
    // for (long x = minx; x <= maxx; x++) {
    // if (isTileSet(x, y, zoomLevel)) {
    // rect[2] = x;
    // rect[3] = y;
    // } else {
    // maxx = x - 1;// new maxx
    // break;// next row
    // }
    // }
    // }
    //
    // return rect;
    // }
    //
    // private WritableRaster copyOfMatrix(final int zoomLevel) {
    // final BufferedImage original = this.byLevelMasks[zoomLevel];
    // WritableRaster or = original.getRaster();
    // Point location = new Point(or.getMinX(), or.getMinY());
    // WritableRaster copy = Raster.createWritableRaster(or.getSampleModel(), location);
    // copy.setDataElements(location.x, location.y, or);
    // return copy;
    // }
    //
    // private Geometry createAffectedTilesGeometry(final GeometryFactory gfac, final int level) {
    // Raster matrix = byLevelMasks[level].getRaster();
    // return createAffectedTilesGeometry(gfac, matrix, level);
    // }
    //
    // private Geometry createAffectedTilesGeometry(final GeometryFactory gfac, final Raster matrix,
    // final int level) {
    //
    // final long[] bounds = getCoveredBounds(level);
    //
    // final long miny = bounds[1];
    // final long maxy = bounds[3];
    // final long minx = bounds[0];
    // final long maxx = bounds[2];
    //
    // Geometry currGeom = null;
    // Polygon tilePolygon;
    //
    // boolean tileSet;
    //
    // for (long y = miny; y <= maxy; y++) {
    // for (long x = minx; x <= maxx; x++) {
    // tileSet = isTileSet(matrix, x, y, level);
    // if (tileSet) {
    // tilePolygon = polygon(gfac, x, y);
    // if (currGeom == null) {
    // currGeom = tilePolygon;
    // } else {
    // currGeom = currGeom.union(tilePolygon);
    // }
    // }
    // }
    // }
    //
    // if (currGeom == null || currGeom.isEmpty()) {
    // return null;
    // }
    // currGeom = TopologyPreservingSimplifier.simplify(currGeom, 0.2);
    // return currGeom;
    // }
    //
    // private Polygon polygon(final GeometryFactory gfac, long[] rect) {
    // long x1 = rect[0];
    // long y1 = rect[1];
    // long x2 = rect[2] + 1;
    // long y2 = rect[3] + 1;
    // Coordinate[] coordinates = {//
    // new Coordinate(x1, y1),//
    // new Coordinate(x1, y2),//
    // new Coordinate(x2, y2),//
    // new Coordinate(x2, y1),//
    // new Coordinate(x1, y1) //
    // };
    // LinearRing shell = gfac.createLinearRing(coordinates);
    // return gfac.createPolygon(shell, null);
    // }
    //
    // private Polygon polygon(GeometryFactory gfac, long x, long y) {
    // return polygon(gfac, new long[] { x, y, x, y });
    // }

}