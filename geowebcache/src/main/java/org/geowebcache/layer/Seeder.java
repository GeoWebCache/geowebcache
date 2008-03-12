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

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Seeder {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.Seeder.class);

    TileLayer layer = null;

    public Seeder(TileLayer layer) {
        this.layer = layer;
    }

    public int doSeed(int zoomStart, int zoomStop, ImageFormat imageFormat,
            BBOX bounds, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");

        PrintWriter pw = response.getWriter();

        infoStart(pw, zoomStart, zoomStop, bounds);

        GridCalculator gc = null;
        if (layer.profile.srs.equalsIgnoreCase("EPSG:4326")) {
            gc = new GridCalculator(layer.profile, bounds, 180.0, 180.0);
        } else if (layer.profile.srs.equalsIgnoreCase("EPSG:900913")) {
            gc = new GridCalculator(layer.profile, bounds, 20037508.34*2, 20037508.34*2);
        }
        
        for (int level = zoomStart; level <= zoomStop; level++) {
            int[] gridBounds = gc.getGridBounds(level);
            infoLevelStart(pw, level, gridBounds);
            
            int count = 0;
            
            for (int gridy = gridBounds[1]; gridy <= gridBounds[3];  ) {
                
                for (int gridx = gridBounds[0]; gridx <= gridBounds[2]; ) {
                    
                    infoTile(pw, count++);
                    int[] gridLoc = { gridx , gridy , level };
                    
                    MetaTile metaTile = new MetaTile(
                            gridBounds, gridLoc, 
                            layer.profile.metaWidth, layer.profile.metaHeight);

                    processTile(metaTile, imageFormat);

                    response.flushBuffer();
                    
                    // Next column
                    gridx += layer.profile.metaWidth;
                }
                // Next row
                gridy += layer.profile.metaHeight;
            }

            log.info("Completed seeding level " + level + " for layer " + layer.name);
            infoLevelStop(pw);
        }

        infoEnd(pw);
        pw.close();
        return 0;
    }

    private int processTile(MetaTile metaTile, ImageFormat imageFormat) {
        int[] metaGridLoc = metaTile.getMetaGridPos();
        layer.waitForQueue(metaGridLoc);
        metaTile.doRequest(layer.profile, imageFormat.getMimeType());
        layer.saveExpirationInformation(metaTile);
        metaTile.createTiles(layer.profile);
        int[][] tilesGridPositions = metaTile.getTilesGridPositions();
        layer.saveTiles(tilesGridPositions, metaTile, imageFormat);

        layer.removeFromQueue(metaGridLoc);
        return 0;
    }

    private void infoStart(PrintWriter pw, int zoomStart, int zoomStop,
            BBOX bounds) throws IOException {
        if (pw == null) {
            return;
        }
        pw.print("<html><body><table><tr><td>Seeding " + layer.name + " from level "
                + zoomStart + " to level " + zoomStop + " for bounds "
                + bounds.getReadableString() + "</td></tr>");
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
        double metaCountX = ((double) tileCountX )/ layer.profile.metaWidth;
        double metaCountY = ((double) tileCountY ) / layer.profile.metaHeight;
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
