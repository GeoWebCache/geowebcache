package org.geowebcache.config;

import java.sql.Connection;
import java.sql.SQLException;

public interface PostgresCommand {

    public void execute(Connection con) throws SQLException;
    
}
