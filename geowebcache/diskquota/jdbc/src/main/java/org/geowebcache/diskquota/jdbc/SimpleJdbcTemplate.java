package org.geowebcache.diskquota.jdbc;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.simple.ParameterizedRowMapper;

/**
 * An {@link SimpleJDBCTemplate} extended with some utility methods
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class SimpleJdbcTemplate extends org.springframework.jdbc.core.simple.SimpleJdbcTemplate {

    public SimpleJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Queries the template for a single object, but makes the result optial, it is ok not to find
     * any object in the db
     * 
     * @param sql
     * @param rowMapper
     * @param params
     * @return
     */
    public <T> T queryForOptionalObject(String sql, ParameterizedRowMapper<T> rowMapper, Map params) {
        List<T> results = query(sql, rowMapper, params);
        if (results.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, results.size());
        } else if (results.size() == 1) {
            return results.get(0);
        } else {
            return null;
        }
    }

}
