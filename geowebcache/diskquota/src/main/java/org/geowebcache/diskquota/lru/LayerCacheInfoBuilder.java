package org.geowebcache.diskquota.lru;

import java.io.File;
import java.io.FileFilter;

import org.geowebcache.storage.blobstore.file.FilePathGenerator;

/**
 * Gathers information about the cache of a layer, such as its size and available {@link TilePage}s.
 * 
 * @author groldan
 */
class LayerCacheInfoBuilder {

    private static final class CacheVisitor implements FileFilter {

        private String gridSetId;

        private TilePageCalculator pages;

        public boolean accept(final File pathname) {
//            if (pathname.isDirectory()) {
//                return true;
//            }
//            System.currentTimeMillis()
//            pathname.getPath().endsWith("jpeg");
//            FilePathGenerator.
//            //long[] tileXYZ;
//            pages.pageFor(tileXYZ, gridSetId);

            return false;
        }

    }
}
