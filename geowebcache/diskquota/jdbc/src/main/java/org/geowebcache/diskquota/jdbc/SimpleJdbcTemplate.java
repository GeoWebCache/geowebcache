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

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * An {@link NamedParameterJdbcTemplate} extended with some utility methods and with failure reporting which includes
 * the parameter values for parameterized statements
 *
 * @author Andrea Aime - GeoSolutions
 */
public class SimpleJdbcTemplate extends NamedParameterJdbcTemplate {

    public SimpleJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Queries the template for a single object, but makes the result optial, it is ok not to find any object in the db
     */
    public <T> T queryForOptionalObject(String sql, RowMapper<T> rowMapper, Map<String, ?> params) {
        try {
            List<T> results = query(sql, params, rowMapper);
            if (results.size() > 1) {
                throw new IncorrectResultSizeDataAccessException(1, results.size());
            } else if (results.size() == 1) {
                return results.get(0);
            } else {
                return null;
            }
        } catch (DataAccessException e) {
            throw new ParametricDataAccessException(sql, params, e);
        }
    }

    @Override
    public int update(String sql, Map<String, ?> params) throws DataAccessException {
        try {
            return super.update(sql, params);
        } catch (DataAccessException e) {
            throw new ParametricDataAccessException(sql, params, e);
        }
    }
}
