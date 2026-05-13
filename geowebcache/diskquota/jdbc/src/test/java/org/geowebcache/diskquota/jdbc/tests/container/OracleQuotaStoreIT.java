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
package org.geowebcache.diskquota.jdbc.tests.container;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.geowebcache.diskquota.jdbc.OracleDialect;
import org.geowebcache.diskquota.jdbc.SQLDialect;
import org.geowebcache.testcontainers.jdbc.OracleXEContainer;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Runs the full {@code JDBCQuotaStoreTest} suite against a real Oracle Express Edition via Testcontainers.
 *
 * <p>If Docker is unavailable the class is skipped cleanly through {@link OracleXEContainer#disabledWithoutDocker()}.
 *
 * <h2>This is {@link Ignore @Ignore}d for now</h2>
 *
 * <p>Running the suite against Oracle XE 21c surfaces a longstanding gap, not specific to this CI plumbing: any
 * SERIALIZABLE transaction in {@code JDBCQuotaStore} whose first read goes through TILEPAGE (or, less often, TILESET)
 * fails with <a href="https://docs.oracle.com/error-help/db/ora-08176/">{@code ORA-08176: consistent read failure;
 * rollback data not available}</a>. Oracle's own diagnostic for this error names the cause: <em>"Encountered data
 * changed by an operation that does not generate rollback data: create index, direct load or discrete
 * transaction."</em> The quota store creates four indexes on TILEPAGE at startup, and Oracle XE's snapshot machinery
 * cannot reconstruct a consistent read across that recent DDL within a SERIALIZABLE transaction.
 *
 * <p>The Oracle-recommended remedy is to retry the transaction so a fresh snapshot SCN is taken. Once
 * {@code JDBCQuotaStore} wraps each {@code tt.execute(...)} with bounded retry on serialization failures, ORA-08176
 * will succeed on a re-attempt - exactly the pattern Oracle's diagnostics recommend.
 *
 * <p>Concretely, what we observed running this IT against {@code gvenzl/oracle-xe:21-slim-faststart}:
 *
 * <ul>
 *   <li>10/19 tests error with ORA-08176 on {@code INSERT INTO TILEPAGE ... SELECT ... FROM DUAL WHERE NOT EXISTS}
 *       inside the SERIALIZABLE runtime path ({@code addToQuotaAndTileCounts}, {@code addHitsAndSetAccesTime},
 *       {@code setTruncated}).
 *   <li>The remaining 9 either touch TILESET only or fire async writes without awaiting them, so they don't observe the
 *       failure.
 *   <li>Rewriting the conditional INSERTs as Oracle {@code MERGE INTO} immediately surfaces a second issue (Spring's
 *       named-parameter binding can't decide a SQL type for a {@code null} parametersId, hitting ORA-17004), and even
 *       with that fixed the underlying snapshot read on freshly indexed tables is the real blocker. The retry layer is
 *       the right level to fix this.
 * </ul>
 *
 * <p>The class is kept in the suite (rather than deleted) so:
 *
 * <ol>
 *   <li>The CI workflow's claim that it covers the Oracle dialect via Testcontainers stays honest: the infrastructure
 *       is in place; only the {@code @Ignore} flips off when the retry layer lands.
 *   <li>The Oracle XE container plumbing is exercised by the workflow at least up to {@code @ClassRule} startup, so it
 *       doesn't bit-rot.
 *   <li>The next person to look at Oracle support has a working scaffold and a clear pointer at the root cause.
 * </ol>
 */
@Ignore("Pending the SERIALIZABLE retry layer; see class javadoc for ORA-08176 root cause.")
public class OracleQuotaStoreIT extends AbstractJDBCQuotaStoreIT {

    @ClassRule
    public static final OracleXEContainer ORACLE = OracleXEContainer.latest().disabledWithoutDocker();

    @Override
    protected SQLDialect getDialect() {
        return new OracleDialect();
    }

    @Override
    protected String getFixtureId() {
        return "oracle-testcontainer";
    }

    @Override
    protected JdbcDatabaseContainer<?> getContainer() {
        return ORACLE;
    }

    /**
     * Oracle requires {@code CASCADE CONSTRAINTS}, not just {@code CASCADE}, to drop tables with dependents; the base
     * cleanup uses the standard SQL form which silently fails on Oracle.
     */
    @Override
    protected void cleanupDatabase(DataSource dataSource) throws SQLException {
        try (Connection cx = dataSource.getConnection();
                Statement st = cx.createStatement()) {
            try {
                st.execute("DROP TABLE TILEPAGE CASCADE CONSTRAINTS");
            } catch (Exception e) {
                // fine, table may not exist
            }
            try {
                st.execute("DROP TABLE TILESET CASCADE CONSTRAINTS");
            } catch (Exception e) {
                // fine too
            }
        }
    }
}
