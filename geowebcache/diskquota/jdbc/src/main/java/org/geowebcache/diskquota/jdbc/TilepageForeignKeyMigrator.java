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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.geotools.util.logging.Logging;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/**
 * Upgrades the {@code TILEPAGE -> TILESET} foreign key of a pre-existing quota-store schema to the dialect's current FK
 * definition. This lets {@link SQLDialect#getRenameLayerStatement renaming a layer} rewrite {@code TILESET.KEY} without
 * orphaning the dependent {@code TILEPAGE} rows. The dialect supplies the two variation points (whether a FK row is
 * already migrated, and the {@code ADD FOREIGN KEY} statement that creates it); everything else - the scan, the
 * drop/add, and the crash/concurrency recovery - lives here.
 *
 * <p>Converges on the migrated FK from whatever state the scan finds:
 *
 * <ul>
 *   <li>already migrated -> no-op (idempotent)
 *   <li>legacy FK present -> drop it, then add the migrated FK
 *   <li>no FK present -> add the migrated FK
 * </ul>
 *
 * <p>The "no FK present" branch recovers an interrupted migration. DDL is non-transactional (always on Oracle): a crash
 * can commit the drop and never reach the add, leaving no FK at all. A scan that only upgraded an existing legacy FK
 * would skip such a table forever; the drop and add are kept as separate committed steps a later startup can finish
 * from.
 *
 * <p>Concurrent-startup safe: a racing instance can make our drop or add throw (the constraint name is gone, or on
 * Oracle a NOWAIT lock conflict). We then re-check the live FK and accept an already-migrated FK as success.
 */
class TilepageForeignKeyMigrator {

    private static final Logger LOG = Logging.getLogger(TilepageForeignKeyMigrator.class);

    private static final int RECHECK_ATTEMPTS = 20;
    private static final long RECHECK_BACKOFF_MS = 100L;

    /** Tests whether a {@link DatabaseMetaData#getImportedKeys} FK row is already migrated for this dialect. */
    @FunctionalInterface
    interface MigratedFkTest {
        boolean appliesTo(ResultSet fkRow) throws SQLException;
    }

    /** Builds the dialect's {@code ALTER TABLE ... ADD FOREIGN KEY} statement for its migrated FK. */
    @FunctionalInterface
    interface AddFkStatement {
        String forTable(String prefixedTilepageName, String prefix);
    }

    private final MigratedFkTest migratedFkTest;
    private final AddFkStatement addFkStatement;

    TilepageForeignKeyMigrator(MigratedFkTest migratedFkTest, AddFkStatement addFkStatement) {
        this.migratedFkTest = migratedFkTest;
        this.addFkStatement = addFkStatement;
    }

    /** Runs the migration, logging a warning (rather than failing startup) if database metadata is unavailable. */
    void migrate(String schema, SimpleJdbcTemplate template) {
        DataSource ds = Objects.requireNonNull(((JdbcAccessor) template.getJdbcOperations()).getDataSource());
        try {
            JdbcUtils.extractDatabaseMetaData(ds, dbmd -> upgradeTilepageForeignKey(dbmd, schema, template));
        } catch (MetaDataAccessException e) {
            LOG.log(Level.WARNING, "Could not migrate the TILEPAGE foreign key; layer renames may leave stale rows", e);
        }
    }

    private Void upgradeTilepageForeignKey(DatabaseMetaData dbmd, String schema, SimpleJdbcTemplate template)
            throws SQLException {

        final String tilepageName = resolveTableName(dbmd, schema, "TILEPAGE");
        if (tilepageName == null) {
            return null;
        }
        if (isTilepageFkAlreadyMigrated(dbmd, schema, tilepageName)) {
            return null;
        }
        final String prefix = schema == null ? "" : schema + ".";
        final String prefixedTilepageName = prefix + tilepageName;
        final JdbcOperations jdbcOperations = template.getJdbcOperations();

        String legacyFkName = findLegacyTilepageFkName(dbmd, schema, tilepageName);
        if (legacyFkName != null) {
            LOG.info(() -> "Migrating TILEPAGE.TILESET_ID foreign key (was constraint %s)".formatted(legacyFkName));
            boolean migratedByPeer = dropLegacyTilepageFk(
                    jdbcOperations, prefixedTilepageName, legacyFkName, dbmd, schema, tilepageName);
            if (migratedByPeer) {
                return null;
            }
        }
        addMigratedTilepageFk(jdbcOperations, prefixedTilepageName, prefix, dbmd, schema, tilepageName);
        return null;
    }

