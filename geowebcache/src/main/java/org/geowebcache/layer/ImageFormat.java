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
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
 *  
 */
package org.geowebcache.layer;

public class ImageFormat {
	protected String mimeType;
	protected String extension;
	protected String javaName;
	
	public ImageFormat(String mimeType, String extension, String javaName) {
		this.mimeType = mimeType;
		this.extension = extension;
		this.javaName = javaName;
	}
	
	public static ImageFormat createFromMimeType(String mimeType) {
		if(mimeType.equalsIgnoreCase("image/png")) {
			return new ImageFormat("image/png",".png","png");
		} else if(mimeType.equalsIgnoreCase("image/jpeg")) {
			return new ImageFormat("image/jpeg",".jpeg","jpeg");
		} else if(mimeType.equalsIgnoreCase("image/gif")) {
			return new ImageFormat("image/gif",".gif","gif");
		} else if(mimeType.equalsIgnoreCase("image/tiff")) {
			return new ImageFormat("image/tiff",".tiff","tiff");
		} else {
			return null;
		}		
	}
	
	public String getMimeType() {
		return this.mimeType;
	}
	
	public String getExtension() {
		return this.extension;
	}
	
	public String getJavaName() {
		return this.javaName;
	}
}
