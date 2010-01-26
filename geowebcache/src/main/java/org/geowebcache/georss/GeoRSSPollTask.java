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
 * @author Arne Kepp and Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.georss;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.updatesource.GeoRSSFeedDefinition;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.GWCTask;
import org.geowebcache.rest.seed.RasterMask;
import org.geowebcache.rest.seed.SeedRestlet;
import org.geowebcache.storage.DiscontinuousTileRange;

/**
 * A task to run a GeoRSS feed poll and launch the seeding process
 * 
 */
class GeoRSSPollTask implements Runnable {

    private static final Log logger = LogFactory.getLog(GeoRSSPollTask.class);

    private final PollDef poll;

    private final SeedRestlet seedRestlet;

    public GeoRSSPollTask(final PollDef poll, final SeedRestlet seedRestlet) {
        this.poll = poll;
        this.seedRestlet = seedRestlet;
    }

    /**
     * Called by the thread executor when the poll def's interval has elapsed (or as soon as
     * possible after it elapsed).
     */
    public void run() {
        /*
         * This method cannot throw an exception or the thread scheduler will discard the task.
         * Instead, if an error happens when polling we log the exception and hope for the next run
         * to work?
         */
        try {
            runPollAndLaunchSeed();
        } catch (Exception e) {
            logger.error("Error encountered trying to poll the GeoRSS feed "
                    + poll.getPollDef().getFeedUrl()
                    + ". Another attempt will be made after the poll interval of "
                    + poll.getPollDef().getPollIntervalStr(), e);
        } catch (OutOfMemoryError error) {
            System.gc();
            logger.fatal("Out of memory error processing poll " + poll.getPollDef()
                    + ". Need to reduce the maxMaskLevel param or increase system memory."
                    + " Poll disabled.", error);
            throw error;
        }
    }

    private void runPollAndLaunchSeed() throws IOException {
        final TileLayer layer = poll.getLayer();
        final GeoRSSFeedDefinition pollDef = poll.getPollDef();

        logger.info("Polling GeoRSS feed for layer " + layer.getName() + ": " + pollDef.toString());

        final String gridSetId = pollDef.getGridSetId();
        final URL feedUrl = new URL(pollDef.getFeedUrl());

        logger.debug("Getting GeoRSS reader for " + feedUrl.toExternalForm());
        final GeoRSSReaderFactory geoRSSReaderFactory = new GeoRSSReaderFactory();
        final GeoRSSReader geoRSSReader = geoRSSReaderFactory.createReader(feedUrl);

        logger.debug("Got reader for " + pollDef.getFeedUrl()
                + ". Creating geometry filter matrix for gridset " + gridSetId + " on layer "
                + layer.getName());

        final int maxMaskLevel = pollDef.getMaxMaskLevel();
        final GeoRSSTileRangeBuilder matrixBuilder = new GeoRSSTileRangeBuilder(layer, gridSetId,
                maxMaskLevel);

        logger.debug("Creating tile range mask based on GeoRSS feed's geometries from "
                + feedUrl.toExternalForm() + " for " + layer.getName());

        final TileGridFilterMatrix tileRangeMask = matrixBuilder.buildTileRangeMask(geoRSSReader);
        logger.debug("Created tile range mask based on GeoRSS geometry feed from " + pollDef
                + " for " + layer.getName() + ". Calculating number of affected tiles...");
        _logImagesToDisk(tileRangeMask);

        final boolean tilesAffected = tileRangeMask.hasTilesSet();
        if (tilesAffected) {
            logger.info("Launching reseed process " + pollDef + " for " + layer.getName());
        } else {
            logger.info(pollDef + " for " + layer.getName()
                    + " did not affect any tile. No need to reseed.");
            return;
        }

        launchSeeding(layer, pollDef, gridSetId, tileRangeMask);

        logger.info("Seeding process for tiles affected by feed " + feedUrl.toExternalForm()
                + " successfully launched.");
    }

    /**
     * For debug purposes only, writes down the bitmask images to the directory specified by the
     * System property (ej, {@code -Dorg.geowebcache.georss.debugToDisk=target/})
     * 
     * @param tileRangeMask
     */
    private void _logImagesToDisk(final TileGridFilterMatrix matrix) {
        if (null == System.getProperty("org.geowebcache.georss.debugToDisk")) {
            return;
        }
        File target = new File(System.getProperty("org.geowebcache.georss.debugToDisk"));
        if (!target.isDirectory() || !target.canWrite()) {
            throw new IllegalStateException("Can't access debug directory for "
                    + "dumping mask images: " + target.getAbsolutePath());
        }

        logger.warn("\n!!!!!!!!!!!\n REMEMBER NOT TO SET THE org.geowebcache.georss.debugToDisk"
                + " SYSTEM PROPERTY ON A PRODUCTION ENVIRONMENT \n!!!!!!!!!!!");
        BufferedImage[] byLevelMasks = matrix.getByLevelMasks();

        for (int i = 0; i < byLevelMasks.length; i++) {
            File output = new File(target, poll.getLayer().getName() + "_level_" + i + ".tiff");
            System.out.println("--- writing " + output.getAbsolutePath() + "---");
            try {
                ImageIO.write(byLevelMasks[i], "TIFF", output);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void launchSeeding(final TileLayer layer, final GeoRSSFeedDefinition pollDef,
            final String gridSetId, final TileGridFilterMatrix tileRangeMask) {
        GridSubset gridSub = layer.getGridSubset(gridSetId);

        final String mimeFormat = pollDef.getMimeFormat();

        MimeType mime = null;
        try {
            mime = MimeType.createFromFormat(mimeFormat);
        } catch (MimeException e) {
            logger.error("MimeType " + mimeFormat + " not recognized, "
                    + "aborting GeoRSS update! Check geowebcache.xml");
        }

        long[][] fullCoverage = gridSub.getCoverages();
        long[][] coveredBounds = tileRangeMask.getCoveredBounds();

        BufferedImage[] byLevelMasks = tileRangeMask.getByLevelMasks();
        
        RasterMask rasterMask = new RasterMask(byLevelMasks, fullCoverage, coveredBounds);

        DiscontinuousTileRange dtr = new DiscontinuousTileRange(layer.getName(), gridSetId, gridSub
                .getZoomStart(), gridSub.getZoomStop(), rasterMask, mime, null);

        GWCTask[] tasks = seedRestlet.createTasks(dtr, layer, GWCTask.TYPE.TRUNCATE, 1, false);

        // We do the truncate synchronously
        try {
            tasks[0].doAction();
        } catch (GeoWebCacheException e) {
            logger.error("Problem truncating based on GeoRSS feed: " + e.getMessage());
        }

        // Then we seed
        final int seedingThreads = pollDef.getSeedingThreads();
        tasks = seedRestlet.createTasks(dtr, layer, GWCTask.TYPE.SEED, seedingThreads, false);

        seedRestlet.dispatchTasks(tasks);
    }
}