    /**
     * Drops the legacy FK as its own committed statement, separate from the {@link #addMigratedTilepageFk add} that
     * follows. DDL is non-transactional (always on Oracle): this commit can be the last step an interrupted migration
     * completes, and the separate add lets a later startup finish from the "no FK present" state.
     *
     * @return {@code true} if a concurrent peer already completed the migration (nothing left to do); {@code false} if
     *     the legacy FK is now gone and the caller should add the migrated FK
     * @throws DataAccessException if the drop failed for a reason other than a concurrent migration (legacy FK still
     *     present)
     */
    private boolean dropLegacyTilepageFk(
            JdbcOperations jdbcOperations,
            String prefixedTilepageName,
            String legacyFkName,
            DatabaseMetaData dbmd,
            String schema,
            String tilepageName)
            throws SQLException {
        String drop = "ALTER TABLE %s DROP CONSTRAINT %s".formatted(prefixedTilepageName, legacyFkName);
        try {
            jdbcOperations.execute(drop);
            return false;
        } catch (DataAccessException raceLikely) {
            if (awaitConcurrentMigration(dbmd, schema, tilepageName)) {
                LOG.fine(() -> "TILEPAGE FK was migrated concurrently by another instance while this instance "
                        + "was trying to drop %s; accepting concurrent migration".formatted(legacyFkName));
                return true;
            }
            if (findLegacyTilepageFkName(dbmd, schema, tilepageName) != null) {
                throw raceLikely;
            }
            return false;
        }
    }

    /**
     * Adds the migrated FK as its own committed statement. Reached after this instance drops the legacy FK, or directly
     * when an earlier interrupted migration (or a peer) already dropped it and left no {@code TILEPAGE -> TILESET} FK.
     */
    private void addMigratedTilepageFk(
            JdbcOperations jdbcOperations,
            String prefixedTilepageName,
            String prefix,
            DatabaseMetaData dbmd,
            String schema,
            String tilepageName)
            throws SQLException {
        String add = addFkStatement.forTable(prefixedTilepageName, prefix);
        try {
            jdbcOperations.execute(add);
        } catch (DataAccessException raceLikely) {
            if (awaitConcurrentMigration(dbmd, schema, tilepageName)) {
                LOG.fine(() -> "TILEPAGE FK was migrated concurrently by another instance while this instance "
                        + "was re-adding it; accepting concurrent migration");
                return;
            }
            throw raceLikely;
        }
    }

    /** Returns the name of the legacy (not-yet-migrated) {@code TILEPAGE -> TILESET} FK, or {@code null} if none. */
    private String findLegacyTilepageFkName(DatabaseMetaData dbmd, String schema, String tilepageName)
            throws SQLException {
        try (ResultSet rs = dbmd.getImportedKeys(null, schema, tilepageName)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                if (isTilepageTilesetFkRow(rs, fkName) && !migratedFkTest.appliesTo(rs)) {
                    return fkName;
                }
            }
        }
        return null;
    }

    /**
     * Polls {@link #isTilepageFkAlreadyMigrated} after a failed drop/add to absorb the window in which a peer instance
     * is mid-migration. Oracle's {@code ALTER TABLE} uses NOWAIT: a concurrent peer can fail us with ORA-00054 while it
     * still has the drop committed but not yet the add, and a one-shot recheck would observe the in-flight state and
     * give up.
     */
    private boolean awaitConcurrentMigration(DatabaseMetaData dbmd, String schema, String tilepageName)
            throws SQLException {
        for (int attempt = 0; attempt < RECHECK_ATTEMPTS; attempt++) {
            if (isTilepageFkAlreadyMigrated(dbmd, schema, tilepageName)) {
                return true;
            }
            try {
                Thread.sleep(RECHECK_BACKOFF_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Re-checks FK state after a failed migration attempt to detect a concurrent migration from another instance. */
    private boolean isTilepageFkAlreadyMigrated(DatabaseMetaData dbmd, String schema, String tilepageName)
            throws SQLException {
        try (ResultSet rs = dbmd.getImportedKeys(null, schema, tilepageName)) {
            while (rs.next()) {
                if (isTilepageTilesetFkRow(rs, rs.getString("FK_NAME")) && migratedFkTest.appliesTo(rs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Identity-only check: is this metadata row the TILEPAGE -> TILESET(KEY) FK? */
    private static boolean isTilepageTilesetFkRow(ResultSet rs, String fkName) throws SQLException {
        if (fkName == null || fkName.isEmpty()) {
            return false;
        }
        String pkTable = rs.getString("PKTABLE_NAME");
        String fkColumn = rs.getString("FKCOLUMN_NAME");
        return "TILESET".equalsIgnoreCase(pkTable) && "TILESET_ID".equalsIgnoreCase(fkColumn);
    }

    private static String resolveTableName(DatabaseMetaData dbmd, String schema, String tableName) throws SQLException {
        try (ResultSet rs = dbmd.getTables(null, schema, tableName.toLowerCase(), null)) {
            if (rs.next()) {
                return rs.getString("TABLE_NAME");
            }
        }
        try (ResultSet rs = dbmd.getTables(null, schema, tableName, null)) {
            if (rs.next()) {
                return rs.getString("TABLE_NAME");
            }
        }
        return null;
    }
}
