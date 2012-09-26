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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer;

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.CropDescriptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;
import org.springframework.util.Assert;

public class MetaTile implements TileResponseReceiver {

    private static Log log = LogFactory.getLog(MetaTile.class);

    protected static final RenderingHints NO_CACHE = new RenderingHints(JAI.KEY_TILE_CACHE, null);

    private static final boolean NATIVE_JAI_AVAILABLE;
    static {
        // we directly access the Mlib Image class, if in the classpath it will tell us if
        // the native extensions are available, if not, an Error will be thrown
        boolean nativeJAIAvailable;
        try {
            Class<?> image = Class.forName("com.sun.medialib.mlib.Image");
            nativeJAIAvailable = (Boolean) image.getMethod("isAvailable").invoke(null);
        } catch (Throwable e) {
            nativeJAIAvailable = false;
        }
        NATIVE_JAI_AVAILABLE = nativeJAIAvailable;
        if (!NATIVE_JAI_AVAILABLE) {
            log.warn("********* Native JAI is not installed, meta tile cropping may be slow ********");
        }
    }

    // buffer for storing the metatile, if it is an image
    protected RenderedImage metaTileImage = null;

    protected int[] gutter = new int[4]; // L,B,R,T in pixels

    protected final Rectangle[] tiles;

    // minx,miny,maxx,maxy,zoomlevel
    protected long[] metaGridCov = null;

    // the grid positions of the individual tiles
    protected long[][] tilesGridPositions = null;

    // X metatiling factor, after adjusting to bounds
    protected int metaX;

    // Y metatiling factor, after adjusting to bounds
    protected int metaY;

    protected GridSubset gridSubset;

    protected long status = -1;

    protected boolean error = false;

    protected String errorMessage;

    protected long expiresHeader = -1;

    protected MimeType responseFormat;

    protected FormatModifier formatModifier;

    private int gutterConfig;

    private BoundingBox metaBbox;

    private int metaTileWidth;

    private int metaTileHeight;

    private List<RenderedImage> disposableImages;

    /**
     * The the request format is the format used for the request to the backend.
     * 
     * The response format is what the tiles are actually saved as. The primary example is to use
     * image/png or image/tiff for backend requests, and then save the resulting tiles to JPEG to
     * avoid loss of quality.
     * 
     * @param srs
     * @param responseFormat
     * @param requestFormat
     * @param tileGridPosition
     * @param metaX
     * @param metaY
     * @param gutter2
     */
    public MetaTile(GridSubset gridSubset, MimeType responseFormat, FormatModifier formatModifier,
            long[] tileGridPosition, int metaX, int metaY, Integer gutter) {
        this.gridSubset = gridSubset;
        this.responseFormat = responseFormat;
        this.formatModifier = formatModifier;
        this.metaX = metaX;
        this.metaY = metaY;
        this.gutterConfig = gutter == null ? 0 : gutter.intValue();

        metaGridCov = calculateMetaTileGridBounds(
                gridSubset.getCoverage((int) tileGridPosition[2]), tileGridPosition);
        tilesGridPositions = calculateTilesGridPositions();
        calculateEdgeGutter();
        int tileHeight = gridSubset.getTileHeight();
        int tileWidth = gridSubset.getTileWidth();
        this.tiles = createTiles(tileHeight, tileWidth);
    }

