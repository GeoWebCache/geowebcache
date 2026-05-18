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
import static org.junit.Assert.assertNull;

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
 * Verifies the upgrade path in {@link SQLDialect#migrateForeignKeys} (the regular IT suite always starts from fresh-DDL
 * tables and it'd only sees the no-op branch). Each test starts from a "legacy" schema built by stripping the dialect's
 * migrated clause from its own DDL; subclasses plug in the dialect, the DataSource, and the dialect-specific FK
 * metadata through the {@code *FkState} / {@link #legacyDdl(String)} hooks.
 */
public abstract class AbstractForeignKeyMigrationTest {

    protected abstract SQLDialect dialect();

    protected abstract DataSource dataSource();

    /** Recreates the legacy schema. Subclasses call this from their {@code @Before} after wiring the data source. */
    protected void recreateLegacySchema() throws SQLException {
        try (Connection cx = dataSource().getConnection();
                Statement st = cx.createStatement()) {
            dropIfExists(st, "TILEPAGE");
            dropIfExists(st, "TILESET");
            for (String table : dialect().TABLE_CREATION_MAP.keySet()) {
                for (String ddl : dialect().TABLE_CREATION_MAP.get(table)) {
                    st.execute(legacyDdl(ddl));
                }
            }
        }
    }

    /** Hook: dialect DDL with its migrated FK clause stripped and {@code ${schema}} substituted. Oracle overrides. */
    protected String legacyDdl(String ddl) {
        return ddl.replace("${schema}", "").replace(" ON UPDATE CASCADE", "");
    }

    /** Hook: the {@code getImportedKeys} value the migrated FK is expected to settle on. Oracle overrides. */
    protected short expectedMigratedFkState() {
        return (short) DatabaseMetaData.importedKeyCascade;
    }

    /** Hook: dialect-specific FK metadata column to compare against {@link #expectedMigratedFkState()}. */
    protected short readFkState(ResultSet rs) throws SQLException {
        return rs.getShort("UPDATE_RULE");
    }

    /** Hook: {@code DROP TABLE} that handles FK dependents. Oracle needs {@code CASCADE CONSTRAINTS}. */
    protected String dropTableSql(String table) {
        return "DROP TABLE " + table + " CASCADE";
    }

    private void dropIfExists(Statement st, String table) {
        try {
            st.execute(dropTableSql(table));
        } catch (SQLException ignored) {
            // table may not exist on the first run; the legacy CREATEs below recreate it
        }
    }

    @Test
    public void migrateRewritesTilepageForeignKey() throws SQLException {
        short before = requireTilepageFkState();
        assertNotEquals(
                "Legacy TILEPAGE FK should not yet be in its migrated state", expectedMigratedFkState(), before);

        dialect().migrateForeignKeys(null, new SimpleJdbcTemplate(dataSource()));

        short after = requireTilepageFkState();
        assertEquals(
                "Migration should rewrite the TILEPAGE FK to its current dialect shape",
                expectedMigratedFkState(),
                after);
    }

    /** Simulates concurrent migration from multiple JVMs: every call must succeed, the final FK state is migrated. */
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
                "After concurrent migration the FK should be in its migrated state",
                expectedMigratedFkState(),
                requireTilepageFkState());
    }

    /**
     * Crash-recovery scenario raised in review of #1530. DDL is non-transactional (always on Oracle): an interrupted
     * migration can commit the {@code DROP CONSTRAINT} and never reach the {@code ADD}, leaving the next startup with
     * no TILEPAGE -> TILESET FK at all. Migration must treat a missing FK as a legitimate starting state and re-add the
     * migrated FK, not silently no-op because the scan finds no legacy FK row to upgrade.
     */
    @Test
    public void migrateRestoresForeignKeyDroppedByInterruptedMigration() throws SQLException {
        dropTilepageForeignKey();
        assertNull(
                "Precondition: an interrupted migration leaves the TILEPAGE -> TILESET FK absent",
                lookupTilepageFkState());

        dialect().migrateForeignKeys(null, new SimpleJdbcTemplate(dataSource()));

        assertEquals(
                "Migration should re-add the TILEPAGE FK when a prior interrupted migration left it dropped",
                expectedMigratedFkState(),
                requireTilepageFkState());
    }

    @Test
    public void migrateIsIdempotent() throws SQLException {
        SimpleJdbcTemplate template = new SimpleJdbcTemplate(dataSource());
        dialect().migrateForeignKeys(null, template);
        assertEquals(expectedMigratedFkState(), requireTilepageFkState());

        // Second invocation must be a no-op (FK already in its migrated state).
        dialect().migrateForeignKeys(null, template);
        assertEquals(expectedMigratedFkState(), requireTilepageFkState());
    }

    /** Simulates a migration interrupted between its committed DROP and the ADD that never ran. */
    private void dropTilepageForeignKey() throws SQLException {
        try (Connection cx = dataSource().getConnection()) {
            String fkName = lookupTilepageFkName(cx.getMetaData());
            assertNotNull("Precondition: legacy TILEPAGE FK must exist before dropping it", fkName);
            try (Statement st = cx.createStatement()) {
                st.execute("ALTER TABLE TILEPAGE DROP CONSTRAINT " + fkName);
            }
        }
    }

    private String lookupTilepageFkName(DatabaseMetaData dbmd) throws SQLException {
        String name = findTilesetFkName(dbmd, "tilepage");
        return name != null ? name : findTilesetFkName(dbmd, "TILEPAGE");
    }

    private String findTilesetFkName(DatabaseMetaData dbmd, String tableName) throws SQLException {
        try (ResultSet rs = dbmd.getImportedKeys(null, null, tableName)) {
            while (rs.next()) {
                String pkTable = rs.getString("PKTABLE_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                if ("TILESET".equalsIgnoreCase(pkTable) && "TILESET_ID".equalsIgnoreCase(fkColumn)) {
                    return rs.getString("FK_NAME");
                }
            }
        }
        return null;
    }

    private short requireTilepageFkState() throws SQLException {
        Short state = lookupTilepageFkState();
        assertNotNull("TILEPAGE -> TILESET foreign key not found in metadata", state);
        return state;
    }

    private Short lookupTilepageFkState() throws SQLException {
        try (Connection cx = dataSource().getConnection()) {
            DatabaseMetaData dbmd = cx.getMetaData();
            Short state = findTilesetFkState(dbmd, "tilepage");
            return state != null ? state : findTilesetFkState(dbmd, "TILEPAGE");
        }
    }

    private Short findTilesetFkState(DatabaseMetaData dbmd, String tableName) throws SQLException {
        try (ResultSet rs = dbmd.getImportedKeys(null, null, tableName)) {
            while (rs.next()) {
                String pkTable = rs.getString("PKTABLE_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                if ("TILESET".equalsIgnoreCase(pkTable) && "TILESET_ID".equalsIgnoreCase(fkColumn)) {
                    return readFkState(rs);
                }
            }
        }
        return null;
    }
}
