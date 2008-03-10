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
 * @author Chris Whitney
 *  
 */
package org.geowebcache.service.wms;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.BBOX;
import org.geowebcache.mime.ImageMimeType;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Parameters;


@SuppressWarnings("unchecked")
public class WMSParameters extends Parameters {
	private static Log log = LogFactory.getLog(org.geowebcache.service.wms.WMSParameters.class);

	// These constants should be in lower case
	public static final String REQUEST_PARAM = "request";
	public static final String VERSION_PARAM = "version";
	public static final String TILED_PARAM = "tiled";
	public static final String TRANSPARENT_PARAM = "transparent";
	public static final String BGCOLOR_PARAM = "bgcolor";
	public static final String PALETTE_PARAM = "palette";
	public static final String SRS_PARAM = "srs";
	public static final String LAYER_PARAM = "layers";
	public static final String STYLES_PARAM = "styles";
	public static final String BBOX_PARAM = "bbox";
	public static final String ORIGIN_PARAM = "tilesorigin";
	public static final String HEIGHT_PARAM = "height";
	public static final String WIDTH_PARAM = "width";
	public static final String IMAGE_TYPE_PARAM = "format";
	public static final String ERROR_TYPE_PARAM = "exceptions";

	public WMSParameters() {
	}

	public WMSParameters(HttpServletRequest httprequest) {
		super(httprequest);
	}

	/**
	 * @return the bbox
	 */
	@Override
	public BBOX getBBOX() {
		Object obj = get(BBOX_PARAM);
		if(obj == null) {
			return null;
		}
		BBOX box = null;
		if(obj.getClass().equals(BBOX.class)) {
			box = (BBOX)obj;
		} else if (obj.getClass().equals(String[].class)) {
			box =  new BBOX((String[])obj);
			setBBOX(box);
		} else if (obj.getClass().equals(String.class)) {
			box = new BBOX((String)obj);
			setBBOX(box);
		}
		return box;
	}

	/**
	 * @param bbox the bbox to set
	 */
	public void setBBOX(BBOX bbox) {
		set(BBOX_PARAM, bbox);
	}

	/**
	 * @param bbox the bbox to set
	 */
	public void setBBOX(String bbox) {
		set(BBOX_PARAM, new BBOX(bbox));
	}


	public String getStyles() {
		return convertToString(get(STYLES_PARAM));
	}

	public void setStyles(String styles) {
		set(STYLES_PARAM, styles);
	}

	public String getRequest() {
		return convertToString(get(REQUEST_PARAM));
	}

	public void setRequest(String request) {
		set(REQUEST_PARAM, request);
	}

	public String getVersion() {
		return convertToString(get(VERSION_PARAM));
	}

	public void setVersion(String version) {
		set(VERSION_PARAM, version);
	}

	public Boolean getIsTiled() {
		Object obj = get(TILED_PARAM);
		if(obj == null) {
			return null;
		}
		if(obj.getClass().equals(Boolean.class)) {
			return (Boolean)obj;
		} else {
			String str = convertToString(obj);
			return Boolean.valueOf(str);
		}
	}
	
	public void setIsTiled(Boolean tiled) {
		set(TILED_PARAM, tiled);
	}

	public void setIsTiled(String tiled) {
		set(TILED_PARAM, Boolean.valueOf(tiled));
	}

	public Boolean getIsTransparent() {
		Object obj = get(TRANSPARENT_PARAM);
		if(obj == null) {
			return null;
		}
		if(obj.getClass().equals(Boolean.class)) {
			return (Boolean)obj;
		} else {
			String str = convertToString(obj);
			return Boolean.valueOf(str);
		}
	}
	
	public void setIsTransparent(Boolean transparent) {
		set(TRANSPARENT_PARAM, transparent);
	}

	public void setIsTransparent(String transparent) {
		set(TRANSPARENT_PARAM, Boolean.valueOf(transparent));
	}

	public String getPalette() {
		return convertToString(get(PALETTE_PARAM));
	}
	
	public void setPalette(String palette) {
		set(PALETTE_PARAM, palette);
	}
	
	public String getBgColor() {
		return convertToString(get(BGCOLOR_PARAM));
	}

	public void setBgColor(String bgcolor) {
		set(BGCOLOR_PARAM, bgcolor);
	}
	
	/**
	 * @return the errormime
	 */
	public MimeType getErrormime() {
		Object obj = get(ERROR_TYPE_PARAM);
		if(obj == null) {
			return null;
		}
		MimeType mime = null;
		if(obj.getClass().equals(MimeType.class)) {
			mime = (MimeType)obj;
		} else {
			try {
				mime = new MimeType(convertToString(obj));
				setErrormime(mime);
			} catch(IOException ioe) {
				log.error("Error creating error mime type", ioe);
			}
		}
		return mime;
	}

	/**
	 * @param errormime the errormime to set
	 */
	public void setErrormime(MimeType errormime) {
		set(ERROR_TYPE_PARAM, errormime);
	}

