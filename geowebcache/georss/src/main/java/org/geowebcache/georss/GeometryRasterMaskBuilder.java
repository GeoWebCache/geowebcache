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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.georss;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.datum.PixelInCell;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.NoninvertibleTransformException;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.LiteShape;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.operation.builder.GridToEnvelopeMapper;
import org.geotools.util.logging.Logging;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * An object that builds a mask of tiles affected by geometries
 *
 * @author Gabriel Roldan (OpenGeo)
 * @see org.geowebcache.georss.GeoRSSTileRangeBuilder
 */
public class GeometryRasterMaskBuilder {

    private static final double TILE_BUFFER_RATIO = 1.5;

    private static final double ENVELOPE_BUFFER_RATIO = 1;

    private static final Logger LOGGER =
            Logging.getLogger(GeometryRasterMaskBuilder.class.getName());

    private static final AffineTransform IDENTITY = new AffineTransform();

    /**
     * By zoom level bitmasked images where every pixel represents a tile in the level's {@link
     * GridSubset#getCoverages() grid coverage}
     */
    private final BufferedImage[] byLevelMasks;

    private Graphics2D[] graphics;

    /**
     * Aggregated bounds of all the geometries sent to {@link #setMasksForGeometry}, in grid
     * subset's CRS.Used to calculate maskBounds
     */
    private Envelope aggregatedGeomBounds;

    private final MathTransform[] transformCache;

    private final GridSubset gridSubset;

    private final int maxMaskLevel;

    private int[] metaTilingFactors;

    public GeometryRasterMaskBuilder(
            final GridSubset gridSubset, final int[] metaTilingFactors, final int maxMaskLevel) {

        this.gridSubset = gridSubset;
        this.metaTilingFactors = metaTilingFactors;
        this.maxMaskLevel = maxMaskLevel;

        final int startLevel = getStartLevel();
        final int numLevels = gridSubset.getCoverages().length;
        final int endLevel = numLevels - 1;

        byLevelMasks = new BufferedImage[numLevels];
        transformCache = new MathTransform[numLevels];

        for (int level = startLevel; level <= endLevel; level++) {
            if (level > maxMaskLevel) {
                byLevelMasks[level] = null;
            } else {
                final long[] levelBounds = getGridCoverage(level);
                final long tilesX = (levelBounds[2] + 1) - levelBounds[0];
                final long tilesY = (levelBounds[3] + 1) - levelBounds[1];
                final long numTiles = tilesX * tilesY;

                if (tilesX >= Integer.MAX_VALUE
                        || tilesY >= Integer.MAX_VALUE
                        || numTiles >= Integer.MAX_VALUE) {
                    // this is so because the image's sample model can't cope up with more than
                    // Integer.MAX_VALUE pixels
                    throw new IllegalStateException(
                            "Masking level "
                                    + level
                                    + " would produce a backing image of too many tiles!"
                                    + " Consider setting a lower maxMaskLevel ");
                }

                // BufferedImage with 1-bit per pixel sample model
                BufferedImage mask =
                        new BufferedImage(
                                (int) tilesX, (int) tilesY, BufferedImage.TYPE_BYTE_BINARY);
                byLevelMasks[level] = mask;
            }
        }
        createGraphics();
    }

    private long[] getGridCoverage(final int level) {
        long[][] coveredBounds = gridSubset.getCoverages();
        coveredBounds = gridSubset.expandToMetaFactors(coveredBounds, metaTilingFactors);
        return coveredBounds[level];
    }

    public boolean hasTilesSet() {
        long[][] coveredBounds = getCoveredBounds();
        for (long[] coveredBound : coveredBounds) {
            if (coveredBound != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param geom a geometry to mask the affected tiles for, in this matrix's gridSubSet coordinate
     *     reference system
     */
    public void setMasksForGeometry(final Geometry geom) {
        if (geom == null || geom.isEmpty()) {
            return;
        }

        final int startLevel = getStartLevel();
        final int maxLevel = startLevel + getNumLevels() - 1;

        // loop over only up to the configured max masking level
        final int endLevel = Math.min(maxLevel, this.maxMaskLevel);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Geom: " + geom);
        }

        if (aggregatedGeomBounds == null) {
            aggregatedGeomBounds = new Envelope(geom.getEnvelopeInternal());
        } else {
            aggregatedGeomBounds.expandToInclude(geom.getEnvelopeInternal());
        }

        for (int level = startLevel; level <= endLevel; level++) {
            final Geometry geometryInGridCrs = transformToGridCrs(geom, level);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Geom in grid CRS: " + geometryInGridCrs);
            }

            final Geometry bufferedGeomInGridCrs = geometryInGridCrs.buffer(TILE_BUFFER_RATIO);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Buffered Geom in grid CRS: " + bufferedGeomInGridCrs);
            }

            // do not generalize in LiteShape, it affects the expected masked pixels
            boolean generalize = false;
            // shape used identity transform, as the geometry is already projected
            Shape shape = new LiteShape(bufferedGeomInGridCrs, IDENTITY, generalize);

            Graphics2D graphics = getGraphics(level);
            /*
             * Disable antialiasing explicitly, otherwise the rendering will pick the platform's
             * default potentially producing missing pixels
             */
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(Color.WHITE);
            graphics.fill(shape);
        }
    }

