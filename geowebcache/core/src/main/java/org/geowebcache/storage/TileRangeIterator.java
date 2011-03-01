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
 * @author Arne OpenGeo 2010
 */

package org.geowebcache.storage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.util.Assert;

public class TileRangeIterator {
    private static final ExecutorService QUEUE_FILLER = Executors.newCachedThreadPool();

    final private TileRange tr;

    final private DiscontinuousTileRange dtr;

    final private int metaX;

    final private int metaY;

    private AtomicLong tilesSkippedCount = new AtomicLong();

    private AtomicLong tilesRenderedCount = new AtomicLong();

    private long numTilesProcessed;

    private volatile long[] lastGridLoc;

    private final boolean newStyle;

    BlockingQueue<long[]> queue =  new LinkedBlockingQueue<long[]>(10000);//new SynchronousQueue<long[]>();

    private volatile boolean done;

    /**
     * Note that the bounds of the tile range must already be expanded to the meta tile factors for
     * this to work.
     * 
     * @param tr
     * @param metaTilingFactors
     */
    public TileRangeIterator(TileRange tr, int[] metaTilingFactors) {
        this(tr, metaTilingFactors, true);
    }

    public TileRangeIterator(final TileRange tr, int[] metaTilingFactors, boolean newStyle) {
        this.tr = tr;
        this.metaX = metaTilingFactors[0];
        this.metaY = metaTilingFactors[1];
        this.newStyle = newStyle;

        if (tr instanceof DiscontinuousTileRange) {
            dtr = (DiscontinuousTileRange) tr;
        } else {
            dtr = null;
        }

        if (newStyle) {
            lastGridLoc = new long[3];
            lastGridLoc[2] = tr.zoomStart;
            long[] levelBounds = tr.rangeBounds[(int) lastGridLoc[2]];
            lastGridLoc[0] = levelBounds[0] - metaX;// initialize X to the previous position, it
                                                    // doesn't
            // matter if it's -1
            lastGridLoc[1] = levelBounds[1];

            QUEUE_FILLER.execute(new Runnable() {

                public void run() {
                    try {
                        fillTileLocQueue();
                    } finally {
                        done = true;
                        System.err.println("Done adding tiles to queue");
                    }
                }

                private void fillTileLocQueue() {
                    long[] levelBounds;
                    long x;
                    long y;
                    int z;

                    // Figure out the starting point
                    z = tr.zoomStart;
                    levelBounds = tr.rangeBounds[z];
                    x = levelBounds[0];
                    y = levelBounds[1];
                    // Loop over any remaining zoom levels
                    for (; z <= tr.zoomStop; z++) {
                        for (; y <= levelBounds[3]; y += metaY) {
                            for (; x <= levelBounds[2]; x += metaX) {

                                long[] gridLoc = { x, y, z };

                                // int tileCount = tilesForLocation(gridLoc, levelBounds);

                                if (checkGridLocation(gridLoc)) {
                                    // tilesRenderedCount.addAndGet(tileCount);
                                    lastGridLoc = gridLoc.clone();
                                    try {
                                        queue.put(gridLoc);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException();
                                    }
                                } else {
                                    // tilesSkippedCount.addAndGet(tileCount);
                                }
                            }
                            x = levelBounds[0];
                        }

                        // Get ready for the next level
                        if (z < tr.zoomStop) {// but be careful not to go out of index
                            levelBounds = tr.rangeBounds[z + 1];
                            x = levelBounds[0];
                            y = levelBounds[1];
                        }
                    }
                }
            });
        }

    }

    /**
     * Returns the underlying tile range
     * 
     * @return
     */
    public TileRange getTileRange() {
        return tr;
    }

    /**
     * This loops over all the possible tile locations.
     * 
     * If the TileRange object provided is a DiscontinuousTileRange implementation, each location is
     * checked against the filter of that class.
     * 
     * @return {@code null} if there're no more tiles to return, the next grid location in the
     *         iterator otherwise
     */
    public long[] nextMetaGridLocation(long gridLoc[]) {
        if (newStyle) {
            return nextMetaGridLocation3(gridLoc);
        }
        return nextMetaGridLocation();
    }

