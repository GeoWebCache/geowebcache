/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp / The Open Planning Project 2008
 */
package org.geowebcache.seed;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class SeederThreadPoolExecutor extends ThreadPoolExecutor implements DisposableBean {

    private static final Logger log = Logging.getLogger(SeederThreadPoolExecutor.class.getName());

    private static final ThreadFactory tf = new CustomizableThreadFactory("GWC Seeder Thread-");

    /**
     * Environment variable / system property name for configuring the core pool size. Looked up from Java system
     * properties first, then from OS environment variables. If neither is set or the value is not a valid positive
     * integer, the {@code corePoolSize} constructor argument is used as the default.
     */
    public static final String GWC_SEEDER_CORE_POOL_SIZE = "GWC_SEEDER_CORE_POOL_SIZE";

    /**
     * Environment variable / system property name for configuring the maximum pool size. Looked up from Java system
     * properties first, then from OS environment variables. If neither is set or the value is not a valid positive
     * integer, the {@code maxPoolSize} constructor argument is used as the default.
     */
    public static final String GWC_SEEDER_MAX_POOL_SIZE = "GWC_SEEDER_MAX_POOL_SIZE";

    public SeederThreadPoolExecutor(int corePoolSize, int maxPoolSize) {
        this(resolveAndValidateSizes(corePoolSize, maxPoolSize));
    }

    private SeederThreadPoolExecutor(int[] sizes) {
        super(sizes[0], sizes[1], 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), tf);
        log.info("Seeder thread pool initialized with corePoolSize="
                + getCorePoolSize()
                + ", maxPoolSize="
                + getMaximumPoolSize());
    }

    /**
     * Resolves both pool sizes once and validates the core <= max constraint. Returns a two-element array [core, max].
     */
    private static int[] resolveAndValidateSizes(int defaultCore, int defaultMax) {
        int core = resolvePoolSize(GWC_SEEDER_CORE_POOL_SIZE, defaultCore);
        int max = resolvePoolSize(GWC_SEEDER_MAX_POOL_SIZE, defaultMax);
        if (core > max) {
            log.warning(GWC_SEEDER_CORE_POOL_SIZE
                    + " ("
                    + core
                    + ") is greater than "
                    + GWC_SEEDER_MAX_POOL_SIZE
                    + " ("
                    + max
                    + "), adjusting maxPoolSize to match corePoolSize");
            max = core;
        }
        return new int[] {core, max};
    }

    /**
     * Resolves a pool size configuration value by looking up the given property name first as a Java system property,
     * then as an OS environment variable. Falls back to the provided default if neither is set or the value is not a
     * valid positive integer.
     *
     * @param propertyName the system property / environment variable name to look up
     * @param defaultValue the fallback value if the property is not set or invalid
     * @return the resolved pool size
     */
    static int resolvePoolSize(String propertyName, int defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(propertyName);
        }
        if (value != null && !value.isBlank()) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0) {
                    log.info("Using configured value for " + propertyName + "=" + parsed);
                    return parsed;
                } else {
                    log.warning("Invalid value for "
                            + propertyName
                            + "="
                            + value
                            + " (must be a positive integer), using default "
                            + defaultValue);
                }
            } catch (NumberFormatException e) {
                log.warning("Invalid value for "
                        + propertyName
                        + "="
                        + value
                        + " (not a valid integer), using default "
                        + defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * Destroy method called by the application context at shutdown, needed to gracefully shutdown this thread pool
     * executor and any running thread
     *
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    @Override
    public void destroy() throws Exception {
        log.info("Initiating shut down for running and pending seed tasks...");
        this.shutdownNow();
        while (!this.isTerminated()) {
            log.fine("Waiting for pending tasks to terminate....");
            Thread.sleep(500);
        }
        log.info("Seeder thread pool executor shut down complete.");
    }
}