    private Geometry transformToGridCrs(final Geometry geometryInLayerCrs, final int zoomLevel) {
        final MathTransform worldToGrid;
        if (transformCache[zoomLevel] == null) {
            final long[] coverage = getGridCoverage(zoomLevel);
            final BoundingBox coverageBounds = gridSubset.boundsFromRectangle(coverage);
            worldToGrid = getWorldToGridTransform(coverageBounds, coverage);
            transformCache[zoomLevel] = worldToGrid;
        } else {
            worldToGrid = transformCache[zoomLevel];
        }

        Geometry geomInGridCrs;
        try {
            geomInGridCrs = JTS.transform(geometryInLayerCrs, worldToGrid);
        } catch (MismatchedDimensionException | TransformException e) {
            throw new IllegalArgumentException(e);
        }

        return geomInGridCrs;
    }

    private MathTransform getWorldToGridTransform(
            final BoundingBox coverageBounds, final long[] coverage) {

        // //
        //
        // Convert the JTS envelope and get the transform
        //
        // //
        final ReferencedEnvelope genvelope = new ReferencedEnvelope();
        {
            // genvelope.setCoordinateReferenceSystem(layerCrs);
            double x = coverageBounds.getMinX();
            double y = coverageBounds.getMinY();
            double width = coverageBounds.getWidth();
            double height = coverageBounds.getHeight();
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
        } catch (NoninvertibleTransformException | IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }

        return worldToScreen;
    }

    private Graphics2D getGraphics(int level) {
        return graphics[level];
    }

    public void disposeGraphics() {
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

    public void createGraphics() {
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
     * @return the bounds of the set tiles for the given level, or {@code null} if none is set
     */
    public synchronized long[] getCoveredBounds(final int level) {
        if (aggregatedGeomBounds == null) {
            return null;
        }
        /*
         * Get the best fit for the level
         */
        final long[] coverage = getGridCoverage(level);
        final BoundingBox coverageBounds = gridSubset.boundsFromRectangle(coverage);
        final MathTransform worldToGrid = getWorldToGridTransform(coverageBounds, coverage);

        BoundingBox expandedBounds;
        try {
            Envelope coveredLevelEnvelope;
            coveredLevelEnvelope = JTS.transform(aggregatedGeomBounds, worldToGrid);
            Geometry bufferedEnvelopeInGridCrs;
            bufferedEnvelopeInGridCrs =
                    JTS.toGeometry(coveredLevelEnvelope).buffer(ENVELOPE_BUFFER_RATIO);
            coveredLevelEnvelope = bufferedEnvelopeInGridCrs.getEnvelopeInternal();
            MathTransform gridToWorld = worldToGrid.inverse();
            Envelope bufferedEnvelope = JTS.transform(coveredLevelEnvelope, gridToWorld);
            expandedBounds =
                    new BoundingBox(
                            bufferedEnvelope.getMinX(),
                            bufferedEnvelope.getMinY(),
                            bufferedEnvelope.getMaxX(),
                            bufferedEnvelope.getMaxY());
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }

        long[] coveredBounds = gridSubset.getCoverageIntersection(level, expandedBounds);
        return coveredBounds;
    }

    /** Package visible method for testing purposes only! */
    public BufferedImage[] getByLevelMasks() {
        final int numMaskedLevels = Math.min(getNumLevels(), maxMaskLevel + 1);
        BufferedImage[] maskedLevels = new BufferedImage[numMaskedLevels];
        for (int level = 0; level < numMaskedLevels; level++) {
            maskedLevels[level] = byLevelMasks[level];
        }
        return maskedLevels;
    }
}
