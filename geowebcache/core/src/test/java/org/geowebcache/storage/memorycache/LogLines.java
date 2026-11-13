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
package org.geowebcache.storage.memorycache;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.rules.ExternalResource;

/** Collects the messages one class logs at {@code level} and above during a test. */
class LogLines extends ExternalResource {

    final List<String> lines = new CopyOnWriteArrayList<>();

    // a strong reference, java.util.logging forgets the level of a logger nobody holds
    private final Logger logger;

    private final Level level;

    private Level previous;

    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            lines.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    LogLines(Class<?> type, Level level) {
        this.logger = Logger.getLogger(type.getName());
        this.level = level;
    }

    @Override
    protected void before() {
        previous = logger.getLevel();
        logger.setLevel(level);
        logger.addHandler(handler);
    }

    @Override
    protected void after() {
        logger.removeHandler(handler);
        logger.setLevel(previous);
    }
}
