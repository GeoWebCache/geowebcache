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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.filter.request;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;

public class FileRasterFilter extends RasterFilter {

    private static final long serialVersionUID = -6950985531575208956L;

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
        File fh = new File(createFilePath(gridSetId, zoomLevel));

        if (!fh.exists() || !fh.canRead()) {
            throw new GeoWebCacheException(
                    fh.getAbsolutePath() + " does not exist or is not readable");
        }

        BufferedImage img = ImageIO.read(fh);

        int[] widthHeight = calculateWidthHeight(layer.getGridSubset(gridSetId), zoomLevel);

        if (img.getWidth() != widthHeight[0] || img.getHeight() != widthHeight[1]) {
            String msg =
                    fh.getAbsolutePath()
                            + " has dimensions "
                            + img.getWidth()
                            + ","
                            + img.getHeight()
                            + ", expected "
                            + widthHeight[0]
                            + ","
                            + widthHeight[1];
            throw new GeoWebCacheException(msg);
        }

        return img;
    }

    private String createFilePath(String gridSetId, int zoomLevel) {
        String path =
                storagePath
                        + File.separator
                        + this.getName()
                        + "_"
                        + gridSetId
                        + "_"
                        + zoomLevel
                        + "."
                        + fileExtension;

        return path;
    }

    public void saveMatrix(byte[] data, TileLayer layer, String gridSetId, int zoomLevel)
            throws IOException {
        // Persist
        File fh = new File(createFilePath(gridSetId, zoomLevel));
        try (FileOutputStream fos = new FileOutputStream(fh); ) {
            fos.write(data);
        }
    }

    @Override
    public void update(byte[] filterData, TileLayer layer, String gridSetId, int z)
            throws GeoWebCacheException {
        try {
            saveMatrix(filterData, layer, gridSetId, z);

        } catch (IOException e) {
            throw new GeoWebCacheException(
                    this.getName()
                            + " encountered an error while persisting matrix, "
                            + e.getMessage());
        }

        try {
            super.setMatrix(layer, gridSetId, z, true);
        } catch (IOException e) {
            throw new GeoWebCacheException(
                    this.getName()
                            + " encountered an error while loading matrix, "
                            + e.getMessage());
        }
    }

    @Override
    public void update(TileLayer layer, String gridSetId, int zoomStart, int zoomStop)
            throws GeoWebCacheException {
        throw new GeoWebCacheException(
                "TileLayer layer, String gridSetId, int z) is not appropriate for FileRasterFilters");
    }

    @Override
    public boolean update(TileLayer layer, String gridSetId) {
        return false;
    }
}
