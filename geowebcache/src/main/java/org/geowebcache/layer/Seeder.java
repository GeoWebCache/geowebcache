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
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Seeder {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.Seeder.class);
	TileLayer layer = null;
	
	public Seeder(TileLayer layer) {
		this.layer = layer;
	}
	
	public int doSeed(int zoomStart, int zoomStop,  ImageFormat imageFormat, BBOX bounds, OutputStream os) throws IOException {
		infoStart(os, zoomStart, zoomStop, bounds);
		
		for(int level=zoomStart; level <= zoomStop; level++) {
			int[] gridBounds = layer.profile.metaGridExtent(level, bounds);
			infoLevelStart(os, level, gridBounds);
			int count = 0;
			for(int gridy=gridBounds[1]; gridy<gridBounds[3]; gridy += layer.profile.metaHeight) {
				for(int gridx=gridBounds[0]; gridx<gridBounds[2]; gridx += layer.profile.metaWidth) {
					infoTile(os, count++);
					int[] metaGrid = {gridx,gridy,
							gridx+layer.profile.metaWidth,gridy+layer.profile.metaWidth,level};
					MetaTile metaTile = new MetaTile(layer.profile,metaGrid,true);
					
					processTile(metaTile, imageFormat);
					
				}
			}
			
			infoLevelStop(os);
		}

		infoEnd(os);
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
	
	private void infoStart(OutputStream os, int zoomStart, int zoomStop, BBOX bounds) 
	throws IOException {
		if(os != null)
			return;
		
		os.write(("<table><tr><td>Seeding "+layer.name
				+" from level "+zoomStart+" to level "+zoomStop
				+" for bounds "+bounds.getReadableString()+"</td></tr>").getBytes());
	}
		
	private void infoEnd(OutputStream os) 
	throws IOException {
		if(os != null)
			return;
		
		os.write(("</table").getBytes());
	}
	
	private void infoLevelStart(OutputStream os, int level, int[] gridBounds) 
	throws IOException {
		if(os != null)
			return;
		
		int tileCount = (gridBounds[2] - gridBounds[0]) * (gridBounds[3] - gridBounds[1]);
		os.write(("<tr><td>Level "+level+", "+(
				tileCount / (layer.profile.metaHeight*layer.profile.metaWidth)
				)+" metatiles ("+tileCount+" tiles)</td></tr><tr><td>").getBytes());
	}
	
	private void infoLevelStop(OutputStream os) 
	throws IOException {
		if(os != null)
			return;
		
		os.write(("</td></tr>").getBytes());
	}
	
	private void infoTile(OutputStream os, int count) 
	throws IOException {
		if(os != null)
			return;
		
		os.write(("" + count +", ").getBytes());
	}
	
}
