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
 * @author Arne Kepp / The Open Planning Project 2008 
 */
package org.geowebcache.seed;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class SeederThreadPoolExecutor extends ThreadPoolExecutor implements DisposableBean {

    private static final Log log = LogFactory.getLog(SeederThreadPoolExecutor.class);

    private static final ThreadFactory tf = new CustomizableThreadFactory("GWC Seeder Thread-");

    public SeederThreadPoolExecutor(int corePoolSize, int maxPoolSize) {
        super(corePoolSize, maxPoolSize, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                tf);
    }

    /**
     * Destroy method called by the application context at shutdown, needed to gracefully shutdown
     * this thread pool executor and any running thread
     * 
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        log.info("Initiating shut down for running and pending seed tasks...");
        this.shutdownNow();
        while (!this.isTerminated()) {
            log.debug("Waiting for pending tasks to terminate....");
            Thread.sleep(500);
        }
        log.info("Seeder thread pool executor shut down complete.");
    }
}
