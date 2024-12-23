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
 * <p>Copyright 2022
 */
package org.geowebcache.diskquota.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.sql.Connection;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import org.geotools.util.factory.GeoTools;
import org.geowebcache.config.ConfigurationException;
import org.junit.Test;
import org.mockito.Mockito;

public class JDBCQuotaStoreFactoryTest {

    @Test
    @SuppressWarnings({"PMD.CloseResource", "BanJNDI"})
    public void testJNDILookup() throws Exception {
        // setup mock initial context (need a JNDI provider otherwise, like simple-jndi)
        InitialContext ctx = Mockito.mock(InitialContext.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection cx = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(cx);
        String javaName = "java://data/source";
        Mockito.when(ctx.lookup(javaName)).thenReturn(dataSource);
        String httpName = "http://data/source";
        Mockito.when(ctx.lookup(httpName)).thenReturn(dataSource);

        // using default validator
        GeoTools.init(ctx);

        try {
            JDBCConfiguration javaConfiguration = new JDBCConfiguration();
            javaConfiguration.setJNDISource(javaName);
            JDBCConfiguration httpConfiguration = new JDBCConfiguration();
            httpConfiguration.setJNDISource(httpName);

            assertSame(dataSource, new JDBCQuotaStoreFactory().getDataSource(javaConfiguration));
            try {
                new JDBCQuotaStoreFactory().getDataSource(httpConfiguration);
                fail("Lookup should have failed with an exception");
            } catch (ConfigurationException exception) {
                assertEquals("Failed to get a datasource from: " + httpName, exception.getMessage());
            }
        } finally {
            GeoTools.clearInitialContext();
        }
    }
}
