/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nicola Lagomarsini (Geosolutions) 2014
 */
package org.geowebcache.util;

import static org.geowebcache.util.LogTestWrapper.message;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.geowebcache.util.LogTestWrapper.Level;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileUtilsTest {

    @Rule
    public LogTestWrapper log =
            LogTestWrapper.wrap(
                    () -> FileUtils.log,
                    (x) -> {
                        FileUtils.log = x;
                    },
                    Level.DEBUG);

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testFileRenaming() throws Exception {
        // Creation of a temporary file in the temporary directory directory
        File source = temp.newFile("source.txt");
        File destination = temp.newFile("dest" + System.currentTimeMillis() + ".txt");

        // File rename
        boolean renameFile = FileUtils.renameFile(source, destination);
        // File checks
        assertTrue("FileUtils.renameFile returned false", renameFile);
        assertThat(source, not(FileMatchers.exists()));
        assertThat(destination, FileMatchers.exists());
    }

    @Test
    public void testFileNotRenamed() throws Exception {
        // Creation of a temporary file in the temporary directory directory
        File source = temp.newFile("source.txt");
        File destination = temp.newFile("destination.txt");

        source.delete();
        // File rename
        boolean renameFile = FileUtils.renameFile(source, destination);

        // File checks
        assertFalse("FileUtils.renameFile returned true", renameFile);
        assertThat(log.getEntries(), hasItem(message(containsString("File.renameTo()"))));
    }
}
