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
 * <p>Copyright 2019
 */
package org.geowebcache.diskquota;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/** Shared implementation for UsageStatsMonitor and QuotaUpdatesMonitor */
public abstract class AbstractMonitor {
    private static final Logger log = Logging.getLogger(AbstractMonitor.class.getName());

    public AbstractMonitor() {
        super();
    }

    private ExecutorService executorService;

    public void startUp() {
        executorService = Executors.newSingleThreadExecutor(getThreadFactory());
    }

    protected abstract CustomizableThreadFactory getThreadFactory();

    protected ExecutorService getExecutorService() {
        return executorService;
    }

    /** Calls for a shut down and waits until any remaining task finishes before returning */
    public void shutDown() {
        final boolean cancel = false;
        shutDown(cancel);

        final int maxAttempts = 6;
        final int seconds = 5;
        int attempts = 1;
        boolean interrupted = false;
        try {
            while (!getExecutorService().isTerminated()) {
                attempts++;
                try {
                    awaitTermination(seconds, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                    String message = "Usage statistics thread helper for DiskQuota failed to shutdown within "
                            + (attempts * seconds)
                            + " seconds. Attempt "
                            + attempts
                            + " of "
                            + maxAttempts
                            + "...";
                    log.warning(message);
                    if (attempts == maxAttempts) {
                        throw new RuntimeException(message, e);
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void awaitTermination(int timeout, TimeUnit units) throws InterruptedException {
        if (!getExecutorService().isShutdown()) {
            throw new IllegalStateException(
                    "Called awaitTermination but the " + "UsageStatsMonitor is not shutting down");
        }
        getExecutorService().awaitTermination(timeout, units);
    }

    protected abstract void shutDown(final boolean cancel);

    /** Calls for a shut down and returns immediately */
    public void shutDownNow() {
        shutDown(true);
    }
}
