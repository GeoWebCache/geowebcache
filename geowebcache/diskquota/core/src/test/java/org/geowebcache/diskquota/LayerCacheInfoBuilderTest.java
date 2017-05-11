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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import junit.framework.TestCase;

import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;

public class LayerCacheInfoBuilderTest extends TestCase {

    private LayerCacheInfoBuilder infoBuilder;

    private final int blockSize = 2048;

    private File rootCacheDir;

    private ExecutorService threadPool;
    
    FilePathGenerator pathGenerator = new FilePathGenerator("") {
        protected String getParametersId(String base, java.util.Map<String,String> parameters) throws IOException {
            // we assume no collisions for these tests
            String parametersKvp = ParametersUtils.getKvp(parameters);
            return ParametersUtils.buildKey(parametersKvp);
        };
    };

    public void testFake() {

    }

    /*
     * TODO fix tests /*
     * 
     * @Override protected void setUp() throws Exception { File target = new File("target"); if
     * (!target.exists() || !target.isDirectory() || !target.canWrite()) { throw new
     * IllegalStateException("Can't set up tests, " + target.getAbsolutePath() +
     * " is not a writable directory"); }
     * 
     * rootCacheDir = new File(target, getClass().getSimpleName());
     * FileUtils.rmFileCacheDir(rootCacheDir, null); rootCacheDir.mkdirs();
     * 
     * threadPool = Executors.newSingleThreadExecutor();
     * 
     * infoBuilder = new LayerCacheInfoBuilder(rootCacheDir, threadPool); }
     * 
     * @Override protected void tearDown() throws Exception { if (threadPool != null) {
     * threadPool.shutdownNow(); } if (rootCacheDir != null) {
     * FileUtils.rmFileCacheDir(rootCacheDir, null); } }
     * 
     * public void testBuildCacheInfo() throws MimeException, IOException, InterruptedException {
     * 
     * final String layerName = "MockLayer"; TileLayer mockLayer =
     * EasyMock.createMock(TileLayer.class);
     * EasyMock.expect(mockLayer.getName()).andReturn(layerName).anyTimes(); GridSet gridSet = new
     * GridSetBroker(false, false).WORLD_EPSG4326; GridSubset gridSubset =
     * GridSubsetFactory.createGridSubSet(gridSet); EasyMock.expect(mockLayer.getGridSubsets())
     * .andReturn( new Hashtable<String, GridSubset>(Collections.singletonMap( gridSubset.getName(),
     * gridSubset))).anyTimes(); EasyMock.replay(mockLayer);
     * 
     * final String gridSetId = gridSubset.getName(); final LayerQuota layerQuota = new
     * LayerQuota(layerName, "MockPolicy"); final int numFiles = 10; final int fileSize =
     * this.blockSize + 1;
     * 
     * ExpirationPolicy mockPolicy = EasyMock.createMock(ExpirationPolicy.class);
     * 
     * mockPolicy.createTileInfo(EasyMock.eq(layerQuota), EasyMock.eq(gridSetId),
     * EasyMock.anyLong(), EasyMock.anyLong(), EasyMock.anyInt());
     * EasyMock.expectLastCall().times(numFiles); EasyMock.replay(mockPolicy);
     * 
     * mockSeed(mockLayer, numFiles, fileSize);
     * 
     * layerQuota.setExpirationPolicy(mockPolicy);
     * 
     * infoBuilder.buildCacheInfo(mockLayer, layerQuota);
     * 
     * // be careful and don't wait more than 30s long startTime = System.currentTimeMillis(); long
     * ellapsedTime = 0; while (infoBuilder.isRunning(layerName)) { Thread.sleep(500); ellapsedTime
     * = System.currentTimeMillis() - startTime; if (ellapsedTime > 30000) {
     * fail(LayerCacheInfoBuilder.class.getSimpleName() +
     * ".buildCacheInfo was running for too long, aborting test!"); } } EasyMock.verify(mockLayer);
     * EasyMock.verify(mockPolicy);
     * 
     * // was layer used quota updated? final int blockFileSize = (int) Math.ceil((double) fileSize
     * / this.blockSize) this.blockSize; final long expectedCacheSize = numFiles * blockFileSize;
     * 
     * Quota expectedUsedQuota = new Quota(expectedCacheSize, StorageUnit.B);
     * 
     * Quota usedQuota = layerQuota.getUsedQuota();
     * 
     * assertTrue(usedQuota.getBytes().longValue() > 0);
     * 
     * assertEquals(0L, usedQuota.difference(expectedUsedQuota).getBytes().longValue()); }
     */
    /**
     * Seeds {@code numFiles} fake tiles of {@code fileSize} each at random tile indices
     * 
     * @param layer
     * @param numFiles
     * @throws MimeException
     * @throws IOException
     */
    private void mockSeed(TileLayer layer, int numFiles, int fileSize) throws MimeException,
            IOException {
        final String layerName = layer.getName();

        final GridSubset gridSubset = layer.getGridSubset(layer.getGridSubsets().iterator().next());
        final String gridSetId = gridSubset.getName();
        final String prefix = this.rootCacheDir.getAbsolutePath();
        String format = "image/png";
        final MimeType mimeType = MimeType.createFromFormat(format);

        final byte[] mockTileContents = new byte[fileSize];
        Arrays.fill(mockTileContents, (byte) 0xFF);

        // just to control the same tile is not created more than once
        Set<String> addedTiles = new HashSet<String>();

        long[] tileIndex;
        while (addedTiles.size() < numFiles) {
            int level = (int) (gridSubset.getZoomStart() + ((gridSubset.getZoomStop() - gridSubset
                    .getZoomStart()) * Math.random()));

            String tileKey = null;
            File tilePath;
            do {
                long[] coverage = gridSubset.getCoverage(level);// {minx,miny,maxx,maxy,z}
                long x = (long) (coverage[0] + ((coverage[2] - coverage[0]) * Math.random()));
                long y = (long) (coverage[1] + ((coverage[3] - coverage[1]) * Math.random()));
                tileIndex = new long[] { x, y, level };
                TileObject tile = TileObject.createCompleteTileObject(layerName, tileIndex, gridSetId, format, null, null);
                tilePath = pathGenerator.tilePath(tile, mimeType);
                tileKey = tilePath.getAbsolutePath();
            } while (addedTiles.contains(tileKey));
            addedTiles.add(tileKey);

            File tileDir = tilePath.getParentFile();
            tileDir.mkdirs();
            FileOutputStream fout = new FileOutputStream(tilePath);
            try {
                fout.write(mockTileContents);
            } finally {
                fout.close();
            }
        }

    }
}
