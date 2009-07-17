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
package org.geowebcache.filter.request;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;

public class FileRasterFilter extends RasterFilter {
    
    String storagePath;
    
    String fileExtension;

    protected BufferedImage loadMatrix(TileLayer layer, SRS srs, int zoomLevel)
    throws IOException {

        return ImageIO.read(
                new File( createFilePath(srs, zoomLevel) )
                );
    }
    
    private String createFilePath(SRS srs, int zoomLevel) {
        String path =  
            storagePath + File.separator 
            + this.name + "_" + "EPSG_" + srs.getNumber() 
            + "_" + zoomLevel + "." + fileExtension;
        
        return path;
    }
    
    public void saveMatrix(byte[] data, TileLayer layer, SRS srs, int zoomLevel) throws IOException {
        // Persist
        File fh = new File( createFilePath(srs, zoomLevel) );
        FileOutputStream fos = new FileOutputStream(fh);
        fos.write(data);
        fos.close();
    }
}
