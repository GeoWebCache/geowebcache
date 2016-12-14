/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geotools.mbtiles;

import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.File;
import java.sql.Connection;

/**
 * Utils objects used to interact with GeoTools.
 */
public final class GeoToolsMbtilesUtils {

    private GeoToolsMbtilesUtils() {
    }

    public static MBTilesFile getMBTilesFile(Connection connection, File file) {
        try {
            return new MbtilesFileExtended(connection);
        } catch (Exception exception) {
            throw new RuntimeException(String.format("Error creating MBTiles file for '%s'.", file), exception);
        }
    }

    /**
     * Extended version of GeoTools Mbtiles that allow us to pass the connection to be used.
     */
    private final static class MbtilesFileExtended extends MBTilesFile {

        public MbtilesFileExtended(Connection connection) throws Exception {
            super(new SingleConnectionDataSource(connection, false));
        }
    }
}