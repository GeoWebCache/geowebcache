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
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.util.wms.BBOX;

public class Seeder {
    private static Log log = LogFactory.getLog(org.geowebcache.seeder.Seeder.class);

    TileLayer layer = null;

    public Seeder(TileLayer layer) {
        this.layer = layer;
    }

    public int doSeed(int zoomStart, int zoomStop, ImageMime imageFormat,
            BBOX bounds, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");

        PrintWriter pw = response.getWriter();
        
        int[][] coveredGridLevels = layer.getCoveredGridLevels(bounds);
        int[] metaTilingFactors = layer.getMetaTilingFactors();
        
        infoStart(pw, zoomStart, zoomStop, imageFormat, bounds);
        
        for (int level = zoomStart; level <= zoomStop; level++) {
            int[] gridBounds = coveredGridLevels[level];
            
            infoLevelStart(pw, level, gridBounds);
            
            int count = 0;
            
            for (int gridy = gridBounds[1]; gridy <= gridBounds[3];  ) {
                
                for (int gridx = gridBounds[0]; gridx <= gridBounds[2]; ) {
                    
                    infoTile(pw, count++);
                    int[] gridLoc = { gridx , gridy , level };
                    
                    TileRequest tileReq = new TileRequest(gridLoc, imageFormat.getMimeType());
                    layer.getData(tileReq, "seeder", response);
                    
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
            ImageMime imageFormat, BBOX bounds) throws IOException {
        if (pw == null) {
            return;
        }
        pw.print("<html><body><table><tr><td>Seeding " + layer.getName() 
        		+ " from level "+ zoomStart + " to level " + zoomStop 
                + " for format " + imageFormat.getMimeType() 
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
    
//    public int seed(int zoomStart, int zoomStop, String format, BBOX bounds,
//            HttpServletResponse response) throws IOException {
//
//        String complaint = null;
//
//        // Check that we support this
//        if (bounds == null) {
//            bounds = profile.bbox;
//        } else {
//            if (!profile.bbox.contains(bounds)) {
//                complaint = "Request to seed outside of bounds: "
//                        + bounds.toString();
//                log.error(complaint);
//                response.sendError(400, complaint);
//                return -1;
//            }
//        }
//        
//        ImageMime mime = null;
//        if (format == null) {
//                mime = mimes[0];
//            log.info("User did not specify format for seeding, assuming " + mimes[0].getMimeType());
//        } else {
//                mime = ImageMime.createFromMimeType(format);
//                complaint = supportsMime(mime.getMimeType());
//                
//                if(complaint != null) {
//                        log.error(complaint);
//                        response.sendError(400, complaint);
//                        return -1;
//                }
//        }
//        
//        Seeder seeder = (Seeder) seeders.get(mime.getMimeType());
//        
//        if(seeder == null) {
//                seeder = new Seeder(this);
//                seeders.put(mime.getMimeType(), seeder);
//        }
//        
//        if (profile.expireCache == LayerProfile.CACHE_NEVER) {
//            complaint = "Layers is configured to never cache!";
//            log.error(complaint);
//            response.sendError(400, complaint);
//            return -1;
//        }
//
//        if (zoomStart < 0 || zoomStop < 0) {
//            complaint = "start(" + zoomStart + ") and stop(" + zoomStop
//                    + ") have to greater than zero";
//            log.error(complaint);
//            response.sendError(400, complaint);
//            return -1;
//        }
//        if (zoomStart < profile.zoomStart) {
//            complaint = "start(" + zoomStart
//                    + ") should be greater than or equal to "
//                    + profile.zoomStart;
//            log.error(complaint);
//            response.sendError(400, complaint);
//            return -1;
//        }
//        if (zoomStop > profile.zoomStop) {
//            complaint = "stop(" + zoomStop
//                    + ") should be less than or equal to " + profile.zoomStop;
//            log.error(complaint);
//            response.sendError(400, complaint);
//            return -1;
//        }
//
//        log.info("seeder.doSeed(" + zoomStart + "," + zoomStop + ","
//                + mime.getMimeType() + "," + bounds.toString()
//                + ",stream)");
//
//        int retVal = seeder.doSeed(zoomStart, zoomStop, mime, bounds,
//                response);
//
//        return retVal;
//    }

}
