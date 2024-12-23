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
 * <p>Copyright 2020
 */
package org.geowebcache.storage.blobstore.file;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates temporary file names with the help of random UUIDS and a counter, to avoid scalablity issues in pure random
 * UUID generation
 */
class TempFileNameGenerator {

    private static final int ROLL_LIMIT = 10000;
    String base = UUID.randomUUID().toString();
    AtomicInteger suffix = new AtomicInteger();

    public String newName() {
        int v = this.suffix.incrementAndGet();
        if (v > ROLL_LIMIT) {
            base = UUID.randomUUID().toString();
            this.suffix.set(0);
            v = this.suffix.incrementAndGet();
        }
        return base + v;
    }
}
