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
 * @author Tobias Schulmann, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.swift;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.BlobStoreListenerList;
import org.jclouds.http.HttpResponseException;
import org.jclouds.io.Payload;
import org.jclouds.openstack.swift.v1.domain.SwiftObject;
import org.jclouds.openstack.swift.v1.features.ObjectApi;

public class SwiftUploadTask implements Runnable {
    static final Log log = LogFactory.getLog(SwiftUploadTask.class);
    static final String logStr = "%s, %s, %dms";

    private final String key;
    private final SwiftTile tile;
    private final ObjectApi objectApi;
    private final BlobStoreListenerList listeners;

    public SwiftUploadTask(
            String key, SwiftTile tile, BlobStoreListenerList listeners, ObjectApi objectApi) {
        this.key = key;
        this.tile = tile;
        this.objectApi = objectApi;
        this.listeners = listeners;
    }

    private void checkExisted() {
        if (listeners.isEmpty()) {
            return;
        }
        LocalDateTime time = LocalDateTime.now();
        long getWithoutBody = System.nanoTime();
        SwiftObject object = objectApi.getWithoutBody(key);
        log.info(
                String.format(
                        logStr,
                        time.format(DateTimeFormatter.ISO_DATE_TIME),
                        "HEAD",
                        (System.nanoTime() - getWithoutBody) / 1000000));
        if (object == null) {
            return;
        }

        try (Payload payload = object.getPayload()) {
            tile.setExisted(payload.getContentMetadata().getContentLength());
        } catch (IOException e) {
            log.warn(e.getMessage());
            // pass
        }
    }

    public void run() {
        log.debug("Processing " + key);

        checkExisted();

        try (Payload payload = tile.getPayload()) {
            String localLogStr = "%s, %s, %dms, %dkB";
            LocalDateTime time = LocalDateTime.now();
            long upload = System.nanoTime();
            objectApi.put(key, payload);
            log.debug(
                    String.format(
                            localLogStr,
                            time.format(DateTimeFormatter.ISO_DATE_TIME),
                            "PUT",
                            (System.nanoTime() - upload) / 1000000,
                            payload.getContentMetadata().getContentLength()));
            tile.notifyListeners(listeners);
        } catch (HttpResponseException e) {
            log.warn(e.getMessage());
            throw e;
        } catch (IOException e) {
            // pass
        }
    }
}
