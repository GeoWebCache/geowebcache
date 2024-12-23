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
 * @author Andrea Aime - GeoSolutions Copyright 2012
 */
package org.geowebcache.diskquota.jdbc;

import java.util.Map;
import org.springframework.dao.DataAccessException;

/**
 * A data access exception reporting both the parametrized sql and the actual parameters values
 *
 * @author Andrea Aime - GeoSolutions
 */
@SuppressWarnings("serial")
public class ParametricDataAccessException extends DataAccessException {

    public ParametricDataAccessException(String sql, Map<String, ?> params) {
        super(buildMessage(sql, params));
    }

    public ParametricDataAccessException(String sql, Map<String, ?> params, Throwable cause) {
        super(buildMessage(sql, params), cause);
    }

    private static String buildMessage(String sql, Map<String, ?> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("Failed to execute statement " + sql);
        sb.append(" with params: " + params);

        return sb.toString();
    }
}
