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
 *  
 */
package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;

public class FilePathGenerator {
    
    private static Log log = LogFactory.getLog(FilePathGenerator.class);
    
    String cacheRoot;

    public FilePathGenerator(String cacheRoot) {
        this.cacheRoot = cacheRoot;
    }
    
    /**
     * Builds the storage path for a tile and returns it as a File reference
     * <p>
     * </p>
     * 
     * @param layerName
     *            name of the layer the tile belongs to
     * @param tileIndex
     *            the [x,y,z] index for the tile
     * @param gridSetId
     *            the name of the gridset for the tile inside layer
     * @param mimeType
     *            the storage mime type
     * @param parameters_id
     *            the parameters identifier
     * @return File pointer to the tile image
     */
    public File tilePath(TileObject tile, MimeType mimeType) throws IOException {
        final long[] tileIndex = tile.getXYZ();
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];

        StringBuilder path = new StringBuilder(256);

        long shift = z / 2;
        long half = 2 << shift;
        int digits = 1;
        if (half > 10) {
            digits = (int) (Math.log10(half)) + 1;
        }
        long halfx = x / half;
        long halfy = y / half;

        String fileExtension = mimeType.getFileExtension();

        path.append(cacheRoot);
        path.append(File.separatorChar);
        appendFiltered(tile.getLayerName(), path);
        path.append(File.separatorChar);
        appendGridsetZoomLevelDir(tile.getGridSetId(), z, path);
        String parametersId = tile.getParametersId();
        Map<String, String> parameters = tile.getParameters();
        if (parametersId == null && parameters != null && !parameters.isEmpty()) {
            parametersId = getParametersId(tile.getLayerName(), path.toString(), parameters);
            tile.setParametersId(parametersId);
        }
        if(parametersId != null) {
            path.append('_');
            path.append(parametersId);
        }
        path.append(File.separatorChar);
        zeroPadder(halfx, digits, path);
        path.append('_');
        zeroPadder(halfy, digits, path);
        path.append(File.separatorChar);

        zeroPadder(x, 2 * digits, path);
        path.append('_');
        zeroPadder(y, 2 * digits, path);
        path.append('.');
        path.append(fileExtension);

        File tileFile = new File(path.toString());
        return tileFile;
    }

    protected String buildKey(String parametersKvp) {
        return DigestUtils.shaHex(parametersKvp);
    }
    
    protected String getParametersId(String gridSetId, String fileBase, Map<String, String> parameters) throws IOException {
        String parametersKvp = getParametersKvp(parameters);
        return buildKey(parametersKvp);
    }

    /**
     * Turns the parameter list into a sorted KVP string
     * 
     * @param parameters
     * @return
     */
    protected String getParametersKvp(Map<String, String> parameters) {
        StringBuilder sb = new StringBuilder();
        SortedMap<String, String> sorted = new TreeMap<String, String>(parameters);
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if(sb.length() == 0) {
                sb.append("?");
            } else {
                sb.append("&");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        String paramtersKvp = sb.toString();
        return paramtersKvp;
    }
    
    private void appendGridsetZoomLevelDir(String gridSetId, long z, StringBuilder path) {
        appendFiltered(gridSetId, path);
        path.append('_');
        zeroPadder(z, 2, path);
    }


   
}
