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
 * <p>Copyright 2026
 */
package org.geowebcache.diskquota.jdbc;

import javax.sql.DataSource;

/**
 * In-memory HSQL run of {@link AbstractJDBCQuotaStoreConcurrencyTest}. HSQL serializes writers via row locks rather
 * than aborting them, so this only exercises the no-conflict path; PG/Oracle ITs cover the abort/retry path.
 */
public class HSQLQuotaStoreConcurrencyTest extends AbstractJDBCQuotaStoreConcurrencyTest {

    private static int INSTANCE_COUNTER = 0;

    @Override
    protected DataSource newDataSource() {
        return newPooledDataSource(
                "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:concurrency-" + (++INSTANCE_COUNTER), "sa", "");
    }

    @Override
    protected SQLDialect newDialect() {
        return new HSQLDialect();
    }
}
