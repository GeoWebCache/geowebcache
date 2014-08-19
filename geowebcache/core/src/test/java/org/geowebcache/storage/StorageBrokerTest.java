package org.geowebcache.storage;

import java.io.File;

import junit.framework.TestCase;

import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.blobstore.file.FileBlobStore;

public class StorageBrokerTest extends TestCase {
    public static final String TEST_DB_NAME = "gwcTestStorageBroker";
    
    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";
    
    public static final int THREAD_COUNT = 4;
    
    public static final long REPEAT_COUNT = 10;
    
    public static final long TILE_GET_COUNT = 20000;
    
    public static final long TILE_PUT_COUNT = 30000;
    
    public static final boolean RUN_PERFORMANCE_TESTS = false;
    
    public void testTileSingleThread() throws Exception {
        if(! RUN_PERFORMANCE_TESTS)
            return;
        
        StorageBroker sb = resetAndPrepStorageBroker();
        
        for(int i=0;i<REPEAT_COUNT; i++) {
            runBasicTileTest(sb, i, "Uni");
        }
    }
    
    public void testTileMultiThread() throws Exception {        
        if(! RUN_PERFORMANCE_TESTS)
            return;
        
        System.out.println("\n");
        StorageBroker sb = resetAndPrepStorageBroker();
        
        long iterations = REPEAT_COUNT;
        
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
        long totalTiles = THREAD_COUNT*iterations*TILE_GET_COUNT;
        long diff = stop - start;
        long perSec = totalTiles*1000/diff;
        long bw = (20*1024*8*perSec)/1000000;
        
        System.out.println("Total time: " + diff + "ms for " 
                +  totalTiles + " tiles (" + perSec + " tiles/second, "
                + bw + " mbps with 20kbyte tiles) " + " fetched by " 
                + THREAD_COUNT + " threads in parallel" );
    }
    
    private StorageBroker resetAndPrepStorageBroker() throws Exception {
        System.out.println("Deleting old test database.");

        String blobPath = findTempDir() + File.separator + TEST_BLOB_DIR_NAME;
        System.out.println("Creating new blobstore in " + blobPath);
        
        File blobDirs = new File(blobPath);
        if(! blobDirs.exists() && ! blobDirs.mkdirs()) {
            throw new StorageException("Unable to create " + blobPath);
        }
        
        BlobStore blobStore = new FileBlobStore(blobPath);
        TransientCache transCache = new TransientCache(100, 1024, 2000);
        
        StorageBroker sb = new DefaultStorageBroker(blobStore);
        
        //long[] xyz = {1L,2L,3L};
        Resource blob = new ByteArrayResource(new byte[20*1024]);

        System.out.println("Inserting into database, " + TILE_PUT_COUNT + " tiles");
        
        long startInsert = System.currentTimeMillis();
        for(int i=1; i < TILE_PUT_COUNT; i++) {
            long tmp = (long) Math.log(i) + 1;
            long tmp2 = i % tmp; 
            long[] xyz = { tmp2, tmp2, (long) Math.log10(i)};
            TileObject completeObj = TileObject.createCompleteTileObject("test", xyz,"hefty-gridSet:id1", "image/jpeg", null, blob);    
            sb.put(completeObj);
        }
        long stopInsert = System.currentTimeMillis();
        
        System.out.println(TILE_PUT_COUNT+ " inserts took " + Long.toString(stopInsert - startInsert) + "ms");
        
        return sb;
    }
    
    public static String findTempDir() throws Exception {
        String tmpDir = System.getProperty("java.io.tmpdir");
        if(tmpDir == null || ! (new File(tmpDir)).canWrite()) {
            throw new Exception("Temporary directory " 
                    + tmpDir + " does not exist or is not writable.");
        }
        return tmpDir;
    }
    
    private void runBasicTileTest(StorageBroker sb, long run, String name) throws StorageException {
        long start = System.currentTimeMillis();
        for(int i=1; i<TILE_GET_COUNT; i++) {
            long tmp = (long) Math.log(i) + 1;
            long tmp2 = i % tmp; 
            long[] xyz = { tmp2, tmp2, (long) Math.log10(i)};
            TileObject queryObj2 = TileObject.createQueryTileObject("test", xyz,"hefty-gridSet:id1", "image/jpeg", null);
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
        long iterations;
        
        public StorageBrokerTesterThread(StorageBroker sb, String name, long iterations) {
            this.sb = sb;
            this.name = name;
            this.iterations = iterations;
        }
        
        public void run() {
            try {
                for(long i=0;i<iterations; i++) {
                    runBasicTileTest(sb, i, name);
                }
            } catch (Exception e) {
                fail = e.getMessage();
            }
        }
    }

}
