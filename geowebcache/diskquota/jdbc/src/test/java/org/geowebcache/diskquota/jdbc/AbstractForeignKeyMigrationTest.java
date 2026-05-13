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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.Test;

/**
 * Verifies the legacy-to-current path in {@link SQLDialect#migrateForeignKeys} that drops the existing {@code TILEPAGE
 * -> TILESET} foreign key (declared with only {@code ON DELETE CASCADE}) and re-adds it with {@code ON UPDATE CASCADE
 * ON DELETE CASCADE}.
 *
 * <p>The current {@code JDBCQuotaStoreTest} suite always starts from fresh-DDL tables, so it only exercises the no-op
 * idempotent branch of the migration; this test class fills in the upgrade path.
 *
 * <p>Each test starts from a "legacy" schema built by stripping {@code " ON UPDATE CASCADE"} from the dialect's own
 * table-creation SQL (i.e. the pre-fix shape of the FK). Subclasses provide the dialect and a DataSource pointed at the
 * database under test.
 */
public abstract class AbstractForeignKeyMigrationTest {

    /** Dialect under test. */
    protected abstract SQLDialect dialect();

    /** Data source pointed at a usable database where the legacy schema can be (re)created. */
    protected abstract DataSource dataSource();

    /**
     * Recreates the legacy schema. Subclasses call this from their {@code @Before} after wiring the data source; not
     * annotated so the dialect/dataSource setup ordering is always explicit.
     */
    protected void recreateLegacySchema() throws SQLException {
        try (Connection cx = dataSource().getConnection();
                Statement st = cx.createStatement()) {
            dropIfExists(st, "TILEPAGE");
            dropIfExists(st, "TILESET");
            for (String table : dialect().TABLE_CREATION_MAP.keySet()) {
                for (String ddl : dialect().TABLE_CREATION_MAP.get(table)) {
                    String legacy = stripCascadeOnUpdate(ddl);
                    st.execute(legacy);
                }
            }
        }
    }

    /**
     * Reproduces the pre-fix DDL by removing the {@code ON UPDATE CASCADE} clause that was added to the TILEPAGE FK.
     */
    private static String stripCascadeOnUpdate(String ddl) {
        return ddl.replace("${schema}", "").replace(" ON UPDATE CASCADE", "");
    }

    private static void dropIfExists(Statement st, String table) {
        try {
            st.execute("DROP TABLE " + table + " CASCADE");
        } catch (SQLException ignored) {
            // table may not exist on the first run; the legacy CREATEs below recreate it
        }
    }

    @Test
    public void migrateAddsOnUpdateCascadeToTilepageForeignKey() throws SQLException {
        short ruleBefore = requireTilepageFkUpdateRule();
        assertNotEquals(
                "Legacy TILEPAGE FK should not yet be ON UPDATE CASCADE",
                (short) DatabaseMetaData.importedKeyCascade,
                ruleBefore);

        dialect().migrateForeignKeys(null, new SimpleJdbcTemplate(dataSource()));

        short ruleAfter = requireTilepageFkUpdateRule();
        assertEquals(
                "Migration should rewrite TILEPAGE FK as ON UPDATE CASCADE",
                (short) DatabaseMetaData.importedKeyCascade,
                ruleAfter);
    }

    /**
     * Simulates multiple JVMs starting at the same time against a shared database with the legacy FK still in place.
     * Both call {@code migrateForeignKeys} concurrently; the migration must remain idempotent end-to-end - neither call
     * should propagate an exception, and the final FK state must be cascade-on-update.
     */
    @Test
    public void migrateIsConcurrentStartupSafe() throws Exception {
        int threads = 4;
        CyclicBarrier startGate = new CyclicBarrier(threads);
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        try {
            Callable<Void> migrator = () -> {
                startGate.await();
                dialect().migrateForeignKeys(null, new SimpleJdbcTemplate(dataSource()));
                return null;
            };
            List<Future<Void>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(exec.submit(migrator));
            }
            List<Throwable> failures = new ArrayList<>();
            for (Future<Void> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            if (!failures.isEmpty()) {
                AssertionError ae = new AssertionError("Concurrent migrateForeignKeys threw on " + failures.size() + "/"
                        + threads + " threads; " + "see suppressed");
                failures.forEach(ae::addSuppressed);
                throw ae;
            }
        } finally {
            exec.shutdownNow();
            exec.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(
                "After concurrent migration the FK should be ON UPDATE CASCADE",
                (short) DatabaseMetaData.importedKeyCascade,
                requireTilepageFkUpdateRule());
    }

    @Test
    public void migrateIsIdempotent() throws SQLException {
        SimpleJdbcTemplate template = new SimpleJdbcTemplate(dataSource());
        dialect().migrateForeignKeys(null, template);
        assertEquals((short) DatabaseMetaData.importedKeyCascade, requireTilepageFkUpdateRule());

        // Second invocation must be a no-op (FK already cascade-on-update).
        dialect().migrateForeignKeys(null, template);
        assertEquals((short) DatabaseMetaData.importedKeyCascade, requireTilepageFkUpdateRule());
    }

    private short requireTilepageFkUpdateRule() throws SQLException {
        Short rule = lookupTilepageFkUpdateRule();
        assertNotNull("TILEPAGE -> TILESET foreign key not found in metadata", rule);
        return rule;
    }

    private Short lookupTilepageFkUpdateRule() throws SQLException {
        try (Connection cx = dataSource().getConnection()) {
            DatabaseMetaData dbmd = cx.getMetaData();
            Short rule = findTilesetFkUpdateRule(dbmd, "tilepage");
            return rule != null ? rule : findTilesetFkUpdateRule(dbmd, "TILEPAGE");
        }
    }

    private static Short findTilesetFkUpdateRule(DatabaseMetaData dbmd, String tableName) throws SQLException {
        try (ResultSet rs = dbmd.getImportedKeys(null, null, tableName)) {
            while (rs.next()) {
                String pkTable = rs.getString("PKTABLE_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                if ("TILESET".equalsIgnoreCase(pkTable) && "TILESET_ID".equalsIgnoreCase(fkColumn)) {
                    return rs.getShort("UPDATE_RULE");
                }
            }
        }
        return null;
    }
}