    public synchronized long[] nextMetaGridLocation() {
        long[] levelBounds;
        long x;
        long y;
        int z;

        // Figure out the starting point
        if (lastGridLoc == null) {
            z = tr.zoomStart;
            levelBounds = tr.rangeBounds[z];
            x = levelBounds[0];
            y = levelBounds[1];

        } else {
            z = (int) lastGridLoc[2];
            levelBounds = tr.rangeBounds[z];
            x = lastGridLoc[0] + metaX;
            y = lastGridLoc[1];
        }

        // Loop over any remaining zoom levels
        for (; z <= tr.zoomStop; z++) {
            for (; y <= levelBounds[3]; y += metaY) {
                for (; x <= levelBounds[2]; x += metaX) {

                    long[] gridLoc = { x, y, z };

                    int tileCount = tilesForLocation(gridLoc, levelBounds);

                    if (checkGridLocation(gridLoc)) {
                        tilesRenderedCount.addAndGet(tileCount);
                        lastGridLoc = gridLoc.clone();
                        return gridLoc;
                    }

                    tilesSkippedCount.addAndGet(tileCount);
                }
                x = levelBounds[0];
            }

            // Get ready for the next level
            if (z < tr.zoomStop) {// but be careful not to go out of index
                levelBounds = tr.rangeBounds[z + 1];
                x = levelBounds[0];
                y = levelBounds[1];
            }
        }

        return null;
    }

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * This is a proposal replacement for nextMetaGridLocation() that eliminates it as a
     * synchronization bottleneck (since Yourkit Java Profiler) reveals 35% of the time threads are
     * blocking on that method (with 16 threads running). This new version uses a Lock to serialize
     * write access to this.lastGridLock and makes it dissapear from Yourkit's offenders for blocked
     * threads, which is also notable by the increase of the CPU usage which is otherwise quite idle
     * (taking it to ~70% usage from the previous ~25%)
     */
    public final long[] nextMetaGridLocation2(long[] gridLoc) {
        Assert.notNull(gridLoc, "grid location can't be null");

        long[] levelBounds;
        long x;
        long y;
        int z;
        // Figure out the starting point
        lock.readLock().lock();
        long tilesProcessed;
        try {
            tilesProcessed = numTilesProcessed;
            z = (int) lastGridLoc[2];
            x = lastGridLoc[0] + metaX;
            y = lastGridLoc[1];
        } finally {
            lock.readLock().unlock();
        }
        levelBounds = tr.rangeBounds[z];

        // Loop over any remaining zoom levels
        for (; z <= tr.zoomStop; z++) {
            for (; y <= levelBounds[3]; y += metaY) {
                for (; x <= levelBounds[2]; x += metaX) {

                    gridLoc[0] = x;
                    gridLoc[1] = y;
                    gridLoc[2] = z;
                    // /int tileCount = tilesForLocation(x, y, levelBounds);

                    if (checkGridLocation(gridLoc)) {
                        if (tilesProcessed == numTilesProcessed) {
                            lock.writeLock().lock();
                            try {
                                if (tilesProcessed == numTilesProcessed) {
                                    tilesProcessed = ++numTilesProcessed;
                                    lastGridLoc[0] = x;
                                    lastGridLoc[1] = y;
                                    lastGridLoc[2] = z;
                                    // tilesRenderedCount.addAndGet(tileCount);
                                    return gridLoc;
                                } else {
                                    tilesProcessed = numTilesProcessed;
                                    // x = lastGridLoc[0];
                                    // y = lastGridLoc[1];
                                    // z = (int) lastGridLoc[2];
                                    // levelBounds = tr.rangeBounds[z];
                                }
                            } finally {
                                lock.writeLock().unlock();
                            }
                        }
                    }
                }
                x = levelBounds[0];
            }

            // Get ready for the next level
            if (z < tr.zoomStop) {// but be careful not to go out of index
                levelBounds = tr.rangeBounds[z + 1];
                x = levelBounds[0];
                y = levelBounds[1];
            }
        }

        return null;
    }

    public final long[] nextMetaGridLocation3(long[] gridLoc) {
        int size = queue.size();
        boolean queueDone = done;
        try {
            if (size > 0 || (size == 0 && !queueDone)) {
                return queue.poll(100, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            if (!queueDone) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * Calculates the number of tiles covered by the meta tile for this grid location.
     * 
     * @param gridLoc
     * @param levelBounds
     * @return
     */
    private int tilesForLocation(long x, long y, long[] levelBounds) {
        long boundsMaxX = levelBounds[2];
        long boundsMaxY = levelBounds[3];
        return (int) Math.min(metaX, 1 + (boundsMaxX - x))
                * (int) Math.min(metaY, 1 + (boundsMaxY - y));
    }

    private int tilesForLocation(long[] gridLoc, long[] levelBounds) {
        return tilesForLocation(gridLoc[0], gridLoc[1], levelBounds);
    }

    /**
     * Checks whether this grid location, or any on the same meta tile, should be included according
     * to the DiscontinuousTileRange
     * 
     * @param gridLoc
     * @return
     */
    private boolean checkGridLocation(long[] gridLoc) {
        if (dtr == null) {
            return true;
        } else {
            long[] subIdx = new long[3];
            subIdx[2] = gridLoc[2];
            for (int i = 0; i < this.metaX; i++) {
                for (int j = 0; j < this.metaY; j++) {
                    subIdx[0] = gridLoc[0] + i;
                    subIdx[1] = gridLoc[1] + j;
                    if (dtr.contains(subIdx)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
