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
 */
package org.geowebcache.storage.blobstore.gcs;

import org.geowebcache.storage.TileObject;
import org.geowebcache.util.TMSKeyBuilder;
import org.springframework.util.StringUtils;

record TileLocation(String prefix, CacheId cache, TileIndex tile) {

    /**
     * Same as {@link TMSKeyBuilder#forTile(TileObject)} but using this recrod's data
     *
     * @return {@code <prefix>/<layer name>/<gridset id>/<format id>/<parametersId>/<z>/<x>/<y>.<extension>}
     */
    public String getStorageKey() {
        String parametersId = cache.parametersId();
        if (parametersId == null) {
            parametersId = "default";
        }
        String layerId = cache.layerId();
        String gridset = cache.gridsetId();
        String shortFormat = cache.format().getFileExtension();
        String extension = cache.format().getInternalName(); // png, jpeg, etc.

        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(prefix)) {
            sb.append(prefix).append('/');
        }

        sb.append(layerId)
                .append('/')
                .append(gridset)
                .append('/')
                .append(shortFormat)
                .append('/')
                .append(parametersId)
                .append('/')
                .append(tile.z())
                .append('/')
                .append(tile.x())
                .append('/')
                .append(tile.y())
                .append('.')
                .append(extension);

        return sb.toString();
    }
}
