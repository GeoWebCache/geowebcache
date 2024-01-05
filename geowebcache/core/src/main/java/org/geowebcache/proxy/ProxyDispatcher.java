/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.proxy;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geotools.util.logging.Logging;
import org.geowebcache.util.URLs;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

public class ProxyDispatcher extends AbstractController {
    private static Logger log = Logging.getLogger(ProxyDispatcher.class.getName());

    private static long lastRequest = System.currentTimeMillis();

    @Override
    protected ModelAndView handleRequestInternal(
            HttpServletRequest request, HttpServletResponse response) throws Exception {

        String methStr = request.getMethod();
        if (!methStr.equalsIgnoreCase("POST")) {
            throw new ServletException("Illegal method " + methStr + " for this proxy.");
        }

        String urlStr = request.getParameter("url");
        if (urlStr == null || urlStr.length() == 0 || !urlStr.startsWith("http://")) {
            throw new ServletException("Expected url parameter.");
        }

        // lastRequest is static, static synchronization needed
        synchronized (ProxyDispatcher.class) {
            long time = System.currentTimeMillis();
            if (time - lastRequest < 1000) {
                throw new ServletException("Only one request per second please.");
            } else {
                lastRequest = time;
            }
        }

        log.info("Proxying request for " + request.getRemoteAddr() + " to " + " " + urlStr);

        String charEnc = request.getCharacterEncoding();
        if (charEnc == null) {
            charEnc = "UTF-8";
        }
        String decodedUrl = URLDecoder.decode(urlStr, charEnc);

        URL url = URLs.of(decodedUrl);
        HttpURLConnection wmsBackendCon = (HttpURLConnection) url.openConnection();

        if (wmsBackendCon.getContentEncoding() != null) {
            response.setCharacterEncoding(wmsBackendCon.getContentEncoding());
        }

        response.setContentType(wmsBackendCon.getContentType());

        int read = 0;
        byte[] data = new byte[1024];
        while (read > -1) {
            read = wmsBackendCon.getInputStream().read(data);
            if (read > -1) {
                response.getOutputStream().write(data, 0, read);
            }
        }

        return null;
    }
}
