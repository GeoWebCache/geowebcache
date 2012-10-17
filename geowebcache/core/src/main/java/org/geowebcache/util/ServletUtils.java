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
package org.geowebcache.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServletUtils {
    private static Log log = LogFactory.getLog(org.geowebcache.util.ServletUtils.class);

    // Calendar objects are unfortunately expensive and not thread safe :(
    static private Calendar calendar = new GregorianCalendar();

    static private TimeZone timeZone = TimeZone.getTimeZone("GMT");

    static private SimpleDateFormat format = null;

    static private long localOffset = TimeZone.getDefault().getRawOffset();

    /**
     * Case insensitive lookup
     * 
     * @param map
     * @param key
     * @return all matchings string
     */
    public static String[] stringsFromMap(Map<String, String[]> map, String encoding, String key) {
        String[] strArray = (String[]) map.get(key);

        if (strArray != null) {
            return strArray;
        } else {
            // In case there is a case mismatch
            Iterator<Entry<String, String[]>> iter = map.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<String, String[]> entry = iter.next();
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return URLDecode(entry.getValue(), encoding);
                }
            }
        }
        return null;
    }

    /**
     * Case insensitive lookup
     * 
     * @param map
     * @param key
     * @return
     */
    public static String stringFromMap(Map<String, String[]> map, String encoding, String key) {
        String[] strArray = stringsFromMap(map, encoding, key);
        if (strArray != null) {
            return strArray[0];
        }
        return null;

    }

    /**
     * Case insensitive lookup for a couple of strings, drops everything else
     * 
     * @param map
     * @param keys
     * @return
     */
    public static String[][] selectedStringArraysFromMap(Map<String, String[]> map,
            String encoding, String[] keys) {
        String[][] retAr = new String[keys.length][];

        Iterator<Entry<String, String[]>> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, String[]> entry = iter.next();
            String key = entry.getKey();

            for (int i = 0; i < keys.length; i++) {
                if (key.equalsIgnoreCase(keys[i])) {
                    retAr[i] = URLDecode(entry.getValue(), encoding);
                    continue;
                }
            }
        }

        return retAr;
    }

    /**
     * Case insensitive lookup for a couple of strings, drops everything else
     * 
     * @param map
     * @param keys
     * @return map subset containing (URL decoded) values for {@code keys}, with keys normalized to
     *         upper case
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> selectedStringsFromMap(Map<String, ?> map, String encoding,
            String... keys) {

        map = new CaseInsensitiveMap(map);
        Map<String, String> selected = new CaseInsensitiveMap();
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                String sValue = value instanceof String[] ? ((String[]) value)[0] : String
                        .valueOf(value);
                selected.put(key.toUpperCase(), URLDecode(sValue, encoding));
            }
        }
        return selected;
    }

    /**
     * Extracts the cache control header
     * 
     * @param cacheControlHeader
     * @return Long representing expiration time in seconds
     */
    // public static Long extractHeaderMaxAge(URLConnection backendCon) {
    //
    // String cacheControlHeader = backendCon.getHeaderField("Cache-Control");
    //
    // if (cacheControlHeader == null) {
    // return null;
    // }
    //
    // String expression = "max-age=([0-9]*)[ ,]";
    // Pattern p = Pattern.compile(expression);
    // Matcher m = p.matcher(cacheControlHeader.toLowerCase());
    //
    // if (m.find()) {
    // return Long.valueOf(m.group(1));
    // } else {
    // return null;
    // }
    // }

    /**
     * Reads an inputstream and stores all the information in a buffer.
     * 
     * @param is
     *            the inputstream
     * @param bufferHint
     *            hint for the total buffer, -1 = 10240
     * @param tmpBufferSize
     *            how many bytes to read at a time, -1 = 1024
     * 
     * @return a compacted buffer with all the data
     * @throws IOException
     */
    public static byte[] readStream(InputStream is, int bufferHint, int tmpBufferSize)
            throws IOException {
        return readStream(is, bufferHint, tmpBufferSize, true);
    }

    public static byte[] readStream(InputStream is, int bufferHint, int tmpBufferSize, boolean close)
            throws IOException {
        byte[] buffer = null;
        if (bufferHint > 0) {
            buffer = new byte[bufferHint];
        } else {
            buffer = new byte[10240];
        }

        byte[] tmpBuffer = null;
        if (tmpBufferSize > 0) {
            tmpBuffer = new byte[tmpBufferSize];
        } else {
            tmpBuffer = new byte[1024];
        }

        int totalCount = 0;
        int c = is.read(tmpBuffer);
        while (c != -1) {
            if (c != 0) {
                totalCount += c;

                // Expand buffer if needed
                if (totalCount >= buffer.length) {
                    int newLength = buffer.length * 2;
                    if (newLength < totalCount)
                        newLength = totalCount;

                    byte[] newBuffer = new byte[newLength];
                    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                    buffer = newBuffer;
                }

                System.arraycopy(tmpBuffer, 0, buffer, (totalCount - c), c);
            }
            c = is.read(tmpBuffer);
        }

        if (close)
            is.close();

        // Compact buffer
        byte[] newBuffer = new byte[totalCount];
        System.arraycopy(buffer, 0, newBuffer, 0, totalCount);

        return newBuffer;
    }

    /**
     * Makes HTTP Expire header value
     * 
     * Has to be synchronized due to the shared Calendar objects
     * 
     * @param seconds
     * @return
     */
    public static String makeExpiresHeader(int seconds) {
        return formatTimestamp(System.currentTimeMillis() + seconds * 1000L);
    }

    public static String formatTimestamp(long timestamp) {
        String ret;
        synchronized (calendar) {
            if (ServletUtils.format == null) {
                ServletUtils.format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                ServletUtils.format.setTimeZone(ServletUtils.timeZone);
            }

            calendar.setTimeInMillis(timestamp);
            ret = format.format(calendar.getTime());
        }
        return ret;
    }

    /**
     * Returns the expiration time in milliseconds from now
     * 
     * @param expiresHeader
     * @return
     */
    public static long parseExpiresHeader(String expiresHeader) {
        if (expiresHeader == null) {
            return -1;
        }

        long ret;

        synchronized (calendar) {
            if (ServletUtils.format == null) {
                ServletUtils.format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                ServletUtils.format.setTimeZone(ServletUtils.timeZone);

            }

            try {
                format.parse(expiresHeader);
            } catch (ParseException pe) {
                log.debug("Cannot parse " + expiresHeader + ", " + pe.getMessage());
                return -1;
            }

            ret = calendar.getTimeInMillis() - System.currentTimeMillis() - localOffset;
        }
        return ret;
    }

    public static String hexOfBytes(byte[] bytes) {
        StringBuilder str = new StringBuilder(bytes.length * 2);

        for (int i = 0; i < bytes.length; i++) {
            str.append(hexOfByte(bytes[i]));
        }

        return str.toString();
    }

    /**
     * Converts a byte to a hex String
     * 
     * @param aByte
     * @return
     */
    public static String hexOfByte(byte aByte) {
        char[] str = new char[2];

        for (int i = 0; i < 2; i++) {
            int temp = (int) aByte;
            if (temp < 0) {
                temp += 256;
            }
            if (i == 0) {
                temp = temp / 16;
            } else {
                temp = temp % 16;
            }

            if (temp > 9) {
                switch (temp) {
                case 10:
                    str[i] = 'A';
                    break;
                case 11:
                    str[i] = 'B';
                    break;
                case 12:
                    str[i] = 'C';
                    break;
                case 13:
                    str[i] = 'D';
                    break;
                case 14:
                    str[i] = 'E';
                    break;
                case 15:
                    str[i] = 'F';
                    break;
                }
            } else {
                str[i] = (char) temp;
            }
        }
        return new String(str);
    }

    public static String URLEncode(String str) {
        String ret = null;

        try {
            ret = URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.debug(e.getMessage());
        }

        return ret;
    }

    public static String URLDecode(String str, String encoding) {
        String ret = null;

        if (encoding != null) {
            try {
                ret = URLDecoder.decode(str, encoding);
            } catch (UnsupportedEncodingException e) {
                log.debug(e.getMessage());
            }
        }

        try {
            ret = URLDecoder.decode(str, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            log.debug(e1.getMessage());
        }

        return ret;
    }

    private static String[] URLDecode(String[] values, String encoding) {
        String[] decodedValues = new String[values.length];

        for (int i = 0; i < values.length; i++) {
            decodedValues[i] = URLDecode(values[i], encoding);
        }

        return decodedValues;
    }

    public static String gwcHtmlHeader(String pageTitle) {
        return "<head>\n" + "<title>" + pageTitle + "</title>" + "<style type=\"text/css\">\n"
                + "body, td {\n"
                + "font-family: Verdana,Arial,\'Bitstream Vera Sans\',Helvetica,sans-serif;\n"
                + "font-size: 0.85em;\n" + "vertical-align: top;\n" + "}\n" + "</style>\n"
                + "</head>\n";
    }

    public static String gwcHtmlLogoLink(String relBasePath) {
        return "<a id=\"logo\" href=\"" + relBasePath + "\">" + "<img src=\"" + relBasePath
                + "rest/web/geowebcache_logo.png\"" + "height=\"70\" width=\"247\" border=\"0\"/>"
                + "</a>\n";
    }

    public static long[][] arrayDeepCopy(long[][] array) {
        long[][] ret = new long[array.length][array[0].length];
        for (int i = 0; i < array.length; i++) {
            System.arraycopy(array[i], 0, ret[i], 0, array[i].length);
        }

        return ret;
    }

    /**
     * Replaces occurrences of &gt; and &lt; with HTML equivalents
     * 
     * @param str
     * @return
     */

    public static String disableHTMLTags(String str) {
        if (str == null) {
            return "null";
        }

        return str.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }

    public static Map<String, String> queryStringToMap(String queryString) {
        if (queryString == null || queryString.length() == 0) {
            return Collections.emptyMap();
        }
        String[] params = queryString.split("&");
        Map<String, String> ret = new HashMap<String, String>();
        for (String kvp : params) {
            String[] split = kvp.split("=");
            if (split[0].length() > 0) {
                ret.put(split[0], split[1]);
            }
        }
        return ret;
    }

    /**
     * Generate the base url of the request, minus the context path
     * @param req servlet request
     * @return Base url of request, minus the context path
     */
    public static String getServletBaseURL(HttpServletRequest req) {
        if (req.getServerPort() == 80 || req.getServerPort() == 443) {
            return req.getScheme() + "://" + req.getServerName();
        } else {
            return req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort();
        }
    }
}
