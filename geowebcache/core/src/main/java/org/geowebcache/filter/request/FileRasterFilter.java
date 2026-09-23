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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.filter.request;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;

public class FileRasterFilter extends RasterFilter {

    @Serial
    private static final long serialVersionUID = -6950985531575208956L;

    /** Extensions of the raster formats a matrix can be stored in, all readable by the ImageIO readers. */
    private static final Set<String> RASTER_EXTENSIONS = Set.of("png", "gif", "jpg", "jpeg", "tif", "tiff", "bmp");

    private String storagePath;

    private String fileExtension;

    /** @return the storagePath */
    String getStoragePath() {
        return storagePath;
    }

    /** @param storagePath the storagePath to set */
    void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    /** @return the fileExtension */
    String getFileExtension() {
        return fileExtension;
    }

    /** @param fileExtension the fileExtension to set */
    void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    @Override
    protected BufferedImage loadMatrix(TileLayer layer, String gridSetId, int zoomLevel)
            throws IOException, GeoWebCacheException {
        File fh = getMatrixPath(gridSetId, zoomLevel).toFile();

        if (!fh.exists() || !fh.canRead()) {
            throw new GeoWebCacheException(fh.getAbsolutePath() + " does not exist or is not readable");
        }

        return readMatrix(fh, layer.getGridSubset(gridSetId), zoomLevel, fh.getAbsolutePath());
    }

    /** Decodes the matrix from a {@link File} or {@link InputStream}, checking format and size before decoding. */
    private BufferedImage readMatrix(Object input, GridSubset subset, int zoomLevel, String source)
            throws IOException, GeoWebCacheException {
        int[] widthHeight = calculateWidthHeight(subset, zoomLevel);
        ImageReader reader = getReader();
        try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
            // magic bytes check, some readers (e.g. TIFF) only warn on a foreign header
            if (!reader.getOriginatingProvider().canDecodeInput(iis)) {
                throw new GeoWebCacheException(source + " is not a " + fileExtension + " image");
            }
            reader.setInput(iis, true, true);
            // header only, a huge image must fail before decoding it
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width != widthHeight[0] || height != widthHeight[1]) {
                throw new GeoWebCacheException(source + " has dimensions " + width + "," + height + ", expected "
                        + widthHeight[0] + "," + widthHeight[1]);
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    /** Returns a new reader for the configured extension, the caller must dispose it. */
    private ImageReader getReader() throws IOException {
        // lower case, the JDK readers register lower case suffixes only
        String extension = Objects.toString(fileExtension, "").toLowerCase(Locale.ENGLISH);
        // allowlist, uploaded bytes must not reach whatever reader plugin is on the classpath
        if (!RASTER_EXTENSIONS.contains(extension)) {
            throw new IOException("Unsupported raster file extension: " + fileExtension);
        }
        return ImageIO.getImageReadersBySuffix(extension).next();
    }

    private Path getMatrixPath(String gridSetId, int zoomLevel) throws IOException {
        Path dir = Path.of(storagePath).normalize();
        String fileName = getName() + "_" + gridSetId + "_" + zoomLevel + "." + fileExtension;
        Path file = dir.resolve(fileName).normalize();
        if (!dir.equals(file.getParent())) {
            throw new IOException("Invalid matrix file: " + fileName);
        }
        return file;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if the grid set is not configured for the layer, z is out of its range, or the
     *     data is not an image of the configured format and expected size
     */
    @Override
    public void update(byte[] filterData, TileLayer layer, String gridSetId, int z) throws GeoWebCacheException {
        GridSubset subset = layer.getGridSubset(gridSetId);
        if (subset == null) {
            throw new IllegalArgumentException(
                    "Unknown grid set " + gridSetId + " for layer " + layer.getName() + ": Typo?");
        }
        // the matrices are cached only up to zoomStop
        if (z > getZoomStop() || subset.getCoverage(z) == null) {
            throw new IllegalArgumentException("Zoom level " + z + " is out of range for grid set " + gridSetId);
        }
        try {
            // validate before writing, a bad upload must not replace a readable matrix
            readMatrix(new ByteArrayInputStream(filterData), subset, z, "Uploaded matrix");
            writeMatrix(getMatrixPath(gridSetId, z), filterData);

        } catch (GeoWebCacheException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (IIOException | EOFException e) {
            // decoding failure or data too short, a plain IOException is a storage or configuration problem instead
            throw new IllegalArgumentException("Uploaded matrix is not a readable " + fileExtension + " image", e);
        } catch (IOException e) {
            throw new GeoWebCacheException(
                    this.getName() + " encountered an error while persisting matrix, " + e.getMessage());
        }

        try {
            super.setMatrix(layer, gridSetId, z, true);
        } catch (IOException e) {
            throw new GeoWebCacheException(
                    this.getName() + " encountered an error while loading matrix, " + e.getMessage());
        }
    }

    /** Replaces the matrix file atomically, a concurrent load never sees it half written. */
    private void writeMatrix(Path target, byte[] data) throws IOException {
        // same folder, so the move is a rename; the .tmp suffix never matches a matrix file name
        Path tmp = Files.createTempFile(Path.of(storagePath), null, ".tmp");
        try {
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public void update(TileLayer layer, String gridSetId, int zoomStart, int zoomStop) throws GeoWebCacheException {
        throw new GeoWebCacheException(
                "TileLayer layer, String gridSetId, int z) is not appropriate for FileRasterFilters");
    }

    @Override
    public boolean update(TileLayer layer, String gridSetId) {
        return false;
    }
}
