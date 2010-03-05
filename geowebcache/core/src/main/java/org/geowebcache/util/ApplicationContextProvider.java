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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 * 
 * How can this be necessary...
 */

package org.geowebcache.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;

public class ApplicationContextProvider implements ApplicationContextAware {

    private static Log log = LogFactory.getLog(org.geowebcache.util.ApplicationContextProvider.class);
    
    WebApplicationContext ctx;
    
    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        ctx = (WebApplicationContext) arg0;
    }
    
    public WebApplicationContext getApplicationContext() {
        return ctx;
    }

    
    public String getSystemVar(String varName, String defaultValue) {
        if(ctx == null) {
            String msg = "Application context was not set yet! Damn you Spring Framework :( ";
            log.error(msg);
            throw new RuntimeException(msg);
        }
        
        String tmpVar = ctx.getServletContext().getInitParameter(varName);
        if(tmpVar != null && tmpVar.length() > 7) {
            log.info("Using servlet init context parameter to configure "+varName+" to "+tmpVar);
            return tmpVar;
        }
        
        tmpVar = System.getProperty(varName);
        if(tmpVar != null && tmpVar.length() > 7) {
            log.info("Using Java environment variable to configure "+varName+" to "+tmpVar);
            return tmpVar;
        }
        
        tmpVar = System.getenv(varName);
        if(tmpVar != null && tmpVar.length() > 7) {
            log.info("Using System environment variable to configure "+varName+" to "+tmpVar);
            return tmpVar;
        }
        
        //tmpVar = ;
        log.info("No context parameter, system or Java environment variables found for " + varName);
        log.info("Reverting to " + defaultValue );
        
        return defaultValue;
    }
}
