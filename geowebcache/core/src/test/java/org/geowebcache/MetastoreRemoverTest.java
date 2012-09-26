package org.geowebcache;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.MetastoreRemover;
import org.geowebcache.util.ApplicationContextProvider;

import junit.framework.TestCase;

public class MetastoreRemoverTest extends TestCase {

    File root;

    @Override
    protected void setUp() throws Exception {
        // copy over the data we'll use for the migration test
        root = new File("./target/migration");
        File origin = new File("./src/test/resources/org/geowebcache/ms_removal");
        FileUtils.deleteDirectory(root);
        FileUtils.copyDirectory(origin, root);
    }

    public void testMigration() throws Exception {
        // the remover does the migration on instantiation
        MetastoreRemover remover = new MetastoreRemover(new DefaultStorageFinder(new ApplicationContextProvider()) {
            @Override
            public synchronized String getDefaultPath() throws ConfigurationException {
                return root.toString();
            }
        });

        // the first param has been removed and replaced, the file last modified date has been replaced
        File original1 = new File(root, "topp_states/EPSG_4326_03_1/0_1/02_05.png8");
        File transformed1 = new File(root, "topp_states/EPSG_4326_03_7510004a12f49fdd49a2ba366e9c4594be7e4358/0_1/02_05.png8");
        assertFalse(original1.exists());
        assertTrue(transformed1.exists());
        assertEquals(1348596068000l, transformed1.lastModified());
        
        // same goes for the second param
        File original2 = new File(root, "topp_states/EPSG_4326_03_2/0_1/02_05.png8");
        File transformed2 = new File(root, "topp_states/EPSG_4326_03_f0023dc7bc347fee7a3a04dc797f2223f74e3448/0_1/02_05.png8");
        assertFalse(original2.exists());
        assertTrue(transformed2.exists());
        assertEquals(1348595993000l, transformed2.lastModified());
        
        // let's also check a file that did not have the parameter transformed
        File untransformed = new File(root, "raster_test_layer/EPSG_900913_01/0_0/00_01.jpeg");
        assertEquals(1348595928000l, untransformed.lastModified());
    }

}
