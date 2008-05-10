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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.service.Parameters;
import org.geowebcache.util.wms.BBOX;

@SuppressWarnings("unchecked")
public class WMSParameters extends Parameters {
    private static Log log = LogFactory
            .getLog(org.geowebcache.service.wms.WMSParameters.class);

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

    public static final String FORMAT_PARAM = "format";

    public static final String ERROR_TYPE_PARAM = "exceptions";
    
    public static final String VENDOR_PARAMS = "vendorparameters";

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
        if (obj == null) {
            return null;
        }
        BBOX box = null;
        if (obj.getClass().equals(BBOX.class)) {
            box = (BBOX) obj;
        } else if (obj.getClass().equals(String[].class)) {
            box = new BBOX((String[]) obj);
            setBBOX(box);
        } else if (obj.getClass().equals(String.class)) {
            box = new BBOX((String) obj);
            setBBOX(box);
        }
        return box;
    }

    /**
     * @param bbox
     *            the bbox to set
     */
    public void setBBOX(BBOX bbox) {
        set(BBOX_PARAM, bbox);
    }

    /**
     * @param bbox
     *            the bbox to set
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

    public boolean getIsTiled() {
        Object obj = get(TILED_PARAM);
        if (obj == null) {
            return false;
        }
        if (obj.getClass().equals(Boolean.class)) {
            return (Boolean) obj;
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

    public boolean getIsTransparent() {
        Object obj = get(TRANSPARENT_PARAM);
        if (obj == null) {
            return false;
        }
        if (obj.getClass().equals(Boolean.class)) {
            return (Boolean) obj;
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
    public String getErrormime() {
        return convertToString(get(ERROR_TYPE_PARAM));
    }


    /**
     * @param errormime
     *            the errormime to set
     * @throws IOException
     */
    public void setErrorMime(String errorMime) {
        set(ERROR_TYPE_PARAM, errorMime);
    }

    /**
     * @return the imagemime
     */
    public String getFormat() {
        return convertToString(get(FORMAT_PARAM));
    }

    /**
     * @param errormime
     *            the errormime to set
     * @throws IOException
     */
    public void setFormat(String mime) {
        set(FORMAT_PARAM, mime);
    }

    /**
     * @return the layer
     */
    @Override
    public String getLayer() {
        return convertToString(get(LAYER_PARAM));
    }

    /**
     * @param layer
     *            the layer to set
     */
    public void setLayer(String layer) {
        set(LAYER_PARAM, layer);
    }

    /**
     * @return the height
     */
    public Integer getHeight() {
        Object obj = get(HEIGHT_PARAM);
        if (obj == null) {
            return null;
        }
        Integer height = null;
        if (obj.getClass().equals(Integer.class)) {
            height = (Integer) obj;
        } else {
            height = Integer.valueOf(convertToString(obj));
            setHeight(height);
        }
        return height;
    }

    /**
     * @param height
     *            the height to set
     */
    public void setHeight(Integer height) {
        set(HEIGHT_PARAM, height);
    }

    /**
     * @param height
     *            the height to set
     */
    public void setHeight(int height) {
        setHeight(Integer.valueOf(height));
    }

    /**
     * @param height
     *            the height to set
     */
    public void setHeight(String height) {
        setHeight(Integer.valueOf(height));
    }

    /**
     * @return the origin
     */
    public String getOrigin() {
        return convertToString(get(ORIGIN_PARAM));
    }

    /**
     * @param origin
     *            the origin to set
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
     * @param srs
     *            the srs to set
     */
    public void setSrs(String srs) {
        set(SRS_PARAM, srs);
    }

    /**
     * @return the width
     */
    public Integer getWidth() {
        Object obj = get(WIDTH_PARAM);
        if (obj == null) {
            return null;
        }
        Integer width = null;
        if (obj.getClass().equals(Integer.class)) {
            width = (Integer) obj;
        } else {
            width = Integer.valueOf(convertToString(obj));
            setWidth(width);
        }
        return width;
    }

    /**
     * @param width
     *            the width to set
     */
    public void setWidth(Integer width) {
        set(WIDTH_PARAM, width);
    }

    /**
     * @param width
     *            the width to set
     */
    public void setWidth(int width) {
        setWidth(Integer.valueOf(width));
    }

    /**
     * @param width
     *            the width to set
     */
    public void setWidth(String width) {
        setWidth(Integer.valueOf(width));
    }
    
    public String getVendorParams() {
        return convertToString(get(VENDOR_PARAMS));
    }

    public void setVendorParams(String request) {
        set(VENDOR_PARAMS, request);
    }

    /**
     * Outputs an HTTP parameter string
     * 
     * This is identical to Parameters.getURLString() but makes an exception
     * for VENDOR_PARAMS
     * 
     */
    public StringBuffer getURLString() {
        StringBuffer arg_str = new StringBuffer(256);
        String param_name;
 
        Iterator itr = super.params.keySet().iterator();
        while (itr.hasNext()) {
            param_name = (String) itr.next();
            if (param_name != null && param_name.length() > 0) {
                if (arg_str == null || arg_str.length() == 0) {
                    arg_str.append('?');
                } else {
                    arg_str.append('&');
                }

                try {
                	if(param_name.equalsIgnoreCase(VENDOR_PARAMS)) {
                		arg_str.append(get(VENDOR_PARAMS));
                	} else {
                		arg_str.append(URLEncoder.encode(param_name, CHARSET));
                		arg_str.append('=');
                		arg_str.append(URLEncoder.encode(
                				convertToString(get(param_name)), CHARSET));
                	}

                } catch (UnsupportedEncodingException uee) {
                    log.fatal("Unsupported URL Encoding: ", uee);
                    return null;
                } catch (NullPointerException npe) {
                    log.error("Missing value for parameter: " + param_name);
                }
            }
        }
        return arg_str;
    }
}
