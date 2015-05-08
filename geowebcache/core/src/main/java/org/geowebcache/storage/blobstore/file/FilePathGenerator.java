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
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.codec.digest.DigestUtils;
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
    public File tilePath(TileObject tile, MimeType mimeType) {
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

        // Override to customize the rootPath by one optional user directory.
        String pathRoot = cacheRoot;
        String optionalPath = tile.getOutputFolder();
        if (optionalPath!=null && optionalPath.length()>0) pathRoot = optionalPath;

        // --------------------------------------------------------------------------
        // Other output formats: (RESTful...)
        
        Map<String,String> parameters2 = tile.getParameters();
        
        String profile = tile.getProfile();
        if (profile==null || profile.length()==0) profile = "GWC";
       	        
        if (profile.equalsIgnoreCase("RESTful"))
        {
            long y_inverse = (long)Math.pow(2,z) - (y+1);
            y = y_inverse;
       	    
            path.append(pathRoot);
            path.append(File.separatorChar);
            appendFiltered(tile.getLayerName(), path);
            path.append(File.separatorChar);
            
            String styleName = "default";
            if (parameters2!=null && parameters2.containsKey("STYLES")) styleName = parameters2.get("STYLES");   
            appendFiltered(styleName, path);
            path.append(File.separatorChar);
            
            appendFiltered(tile.getGridSetId(), path);
            path.append(File.separatorChar);
            path.append(z);
            
            path.append(File.separatorChar);
            path.append(y);
            path.append(File.separatorChar);
            path.append(x);
            path.append('.');
            path.append(fileExtension);

            File tileFile = new File(path.toString());
            return tileFile;
        }
       	
        path.append(pathRoot);
       	
        // --------------------------------------------------------------------------
       	                
        path.append(File.separatorChar);
        appendFiltered(tile.getLayerName(), path);
        path.append(File.separatorChar);
        appendGridsetZoomLevelDir(tile.getGridSetId(), z, path);
        String parametersId = tile.getParametersId();
        Map<String, String> parameters = tile.getParameters();
        if (parametersId == null && parameters != null && !parameters.isEmpty()) {
            parametersId = getParametersId(parameters);
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

    protected static String buildKey(String parametersKvp) {
        return DigestUtils.shaHex(parametersKvp);
    }
    
    /**
     * Returns the parameters identifier for the given parameters map
     * @param parameters
     * @return
     */
    public static String getParametersId(Map<String, String> parameters) {
        if(parameters == null || parameters.size() == 0) {
            return null;
        }
        String parametersKvp = getParametersKvp(parameters);
        return buildKey(parametersKvp);
    }

    /**
     * Turns the parameter list into a sorted KVP string
     * 
     * @param parameters
     * @return
     */
    public static String getParametersKvp(Map<String, String> parameters) {
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


   
}
