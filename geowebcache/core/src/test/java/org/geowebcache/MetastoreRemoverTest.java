package org.geowebcache;

import java.io.File;
import java.util.Iterator;
import org.apache.commons.io.FileUtils;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.MetastoreRemover;
import org.geowebcache.util.ApplicationContextProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MetastoreRemoverTest {

    static final long FIXED_DATE = 1348596000000l;

    File root;

    @Before
    public void setUp() throws Exception {
        // copy over the data we'll use for the migration test
        root = new File("./target/migration");
        File origin = new File("./src/test/resources/org/geowebcache/ms_removal");
        FileUtils.deleteDirectory(root);
        FileUtils.copyDirectory(origin, root);
        // force all files to a specific date for testing sake
        Iterator<File> it = FileUtils.iterateFiles(root, new String[] {"png8", "png", "jpeg"}, true);
        while (it.hasNext()) {
            File file = it.next();
            file.setLastModified(FIXED_DATE);
        }
    }

    @Test
    public void testMigrationNoDates() throws Exception {
        testMigration(false);
    }

    @Test
    public void testMigrationWithDates() throws Exception {
        testMigration(true);
    }

    protected void testMigration(boolean migrateCreationDates) throws Exception {
        System.setProperty("MIGRATE_CREATION_DATES", String.valueOf(migrateCreationDates));
        // the remover does the migration on instantiation
        new MetastoreRemover(new DefaultStorageFinder(new ApplicationContextProvider()) {
            @Override
            public synchronized String getDefaultPath() throws ConfigurationException {
                return root.toString();
            }
        });

        // the first param has been removed and replaced, the file last modified date has been
        // replaced
        File original1 = new File(root, "topp_states/EPSG_4326_03_1/0_1/02_05.png8");
        File transformed1 =
                new File(root, "topp_states/EPSG_4326_03_7510004a12f49fdd49a2ba366e9c4594be7e4358/0_1/02_05.png8");
        Assert.assertFalse(original1.exists());
        Assert.assertTrue(transformed1.exists());
        if (migrateCreationDates) {
            Assert.assertTrue(Math.abs(transformed1.lastModified() - 1348596068000d) < 1000);
        } else {
            Assert.assertEquals(FIXED_DATE, transformed1.lastModified());
        }

        // same goes for the second param
        File original2 = new File(root, "topp_states/EPSG_4326_03_2/0_1/02_05.png8");
        File transformed2 =
                new File(root, "topp_states/EPSG_4326_03_f0023dc7bc347fee7a3a04dc797f2223f74e3448/0_1/02_05.png8");
        Assert.assertFalse(original2.exists());
        Assert.assertTrue(transformed2.exists());
        if (migrateCreationDates) {
            Assert.assertTrue(Math.abs(transformed2.lastModified() - 1348595993000l) < 1000);
        } else {
            Assert.assertEquals(FIXED_DATE, transformed1.lastModified());
        }

        // let's also check a file that did not have the parameter transformed
        File untransformed = new File(root, "raster_test_layer/EPSG_900913_01/0_0/00_01.jpeg");
        if (migrateCreationDates) {
            Assert.assertTrue(Math.abs(untransformed.lastModified() - 1348595928000l) < 1000);
        } else {
            Assert.assertEquals(FIXED_DATE, transformed1.lastModified());
        }
    }
}
