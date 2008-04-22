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

package org.geowebcache.seeder;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.wms.BBOX;

/**
 * This code is very much temporary and will be replaced with a
 * good and multithreaded seeder at the first chance.
 * 
 * @author ak
 */
public class Seeder {
    private static Log log = LogFactory.getLog(org.geowebcache.seeder.Seeder.class);

    TileLayer layer = null;

    public Seeder(TileLayer layer) {
        this.layer = layer;
    }

    /**
     * 
     * @param zoomStart
     * @param zoomStop
     * @param imageFormat
     * @param srs
     * @param bounds
     * @param response
     * @return
     * @throws IOException
     */
    protected int doSeed(int zoomStart, int zoomStop, MimeType mimeType,
            SRS srs, BBOX bounds, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");

        PrintWriter pw = response.getWriter();
        int srsIdx = layer.getSRSIndex(srs);
        int[][] coveredGridLevels = layer.getCoveredGridLevels(srsIdx, bounds);
        int[] metaTilingFactors = layer.getMetaTilingFactors();
        
        infoStart(pw, zoomStart, zoomStop, mimeType, bounds);
        
        for (int level = zoomStart; level <= zoomStop; level++) {
            int[] gridBounds = coveredGridLevels[level];
            
            infoLevelStart(pw, level, gridBounds);
            
            int count = 0;
            
            for (int gridy = gridBounds[1]; gridy <= gridBounds[3];  ) {
                
                for (int gridx = gridBounds[0]; gridx <= gridBounds[2]; ) {
                    
                    infoTile(pw, count++);
                    int[] gridLoc = { gridx , gridy , level };
                    
                    TileRequest tileReq = new TileRequest(gridLoc, mimeType, srs);
                    layer.getResponse(tileReq, "seeder", response);
                    
                    // Next column
                    gridx += metaTilingFactors[0];
                }
                // Next row
                gridy += metaTilingFactors[1];
            }

            log.info("Completed seeding level " + level + " for layer " + layer.getName());
            infoLevelStop(pw);
        }

        infoEnd(pw);
        pw.close();
        return 0;
    }

    private void infoStart(PrintWriter pw, int zoomStart, int zoomStop,
            MimeType mimeType, BBOX bounds) throws IOException {
        if (pw == null) {
            return;
        }
        pw.print("<html><body><table><tr><td>Seeding " + layer.getName() 
        		+ " from level "+ zoomStart + " to level " + zoomStop 
                + " for format " + mimeType.getFormat() 
                + " and bounds " + bounds.getReadableString() 
                + "</td></tr>");
        pw.flush();
    }

    private void infoEnd(PrintWriter pw) throws IOException {
        if (pw == null) {
            return;
        }

        pw.print("</table></body></html>");
    }

    private void infoLevelStart(PrintWriter pw, int level, int[] gridBounds)
    throws IOException {
        if (pw == null) {
            return;
        }

        int tileCountX = (gridBounds[2] - gridBounds[0] + 1);
        int tileCountY = (gridBounds[3] - gridBounds[1] + 1);
        double metaCountX = ((double) tileCountX )/ layer.getMetaTilingFactors()[0];
        double metaCountY = ((double) tileCountY ) / layer.getMetaTilingFactors()[1];
        int metaTileCountX = (int) Math.ceil(metaCountX);
        int metaTileCountY = (int) Math.ceil(metaCountY);
        
        //tileCount / (layer.profile.metaHeight * layer.profile.metaWidth)
        pw.print("<tr><td>Level "
                + level
                + ", ~"
                + metaTileCountX*metaTileCountY
                + " metatile(s) [" + tileCountX*tileCountY
                + " tile(s)]</td></tr><tr><td>");
        pw.flush();
    }

    private void infoLevelStop(PrintWriter pw) throws IOException {
        if (pw == null) {
            return;
        }

        pw.print("</td></tr>");
    }

    private void infoTile(PrintWriter pw, int count) throws IOException {
        if (pw == null) {
            return;
        }

        // System.out.println("Count: " + count);
        pw.print("" + count + ", ");
        pw.flush();
    }
}
