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
package org.geowebcache.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.util.wms.BBOX;

public abstract class Parameters {

    private static Log log = LogFactory
            .getLog(org.geowebcache.service.Parameters.class);

    // Charset to use for URL strings
    protected static final String CHARSET = "UTF-8";

    protected Map params;

    public Parameters() {
        params = new HashMap();
    }

    public Parameters(HttpServletRequest httprequest) {
        params = new HashMap();
        setFromHttpServletRequest(httprequest);
    }

    // TODO What does this really achieve? I know there was something...
    public void setFromHttpServletRequest(HttpServletRequest httprequest) {
        if (log.isTraceEnabled()) {
            log.trace("Setting from HttpServletRequest.");
        }

        // this.params.putAll() won't work since we want all keys to be in lowercase
        Map param_map = httprequest.getParameterMap();
        Iterator itr = param_map.keySet().iterator();
        String mixedstr;

        while (itr.hasNext()) {
            mixedstr = (String) itr.next();
            set(mixedstr, param_map.get(mixedstr));
        }

        //if (log.isTraceEnabled()) {
        //    log.trace("Results from setting from HttpServletRequest: " + getReadableString());
        //}
    }

    /**
     * Allows arbitary key / values to be set
     */
    @SuppressWarnings("unchecked")
    public void set(String key, Object value) {
        params.put(key.toLowerCase(), value);
    }

    public Object get(String key) {
        return params.get(key.toLowerCase());
    }

    public void remove(String key) {
        params.remove(key.toLowerCase());
    }

    /**
     * Converts the map object to a proper URL string (such as by turning string
     * arrays in comma separated values) Assumes that the object in the map
     * implements a correct toString()
     * 
     * @param obj
     * @return
     */
    protected String convertToString(Object obj) {
        if (obj != null) {
            if (obj instanceof String) {
                return (String) obj;
            } else if (obj instanceof Boolean) {
                // Tweak for Ionic
                return ((Boolean) obj).toString().toUpperCase();
            } else if (obj instanceof String[]) {
                // Make a comma separated list out of the array
                String[] array = (String[]) obj;
                StringBuffer str = new StringBuffer(100);
                boolean notfirst = false;
                for (int i = 0; i < array.length; ++i) {
                    if (notfirst) {
                        str.append(',');
                    } else {
                        notfirst = true;
                    }
                    str.append(array[i]);
                }
                return str.toString();
            } else {
                // Assume this class implements toString
                return obj.toString();
            }
        }
        // If object is null, return null
        return null;
    }

    /**
     * Merges another Paramters object with this one if the old value is unset,
     * it is set to the new value if the old value is set and the new value is
     * not null, the new value is used
     */
    @SuppressWarnings("unchecked")
    public void merge(Parameters params) {
        this.params.putAll(params.params);
    }

    /**
     * Outputs an HTTP parameter string
     */
    public StringBuffer getURLString(boolean prefixAmpersand) {
        StringBuffer arg_str = new StringBuffer(256);
        String param_name;

        Iterator itr = params.keySet().iterator();
        while (itr.hasNext()) {
            param_name = (String) itr.next();
            if (param_name != null && param_name.length() > 0) {
                if (arg_str.length() > 0) {
                    arg_str.append('&');
                } else {
                    if (prefixAmpersand) {
                        arg_str.append('&');
                    }
                }

                try {
                    arg_str.append(URLEncoder.encode(param_name, CHARSET));
                    arg_str.append('=');
                    arg_str.append(URLEncoder.encode(
                            convertToString(get(param_name)), CHARSET));

                } catch (UnsupportedEncodingException uee) {
                    log.fatal("Unsupported URL Encoding: ", uee);
                    return null;
                } catch (NullPointerException npe) {
                    System.out.println("Missing value for parameter: "
                            + param_name);
                    log.error("Missing value for parameter: " + param_name);
                }
            }
        }
        return arg_str;
    }

    /**
     * Returns a URL string. If a StringBuffer is desired instead, use
     * getURLString()
     */
    //@Override
    //public String toString() {
    //    return getURLString(true).toString();
    //}

    /**
     * Outputs an easily readable parameter / value listing
     */
//    public String getReadableString() {
//        String key;
//        StringBuffer buf = new StringBuffer();
//
//        Iterator itr = params.keySet().iterator();
//        while (itr.hasNext()) {
//            key = (String) itr.next();
//            if (key != null && key.length() > 0) {
//                Object value = params.get(key);
//                if (value != null) {
//                    buf.append(key + '=' + convertToString(value) + ' ');
//                } else {
//                    buf.append(key + "=null ");
//                }
//            }
//        }
//        return buf.toString();
//    }

    /**
     * Service parameter classes will define how to get the layer name from the
     * map
     * 
     * @return
     */
    public abstract String getLayer();

    /**
     * Service parameter classes will define how to get the image format from
     * the map
     * 
     * @return
     */
    //public abstract ImageFormat getImageFormat();
    /**
     * Service parameter classes will define how to get the BBOX from the map
     * 
     * @return
     */
    public abstract BBOX getBBOX();

    /**
     * Compares if the given parameter set is on the same "Layer"
     * 
     * @param params
     * @return
     */
     //public abstract boolean sameLayerAs(Parameters params);
}
