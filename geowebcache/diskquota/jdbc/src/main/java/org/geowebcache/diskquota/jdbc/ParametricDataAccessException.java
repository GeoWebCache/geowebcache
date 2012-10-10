package org.geowebcache.diskquota.jdbc;

import java.util.Map;

import org.springframework.dao.DataAccessException;

@SuppressWarnings("serial")
public class ParametricDataAccessException extends DataAccessException {

    public ParametricDataAccessException(String sql, Map<String, Object> params) {
        super(buildMessage(sql, params));
    }

    public ParametricDataAccessException(String sql, Map<String, Object> params, Throwable cause) {
        super(buildMessage(sql, params), cause);
    }

    private static String buildMessage(String sql, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("Failed to execute statement " + sql);
        sb.append(" with params: " + params);
        
        return sb.toString();
    }

}
