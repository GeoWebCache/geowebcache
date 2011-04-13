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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool.PoolableObjectFactory;

public class PoolableProcessFactory implements PoolableObjectFactory  {

    private static Log log = LogFactory.getLog(org.geowebcache.util.PoolableProcessFactory.class);
    private String command = null;
	
    public PoolableProcessFactory() {
    }
	public Object makeObject() throws Exception {
		Process p = null;
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf( "win" ) >= 0) {
			command = "cmd";
		} else if (os.indexOf( "nix") >=0 || os.indexOf( "nux") >=0) {
			command = System.getenv("SHELL");
		} else {
			log.error("Operating system " + os + " not supported");
		}
		try {
			p = Runtime.getRuntime().exec(command);
		}
		catch (Exception e) {
			log.error("Error '"+ e.getMessage() + "' while creating process, using command: " + command);
			throw e;
		}
    	return p; 
    }

    public void destroyObject(Object obj) {
    	Process p = (Process) obj;
    	p.destroy();
    }
    
    public boolean validateObject(Object obj) {
    	Process prc = (Process) obj;
		PrintWriter  prcout = new PrintWriter (prc.getOutputStream());
		BufferedReader prcin = new BufferedReader(new InputStreamReader(prc.getInputStream()));
		boolean exceptionOccured = false;
		prcout.println("echo hello");
		prcout.flush();
		long startTime = System.currentTimeMillis();
		try {
			while (!prcin.ready() && (System.currentTimeMillis() - startTime) < 500) {
		    	log.debug("waiting for process to check validity");
				Thread.sleep(100);
			}
		}
		catch (Exception e) {
			exceptionOccured = true;
		}
		
    	if ((System.currentTimeMillis() - startTime) < 500 && !exceptionOccured) {
        	return true;     		
    	} else {
        	return false;    		
    	}
    }
    public void activateObject(Object obj) {}
    public void passivateObject(Object obj) {}
}