	/**
	 * @param errormime the errormime to set
	 * @throws IOException
	 */
	public void setErrormime(String errormime) throws IOException {
		 setErrormime(new MimeType(errormime));
	}

	/**
	 * @return the imagemime
	 */
	public ImageMimeType getImagemime() {
		Object obj = get(IMAGE_TYPE_PARAM);
		if(obj == null) {
			return null;
		}
		ImageMimeType mime = null;
		if(obj.getClass().equals(ImageMimeType.class)) {
			mime = (ImageMimeType)obj;
		} else {
			try {
				mime = new ImageMimeType(convertToString(obj));
				setImagemime(mime);
			} catch(IOException ioe) {
				log.error("Error creating image mime type", ioe);
			}
		}
		return mime;
	}

	/**
	 * @param imagemime the imagemime to set
	 */
	public void setImagemime(ImageMimeType imagemime) {
		set(IMAGE_TYPE_PARAM, imagemime);
	}

	/**
	 * @param errormime the errormime to set
	 * @throws IOException
	 */
	public void setImagemime(String imagemime) throws IOException {
		setImagemime(new ImageMimeType(imagemime));
	}

	/**
	 * @return the layer
	 */
	@Override
	public String getLayer() {
		return convertToString(get(LAYER_PARAM));
	}

	/**
	 * @param layer the layer to set
	 */
	public void setLayer(String layer) {
		set(LAYER_PARAM, layer);
	}

	/**
	 * @return the height
	 */
	public Integer getHeight() {
		Object obj = get(HEIGHT_PARAM);
		if(obj == null) {
			return null;
		}
		Integer height = null;
		if(obj.getClass().equals(Integer.class)) {
			height = (Integer)obj;
		} else {
			height = Integer.valueOf(convertToString(obj));
			setHeight(height);
		}
		return height;
	}

	/**
	 * @param height the height to set
	 */
	public void setHeight(Integer height) {
		set(HEIGHT_PARAM, height);
	}

	/**
	 * @param height the height to set
	 */
	public void setHeight(int height) {
		setHeight(new Integer(height));
	}

	/**
	 * @param height the height to set
	 */
	public void setHeight(String height) {
		setHeight(Integer.parseInt(height));
	}

	/**
	 * @return the origin
	 */
	public String getOrigin() {
		return convertToString(get(ORIGIN_PARAM));
	}

	/**
	 * @param origin the origin to set
	 */
	public void setOrigin(String origin) {
		set(ORIGIN_PARAM, origin);
	}

	/**
	 * @return the srs
	 */
	public String getSrs() {
		return convertToString(get(SRS_PARAM));
	}

	/**
	 * @param srs the srs to set
	 */
	public void setSrs(String srs) {
		set(SRS_PARAM, srs);
	}

	/**
	 * @return the width
	 */
	public Integer getWidth() {
		Object obj = get(WIDTH_PARAM);
		if(obj == null) {
			return null;
		}
		Integer width = null;
		if(obj.getClass().equals(Integer.class)) {
			width = (Integer)obj;
		} else {
			width = Integer.valueOf(convertToString(obj));
			setWidth(width);
		}
		return width;
	}

	/**
	 * @param width the width to set
	 */
	public void setWidth(Integer width) {
		set(WIDTH_PARAM, width);
	}

	/**
	 * @param width the width to set
	 */
	public void setWidth(int width) {
		setWidth(new Integer(width));
	}

	/**
	 * @param width the width to set
	 */
	public void setWidth(String width) {
		setWidth(Integer.parseInt(width));
	}

	/**
	 * Sets all properties at once
	 * @param srs
	 * @param layer
	 * @param bbox
	 * @param origin
	 * @param height
	 * @param width
	 * @param imagemime
	 * @param errormime
	 */
	public void setAllFromString(String request, String version, String istiled, String srs, String layer, String styles, String bbox, String origin, String height, String width,
			String imagemime, String errormime) {
		if(request != null) {
			setRequest(request);
		}
		if(version != null) {
			setVersion(version);
		}
		if(istiled != null) {
			this.setIsTiled(istiled);
		}
		if(srs != null) {
			setSrs(srs);
		}
		if(layer != null) {
			setLayer(layer);
		}
		if(styles != null) {
			setStyles(styles);
		}
		if(bbox != null) {
			this.setBBOX(bbox);
		}
		if(origin != null) {
			setOrigin(origin);
		}
		if(height != null) {
			this.setHeight(height);
		}
		if(width != null) {
			this.setWidth(width);
		}
		if(imagemime != null) {
			try {
				this.setImagemime(imagemime);
			} catch(IOException ioe) {
				log.error("Error setting image format: ", ioe);
			}
		}
		if(errormime != null) {
			try {
				this.setErrormime(errormime);
			} catch(IOException ioe) {
				log.error("Error setting exception format: ", ioe);			}
		}
	}
}

