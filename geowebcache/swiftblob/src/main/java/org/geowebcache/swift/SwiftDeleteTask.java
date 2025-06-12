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
 * @author Matthew Northcott, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.swift;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedSwiftBlobStore;

class SwiftDeleteTask implements Runnable {
    static final Logger log = Logging.getLogger(SwiftDeleteTask.class.getName());
    static final String logStr = "%s, %s, %dms";

    private static final int RETRIES = 5;

    private final RegionScopedSwiftBlobStore blobStore;
    private final String path;
    private final String container;
    private final IBlobStoreListenerNotifier notifier;

    SwiftDeleteTask(
            RegionScopedSwiftBlobStore blobStore, String path, String container, IBlobStoreListenerNotifier notifier) {
        this.blobStore = blobStore;
        this.path = path;
        this.container = container;
        this.notifier = notifier;
    }

    @Override
    public void run() {
        final ListContainerOptions options =
                new ListContainerOptions().prefix(path).recursive();

        int delayMs = 1000;
        boolean deleted = false;

        // Attempt to delete path and increase timeout exponentially with each consective failure
        for (int retry = 0; retry < RETRIES && !deleted; retry++) {
            blobStore.clearContainer(container, options);

            // Wait before checking if deletion was successful
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                log.fine(e.getMessage());
            }
            delayMs *= 2; // Exponential backoff

            // NOTE: this is messy but it seems to work.
            // there might be a more effecient way of doing this.
            deleted = blobStore.list(container, options).isEmpty();
        }

        if (deleted) {
            log.info("Deleted Swift tile cache at %s/%s".formatted(container, path));

            if (notifier != null) {
                notifier.notifyListeners();
            }
        } else {
            log.log(
                    Level.SEVERE,
                    "Failed to delete Swift tile cache at %s/%s after %d retries.".formatted(container, path, RETRIES));
        }
    }
}
