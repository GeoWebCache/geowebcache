/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Nicola Lagomarsini (Geosolutions) 2014
 *  
 */
package org.geowebcache.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileUtilsTest {

    private MockAppender newAppender;

    private Level level;

    @Test
    public void testFileRenaming() throws Exception {
        // Creation of a temporary file in the temporary directory directory
        File source = File.createTempFile("source", ".txt");
        File destination = new File(source.getParent(), "dest" + System.currentTimeMillis()
                + ".txt");// File.createTempFile("destination", ".txt");

        // File rename
        boolean renameFile = FileUtils.renameFile(source, destination);
        // File checks
        assertTrue(renameFile);
        assertFalse(source.exists());
        assertTrue(destination.exists());

        // Remove the created files
        source.delete();
        destination.delete();
    }

    @Before
    public void onBefore() {
        // Configuring logging
        newAppender = new MockAppender();
        Logger.getRootLogger().addAppender(newAppender);
        level = Logger.getRootLogger().getLevel();
        Logger.getRootLogger().setLevel(Level.DEBUG);
    }

    @After
    public void after() {
        // Configuring logging to default setup
        Logger.getRootLogger().setLevel(level);
    }

    @Test
    public void testFileNotRenamed() throws Exception {
        // Creation of a temporary file in the temporary directory directory
        File source = File.createTempFile("source", ".txt");
        File destination = File.createTempFile("destination", ".txt");
        source.delete();
        // File rename
        boolean renameFile = FileUtils.renameFile(source, destination);

        // File checks
        assertFalse(renameFile);
        assertTrue(newAppender.isMessageLogged());

        // Remove the created files
        source.delete();
        destination.delete();
    }

    /**
     * Mock class for {@link Appender} interface used for keeping track of the message logged by the {@link FileUtils} class when an Exception has
     * been thrown.
     * 
     * 
     * @author Nicola Lagomarsini Geosolutions
     */
    static class MockAppender implements Appender {

        /** Parameter indicating that a message has been launched */
        private boolean messageLogged = false;

        /**
         * Method indicating if the Info Message has been logged
         * 
         * @return a boolean indicating that the message has been logged
         */
        public boolean isMessageLogged() {
            return messageLogged;
        }

        @Override
        public void addFilter(Filter newFilter) {
        }

        @Override
        public Filter getFilter() {
            return null;
        }

        @Override
        public void clearFilters() {
        }

        @Override
        public void close() {
        }

        @Override
        public void doAppend(LoggingEvent event) {
            if (event.getLevel().equals(Level.DEBUG)) {
                messageLogged = true;
            }
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public void setErrorHandler(ErrorHandler errorHandler) {
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return null;
        }

        @Override
        public void setLayout(Layout layout) {
        }

        @Override
        public Layout getLayout() {
            return null;
        }

        @Override
        public void setName(String name) {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
