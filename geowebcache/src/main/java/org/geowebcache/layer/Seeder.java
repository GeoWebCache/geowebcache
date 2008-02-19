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
	
	public int doSeed(int zoomStart, int zoomStop,  ImageFormat imageFormat, BBOX bounds, HttpServletResponse response) throws IOException {
		//response.setContentType("text/plain");
		
		PrintWriter pw = response.getWriter();
		
		infoStart(pw, zoomStart, zoomStop, bounds);
		
		for(int level=zoomStart; level <= zoomStop; level++) {
			int[] gridBounds = layer.profile.metaGridExtent(level, bounds);
			infoLevelStart(pw, level, gridBounds);
			int count = 0;
			for(int gridy=gridBounds[1]; gridy<gridBounds[3]; gridy += layer.profile.metaHeight) {
				for(int gridx=gridBounds[0]; gridx<gridBounds[2]; gridx += layer.profile.metaWidth) {
					infoTile(pw, count++);
					int[] metaGrid = {gridx,gridy,
							gridx+layer.profile.metaWidth,gridy+layer.profile.metaWidth,level};
					MetaTile metaTile = new MetaTile(layer.profile,metaGrid,true);
					
					processTile(metaTile, imageFormat);
					
					response.flushBuffer();
				}
			}
			
			infoLevelStop(pw);
		}

		infoEnd(pw);
		pw.close();
		
		return 0;
	}
	
	private int processTile(MetaTile metaTile, ImageFormat imageFormat) {
		int[] metaGridLoc = metaTile.getMetaGridPos();
		layer.waitForQueue(metaGridLoc);

		metaTile.doRequest(imageFormat.getMimeType());
		layer.saveExpirationInformation(metaTile);
		metaTile.createTiles();
		int[][] gridPositions = metaTile.getGridPositions();
		layer.saveTiles(gridPositions, metaTile, imageFormat);
		
		layer.removeFromQueue(metaGridLoc);
		return 0;
	}
	
	private void infoStart(PrintWriter pw, int zoomStart, int zoomStop, BBOX bounds) 
	throws IOException {
		if(pw == null)
			return;
		pw.print("<table><tr><td>Seeding "+layer.name
				+" from level "+zoomStart+" to level "+zoomStop
				+" for bounds "+bounds.getReadableString()+"</td></tr>");
		pw.flush();
	}
		
	private void infoEnd(PrintWriter pw) 
	throws IOException {
		if(pw == null)
			return;
		
		pw.print("</table");
	}
	
	private void infoLevelStart(PrintWriter pw, int level, int[] gridBounds) 
	throws IOException {
		if(pw == null)
			return;
		
		int tileCount = (gridBounds[2] - gridBounds[0]) * (gridBounds[3] - gridBounds[1]);
		pw.print("<tr><td>Level "+level+", "+(
				tileCount / (layer.profile.metaHeight*layer.profile.metaWidth)
				)+" metatiles ("+tileCount+" tiles)</td></tr><tr><td>");
		pw.flush();
	}
	
	private void infoLevelStop(PrintWriter pw) 
	throws IOException {
		if(pw == null)
			return;
		
		pw.print("</td></tr>");
	}
	
	private void infoTile(PrintWriter pw, int count) 
	throws IOException {
		if(pw == null)
			return;
		
		//System.out.println("Count: " + count);
		pw.print("" + count +", ");
		pw.flush();
	}
	
}
