/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.apache.commons.io.FileUtils;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class with some utilities methods for using during the tests.
 */
public abstract class TestSupport {

    private File rootDirectory;

    private List<File> filesToDelete;
    private List<MbtilesBlobStore> storesToClean;

    @Before
    public void beforeTest() throws Exception {
        rootDirectory = Files.createTempDirectory("gwc-").toFile();
        filesToDelete = new ArrayList<>();
        filesToDelete.add(rootDirectory);
        storesToClean = new ArrayList<>();
    }

    @After
    public void afterTest() throws Exception {
        // let's first make sure all the stores connection were removed
        for (MbtilesBlobStore store: storesToClean) {
            try {
                store.destroy();
            } catch (Exception exception) {
                // there is nothing we can do
            }
        }
        // deleting all the files to delete
        for (File file : filesToDelete) {
            FileUtils.deleteQuietly(file);
        }
    }

    protected File getRootDirectory() {
        return rootDirectory;
    }

    protected String getRootDirectoryPath() {
        try {
            return rootDirectory.getCanonicalPath();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error getting root directory canonical path.");
        }
    }

    protected File createFileInRootDir(String path) throws Exception {
        File file = new File(rootDirectory, path);
        if (file.exists()) {
            return null;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        file.createNewFile();
        return file;
    }

    protected File buildRootFile(String... pathParts) {
        return new File(rootDirectory, Utils.buildPath(pathParts));
    }

    protected File buildFile(String... pathParts) {
        return new File(Utils.buildPath(pathParts));
    }

    protected void writeToFile(File file, String content) {
        Utils.createFileParents(file);
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.flush();
            writer.close();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error creating or writing content to file '%s'.", file);
        }
    }

    protected MbtilesInfo getDefaultConfiguration() {
        MbtilesInfo configuration = new MbtilesInfo();
        configuration.setPoolSize(1000);
        configuration.setRootDirectory(getRootDirectory().getPath());
        configuration.setTemplatePath(Utils.buildPath("{grid}", "{layer}", "{format}", "{z}", "tiles-{x}-{y}.sqlite"));
        configuration.setRowRangeCount(500);
        configuration.setColumnRangeCount(500);
        return configuration;
    }

    protected static Resource stringToResource(String string) {
        return new ByteArrayResource(string.getBytes());
    }

    protected static String resourceToString(Resource resource) {
        return new String(Utils.resourceToByteArray(resource));
    }

    protected void addStoresToClean(MbtilesBlobStore ... stores) {
        for (MbtilesBlobStore store : stores) {
            storesToClean.add(store);
        }
    }

    protected void addFilesToDelete(File ... files) {
        for (File file : files) {
            filesToDelete.add(file);
        }
    }
}