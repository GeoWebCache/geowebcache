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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 *     <p>How can this be necessary...
 */
package org.geowebcache.util;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;

public class ApplicationContextProvider implements ApplicationContextAware {

    private static Logger log = Logging.getLogger(ApplicationContextProvider.class.toString());

    WebApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext arg0) throws BeansException {
        ctx = (WebApplicationContext) arg0;
    }

    public WebApplicationContext getApplicationContext() {
        return ctx;
    }

    public String getSystemVar(String varName, String defaultValue) {
        if (ctx == null) {
            String msg = "Application context was not set yet! Damn you Spring Framework :( ";
            log.log(Level.SEVERE, msg);
            throw new RuntimeException(msg);
        }

        String tmpVar =
                Optional.ofNullable(ctx.getServletContext())
                        .map(sc -> sc.getInitParameter(varName))
                        .orElse(null);
        if (tmpVar != null && tmpVar.length() > 7) {
            log.info(
                    "Using servlet init context parameter to configure "
                            + varName
                            + " to "
                            + tmpVar);
            return tmpVar;
        }

        tmpVar = System.getProperty(varName);
        if (tmpVar != null && tmpVar.length() > 7) {
            log.info("Using Java environment variable to configure " + varName + " to " + tmpVar);
            return tmpVar;
        }

        tmpVar = System.getenv(varName);
        if (tmpVar != null && tmpVar.length() > 7) {
            log.info("Using System environment variable to configure " + varName + " to " + tmpVar);
            return tmpVar;
        }

        // tmpVar = ;
        log.info("No context parameter, system or Java environment variables found for " + varName);
        log.info("Reverting to " + defaultValue);

        return defaultValue;
    }
}
