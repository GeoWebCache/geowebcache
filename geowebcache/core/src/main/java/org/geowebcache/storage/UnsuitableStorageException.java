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
package org.geowebcache.storage;

import java.io.Serial;

/**
 * Thrown when a Blobstore was initialized with persistance that is unsuitable, such as a non-empty directory
 *
 * @author smithkm
 */
public class UnsuitableStorageException extends StorageException {

    /** serialVersionUID */
    @Serial
    private static final long serialVersionUID = 3939772540460067187L;

    public UnsuitableStorageException(String msg) {
        super(msg);
    }

    @SuppressWarnings("FallThrough") // REVISIT the switch logic is hard to follow
    public static void checkSuitability(String location, final boolean exists, boolean empty)
            throws UnsuitableStorageException {
        switch (CompositeBlobStore.getStoreSuitabilityCheck()) {
            case EXISTING:
                if (exists) {
                    break;
                }
            case EMPTY:
                if (!empty) {
                    throw new UnsuitableStorageException(
                            "Attempted to create Blob Store in " + location + " but it was not empty");
                }
            case NONE:
        }
    }
}
