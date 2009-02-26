package org.geowebcache.storage;

import java.io.File;

import junit.framework.TestCase;

import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend;
import org.h2.tools.DeleteDbFiles;

public class StorageBrokerTest extends TestCase {
    public static final String TEST_DB_NAME = "gwcTestStorageBroker";
    
    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";
    
    public static final int THREAD_COUNT = 4;
    
    public static final int TILE_GET_COUNT = 10000;
    
    public static final int TILE_PUT_COUNT = 20000;
    
    public static final boolean RUN_PERFORMANCE_TESTS = false;
    
    public void testTileSingleThread() throws Exception {
        if(! RUN_PERFORMANCE_TESTS)
            return;
        
        StorageBroker sb = resetAndPrepBasicTestDb();
        
        for(int i=0;i<4; i++) {
            runBasicTileTest(sb, i, "Uni");
        }
    }
    
    public void testTileMultiThread() throws Exception {        
        if(! RUN_PERFORMANCE_TESTS)
            return;
        
        
        System.out.println("\n");
        StorageBroker sb = resetAndPrepBasicTestDb();
        
        int iterations = 4;
        
        long start = System.currentTimeMillis();
        Thread[] threadAr = new Thread[THREAD_COUNT];
        for(int i=0; i<THREAD_COUNT; i++) {
            threadAr[i] = new StorageBrokerTesterThread(sb,"Thread"+i, iterations);
        }
        for(int i=0; i<THREAD_COUNT; i++) {
            threadAr[i].start();
        }
        for(int i=0; i<THREAD_COUNT; i++) {
            threadAr[i].join();
        }
        long stop = System.currentTimeMillis();
        long totalTiles = (THREAD_COUNT*iterations*TILE_GET_COUNT);
        long diff = stop - start;
        long perSec = totalTiles*1000/diff;
        long bw = (20*1024*8*perSec)/1000000;
        
        System.out.println("Total time: " + diff + "ms for " 
                +  totalTiles + " tiles (" + perSec + " tiles/second, "
                + bw + " mbps with 20kbyte tiles) " + " fetched by " 
                + THREAD_COUNT + " threads in parallel" );
    }
    
    private StorageBroker resetAndPrepBasicTestDb() throws Exception {
        deleteDb(TEST_DB_NAME);
        
        MetaStore metaStore = new JDBCMetaBackend("org.h2.Driver", 
                "jdbc:h2:file:" + findTempDir() + File.separator +TEST_DB_NAME,
                "sa",
                "");

        String blobPath = findTempDir() + File.separator + TEST_BLOB_DIR_NAME;
        (new File(blobPath)).mkdirs();
        BlobStore blobStore = new FileBlobStore(blobPath);
        
        StorageBroker sb = new StorageBroker(metaStore, blobStore);
        
        //long[] xyz = {1L,2L,3L};
        byte[] blob = new byte[20*1024];

        
        long startInsert = System.currentTimeMillis();
        for(int i=1; i < TILE_PUT_COUNT; i++) {
            long tmp = (long) Math.log(i) + 1;
            long tmp2 = i % tmp; 
            long[] xyz = { tmp2, tmp2, (long) Math.log10(i)};
            TileObject completeObj = TileObject.createCompleteTileObject("test", xyz,900913, "image/jpeg", null, blob);    
            sb.put(completeObj);
        }
        long stopInsert = System.currentTimeMillis();
        
        System.out.println(TILE_PUT_COUNT+ " inserts took " + Long.toString(stopInsert - startInsert) + "ms");
        
        return sb;
    }
    
    public static void deleteDb(String db_name) throws Exception {
        DeleteDbFiles.execute(findTempDir(), db_name, true);
        // Should clear out blobs too
    }
    
    public static String findTempDir() throws Exception {
        String tmpDir = System.getProperty("java.io.tmpdir");
        if(tmpDir == null || ! (new File(tmpDir)).canWrite()) {
            throw new Exception("Temporary directory " 
                    + tmpDir + " does not exist or is not writable.");
        }
        return tmpDir;
    }
    
    private void runBasicTileTest(StorageBroker sb, int run, String name) throws StorageException {
        long start = System.currentTimeMillis();
        for(int i=1; i<TILE_GET_COUNT; i++) {
            long tmp = (long) Math.log(i) + 1;
            long tmp2 = i % tmp; 
            long[] xyz = { tmp2, tmp2, (long) Math.log10(i)};
            TileObject queryObj2 = TileObject.createQueryTileObject("test", xyz,900913, "image/jpeg", null);
            sb.get(queryObj2);
        }
        
        long stop = System.currentTimeMillis();
        
        System.out.println(name + " - run "+run+", "
                +TILE_GET_COUNT+" gets took " + Long.toString(stop - start) + "ms");
    }
    
    public class StorageBrokerTesterThread extends Thread {
        StorageBroker sb = null;
        String fail = null;
        String name = null;
        int iterations;
        
        public StorageBrokerTesterThread(StorageBroker sb, String name, int iterations) {
            this.sb = sb;
            this.name = name;
            this.iterations = iterations;
        }
        
        public void run() {
            try {
                for(int i=0;i<iterations; i++) {
                    runBasicTileTest(sb, i, name);
                }
            } catch (Exception e) {
                fail = e.getMessage();
            }
        }
    }

}