    /***
     * Calculates final meta tile width, height and bounding box
     * <p>
     * Adding a gutter should be really easy, just add to all sides, right ?
     * 
     * But GeoServer / GeoTools, and possibly other WMS servers, can get mad if we exceed 180,90 (or
     * the equivalent for other projections), so we'lll treat those with special care.
     * </p>
     * 
     * @param strBuilder
     * @param metaTileGridBounds
     */
    protected void calculateEdgeGutter() {

        Arrays.fill(this.gutter, 0);

        long[] layerCov = gridSubset.getCoverage((int) this.metaGridCov[4]);

        this.metaBbox = gridSubset.boundsFromRectangle(metaGridCov);

        this.metaTileWidth = metaX * gridSubset.getTileWidth();
        this.metaTileHeight = metaY * gridSubset.getTileHeight();

        double widthRelDelta = ((1.0 * metaTileWidth + gutterConfig) / metaTileWidth) - 1.0;
        double heightRelDelta = ((1.0 * metaTileHeight + gutterConfig) / metaTileHeight) - 1.0;

        double coordWidth = metaBbox.getWidth();
        double coordHeight = metaBbox.getHeight();

        double coordWidthDelta = coordWidth * widthRelDelta;
        double coordHeightDelta = coordHeight * heightRelDelta;

        if (layerCov[0] < metaGridCov[0]) {
            metaTileWidth += gutterConfig;
            gutter[0] = gutterConfig;
            metaBbox.setMinX(metaBbox.getMinX() - coordWidthDelta);
        }
        if (layerCov[1] < metaGridCov[1]) {
            metaTileHeight += gutterConfig;
            gutter[1] = gutterConfig;
            metaBbox.setMinY(metaBbox.getMinY() - coordHeightDelta);
        }
        if (layerCov[2] > metaGridCov[2]) {
            metaTileWidth += gutterConfig;
            gutter[2] = gutterConfig;
            metaBbox.setMaxX(metaBbox.getMaxX() + coordWidthDelta);
        }
        if (layerCov[3] > metaGridCov[3]) {
            metaTileHeight += gutterConfig;
            gutter[3] = gutterConfig;
            metaBbox.setMaxY(metaBbox.getMaxY() + coordHeightDelta);
        }
    }

    public BoundingBox getMetaTileBounds() {
        return metaBbox;
    }

    public int getMetaTileWidth() {
        return metaTileWidth;
    }

    public int getMetaTileHeight() {
        return metaTileHeight;
    }

    public int getStatus() {
        return (int) status;
    }

    public void setStatus(int status) {
        this.status = (long) status;
    }

    public boolean getError() {
        return this.error;
    }

