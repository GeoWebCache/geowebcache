package org.geowebcache.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Helper class
 * @author ez
 *
 */
public class SQLUtils {

    /**
     * Will handle null and exceptions
     * 
     * @param rs
     */
    public static void closeQuietly(ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (Throwable thx) {
        }
    }

    /**
     * Will handle null and exceptions
     * 
     * @param c
     */
    public static void closeQuietly(Connection c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (Throwable e) {
        }
    }

    /**
     * Will handle null and exceptions
     * 
     * @param s
     */
    public static void closeQuietly(Statement s) {
        try {
            if (s != null) {
                s.close();
            }
        } catch (Throwable e) {
        }
    }
    
}