    public void setError() {
        this.error = true;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getExpiresHeader() {
        return this.expiresHeader;
    }

    public void setExpiresHeader(long seconds) {
        this.expiresHeader = seconds;
    }

    public void setImageBytes(Resource buffer) throws GeoWebCacheException {
        Assert.notNull(buffer, "WMSMetaTile.setImageBytes() received null");
        Assert.isTrue(buffer.getSize() > 0, "WMSMetaTile.setImageBytes() received empty contents");

        try {
            ImageInputStream imgStream;
            imgStream = new ResourceImageInputStream(((ByteArrayResource) buffer).getInputStream());
            RenderedImage metaTiledImage = ImageIO.read(imgStream);// read closes the stream for us
            setImage(metaTiledImage);
        } catch (IOException ioe) {
            throw new GeoWebCacheException("WMSMetaTile.setImageBytes() "
                    + "failed on ImageIO.read(byte[" + buffer.getSize() + "])", ioe);
        }
        if (metaTileImage == null) {
            throw new GeoWebCacheException(
                    "ImageIO.read(InputStream) returned null. Unable to read image.");
        }
    }

    public void setImage(RenderedImage metaTiledImage) {
        this.metaTileImage = metaTiledImage;
    }

    /**
     * Cuts the metaTile into the specified number of tiles, the actual number of tiles is
     * determined by metaX and metaY, not the width and height provided here.
     * 
     * @param tileWidth
     *            width of each tile
     * @param tileHeight
     *            height of each tile
     * @return
     */
    private Rectangle[] createTiles(int tileHeight, int tileWidth) {
        int tileCount = metaX * metaY;
        Rectangle[] tiles = new Rectangle[tileCount];

        for (int y = 0; y < metaY; y++) {
            for (int x = 0; x < metaX; x++) {
                int i = x * tileWidth + gutter[0];
                int j = (metaY - 1 - y) * tileHeight + gutter[3];

                // tiles[y * metaX + x] = createTile(i, j, tileWidth, tileHeight, useJAI);
                tiles[y * metaX + x] = new Rectangle(i, j, tileWidth, tileHeight);
            }
        }
        return tiles;
    }

    /**
     * Extracts a single tile from the metatile.
     * 
     * @param minX
     *            left pixel index to crop the meta tile at
     * @param minY
     *            top pixel index to crop the meta tile at
     * @param tileWidth
     *            width of the tile
     * @param tileHeight
     *            height of the tile
     * @return a rendered image of the specified meta tile region
     */
    public RenderedImage createTile(final int minX, final int minY, final int tileWidth,
            final int tileHeight) {

        // do a crop, and then turn it into a buffered image so that we can release
        // the image chain
        RenderedOp cropped = CropDescriptor.create(metaTileImage, Float.valueOf(minX),
                Float.valueOf(minY), Float.valueOf(tileWidth), Float.valueOf(tileHeight), NO_CACHE);
        if (nativeAccelAvailable()) {
            log.trace("created cropped tile");
            return cropped;
        }
        log.trace("native accel not available, returning buffered image");
        BufferedImage tile = cropped.getAsBufferedImage();
        disposePlanarImageChain(cropped, new HashSet<PlanarImage>());
        return tile;
    }

    protected boolean nativeAccelAvailable() {
        return NATIVE_JAI_AVAILABLE;
    }

    /**
     * Outputs one tile from the internal array of tiles to a provided stream
     * 
     * @param tileIdx
     *            the index of the tile relative to the internal array
     * @param format
     *            the Java name for the format
     * @param resource
     *            the outputstream
     * @return true if no error was encountered
     * @throws IOException
     */
    public boolean writeTileToStream(final int tileIdx, Resource target) throws IOException {
        if (tiles == null) {
            return false;
        }
        String format = responseFormat.getInternalName();

        if (log.isDebugEnabled()) {
            log.debug("Thread: " + Thread.currentThread().getName() + " writing: " + tileIdx);
        }
        
        // TODO should we recycle the writers ?
        // GR: it'd be only a 2% perf gain according to profiler
        Iterator<ImageWriter> it = javax.imageio.ImageIO.getImageWritersByFormatName(format);
        ImageWriter writer = it.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (this.formatModifier != null) {
            param = formatModifier.adjustImageWriteParam(param);
        }

        Rectangle tileRegion = tiles[tileIdx];
        RenderedImage tile = createTile(tileRegion.x, tileRegion.y, tileRegion.width,
                tileRegion.height);
        disposeLater(tile);
        OutputStream outputStream = target.getOutputStream();
        ImageOutputStream imgOut = new MemoryCacheImageOutputStream(outputStream);
        writer.setOutput(imgOut);
        IIOImage image = new IIOImage(tile, null, null);
        try {
            writer.write(null, image, param);
        } finally {
            imgOut.close();
            writer.dispose();
        }

        return true;
    }

    protected void disposeLater(RenderedImage tile) {
        if (disposableImages == null) {
            disposableImages = new ArrayList<RenderedImage>(tiles.length);
        }
        disposableImages.add(tile);
    }

    public String debugString() {
        return " metaX: " + metaX + " metaY: " + metaY + " metaGridCov: "
                + Arrays.toString(metaGridCov);
    }

    /**
     * Figures out the bounds of the metatile, in terms of the gridposition of all contained tiles.
     * To get the BBOX you need to add one tilewidth to the top and right.
     * 
     * It also updates metaX and metaY to the actual metatiling factors
     * 
     * @param gridBounds
     * @param tileGridPosition
     * @return
     */
    private long[] calculateMetaTileGridBounds(long[] coverage, long[] tileIdx) {
        long[] metaGridCov = new long[5];
        metaGridCov[0] = tileIdx[0] - (tileIdx[0] % metaX);
        metaGridCov[1] = tileIdx[1] - (tileIdx[1] % metaY);
        metaGridCov[2] = Math.min(metaGridCov[0] + metaX - 1, coverage[2]);
        metaGridCov[3] = Math.min(metaGridCov[1] + metaY - 1, coverage[3]);
        metaGridCov[4] = tileIdx[2];

        // Save the actual metatiling factor, important at the boundaries
        metaX = (int) (metaGridCov[2] - metaGridCov[0] + 1);
        metaY = (int) (metaGridCov[3] - metaGridCov[1] + 1);

        return metaGridCov;
    }

    /**
     * Creates an array with all the grid positions, used for cache keys
     */
    private long[][] calculateTilesGridPositions() {
        if (metaX < 0 || metaY < 0) {
            return null;
        }

        long[][] tilesGridPos = new long[metaX * metaY][3];

        for (int y = 0; y < metaY; y++) {
            for (int x = 0; x < metaX; x++) {
                int tile = y * metaX + x;
                tilesGridPos[tile][0] = metaGridCov[0] + x;
                tilesGridPos[tile][1] = metaGridCov[1] + y;
                tilesGridPos[tile][2] = metaGridCov[4];
            }
        }

        return tilesGridPos;
    }

    /**
     * The bottom left grid position and zoomlevel for this metatile, used for locking.
     * 
     * @return
     */
    public long[] getMetaGridPos() {
        long[] gridPos = { metaGridCov[0], metaGridCov[1], metaGridCov[4] };
        return gridPos;
    }

    /**
     * The bounds for the metatile
     * 
     * @return
     */
    public long[] getMetaTileGridBounds() {
        return metaGridCov;
    }

    public long[][] getTilesGridPositions() {
        return tilesGridPositions;
    }

    public SRS getSRS() {
        return this.gridSubset.getSRS();
    }

    public MimeType getResponseFormat() {
        return this.responseFormat;
    }

    public MimeType getRequestFormat() {
        if (formatModifier == null) {
            return this.responseFormat;
        } else {
            return this.formatModifier.getRequestFormat();
        }
    }

    /**
     * Should be called as soon as the meta tile is no longer needed in order to dispose any held
     * resource
     */
    public void dispose() {
        if (metaTileImage == null) {
            return;
        }
        RenderedImage image = metaTileImage;
        metaTileImage = null;

        if (log.isTraceEnabled()) {
            log.trace("disposing metatile " + image);
        }
        if (image instanceof BufferedImage) {
            ((BufferedImage) image).flush();
        } else if (image instanceof PlanarImage) {
            disposePlanarImageChain((PlanarImage) image, new HashSet<PlanarImage>());
        }
        if (disposableImages != null) {
            for (RenderedImage tile : disposableImages) {
                if (log.isTraceEnabled()) {
                    log.trace("disposing tile " + tile);
                }
                if (tile instanceof BufferedImage) {
                    ((BufferedImage) tile).flush();
                } else if (tile instanceof PlanarImage) {
                    disposePlanarImageChain((PlanarImage) tile, new HashSet<PlanarImage>());
                }
            }
        }
        disposableImages = null;
    }

    @SuppressWarnings("rawtypes")
    protected static void disposePlanarImageChain(PlanarImage pi, HashSet<PlanarImage> visited) {
        Vector sinks = pi.getSinks();
        // check all the sinks (the image might be in the middle of a chain)
        if (sinks != null) {
            for (Object sink : sinks) {
                if (sink instanceof PlanarImage && !visited.contains(sink)) {
                    disposePlanarImageChain((PlanarImage) sink, visited);
                } else if (sink instanceof BufferedImage) {
                    ((BufferedImage) sink).flush();
                }
            }
        }
        // dispose the image itself
        pi.dispose();
        visited.add(pi);

        // check the image sources
        Vector sources = pi.getSources();
        if (sources != null) {
            for (Object child : sources) {
                if (child instanceof PlanarImage && !visited.contains(child)) {
                    disposePlanarImageChain((PlanarImage) child, visited);
                } else if (child instanceof BufferedImage) {
                    ((BufferedImage) child).flush();
                }
            }
        }

        // ImageRead might also hold onto a image input stream that we have to close
        if (pi instanceof RenderedOp) {
            RenderedOp op = (RenderedOp) pi;
            for (Object param : op.getParameterBlock().getParameters()) {
                if (param instanceof ImageInputStream) {
                    ImageInputStream iis = (ImageInputStream) param;
                    try {
                        iis.close();
                    } catch (IOException e) {
                        // fine, we tried
                    }
                }
            }
        }
    }
}
