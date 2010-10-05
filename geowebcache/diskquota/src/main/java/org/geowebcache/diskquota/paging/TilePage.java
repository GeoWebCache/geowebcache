package org.geowebcache.diskquota.paging;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class TilePage implements Serializable {

    /**
     * Do not change if don't know what you're doing. Used in the custom
     * {@link #writeObject(java.io.ObjectOutputStream)} and
     * {@link #readObject(java.io.ObjectInputStream)} methods
     */
    private static final long serialVersionUID = 1L;

    /**
     * Current time with near-minute precision, meaning we can hold values up till year 2115
     */
    private static final AtomicInteger currentTime = new AtomicInteger(
            (int) (System.currentTimeMillis() / 1000L / 60L));
    static {
        CustomizableThreadFactory tf = new CustomizableThreadFactory("Minute-Timer");
        tf.setDaemon(true);
        tf.setThreadPriority((Thread.MAX_PRIORITY - Thread.MIN_PRIORITY) / 5);
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, tf);
        Runnable command = new Runnable() {
            public void run() {
                int minutesSinceJan1_1970 = (int) (System.currentTimeMillis() / 1000L / 60L);
                TilePage.currentTime.set(minutesSinceJan1_1970);
            }
        };
        executorService.scheduleAtFixedRate(command, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * x, y, z index for this page, in reverse order (z, y, x) to aid in a more efficient short
     * circuit comparison at {@link #equals(Object)}
     */
    private int[] zyxIndex;

    private AtomicLong numHits;

    private AtomicLong numTilesInPage;

    private transient int hashCode;

    /**
     * Last access time, with near-minute precision
     */
    private int accessTimeMinutes;

    public TilePage(final int x, final int y, final int z) {
        this(x, y, z, 0L, 0L, 0);
    }

    public TilePage(final int x, final int y, final int z, final long numHits,
            final long numTilesInPage, final int lastAccessTimeMinutes) {

        this.zyxIndex = new int[] { z, y, x };
        this.numHits = new AtomicLong(numHits);
        this.accessTimeMinutes = lastAccessTimeMinutes;
        this.numTilesInPage = new AtomicLong(numTilesInPage);

        this.hashCode = 17 * (zyxIndex[0] + zyxIndex[1] ^ 2 + zyxIndex[2] ^ 3);
    }

    public void markHit() {
        numHits.addAndGet(1L);
        accessTimeMinutes = currentTime.get();
    }

    public long getNumTilesInPage() {
        return this.numTilesInPage.get();
    }

    public long getNumHits() {
        long hits = numHits.get();
        return hits;
    }

    public int getLastAccessTimeMinutes() {
        return accessTimeMinutes;
    }

    public int getX() {
        return zyxIndex[2];
    }

    public int getY() {
        return zyxIndex[1];
    }

    public int getZ() {
        return zyxIndex[0];
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TilePage)) {
            return false;
        }
        return Arrays.equals(zyxIndex, ((TilePage) o).zyxIndex);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append("[")
                .append(zyxIndex[2]).append(',').append(zyxIndex[1]).append(',')
                .append(zyxIndex[0]).append(". Hits: ").append(getNumHits()).append(". Access: ");
        if (accessTimeMinutes == 0) {
            sb.append("never");
        } else {
            sb.append(currentTime.get() - accessTimeMinutes).append("m ago.");
        }
        sb.append(']');
        return sb.toString();
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt((int) TilePage.serialVersionUID);
        out.writeInt(zyxIndex[2]);
        out.writeInt(zyxIndex[1]);
        out.writeInt(zyxIndex[0]);
        out.writeInt(accessTimeMinutes);
        out.writeLong(numHits.get());
        out.writeLong(numTilesInPage.get());
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        long magic = in.readInt();
        if (TilePage.serialVersionUID != magic) {
            throw new IOException("Object stream does not start with TilePage magic number: "
                    + magic);
        }
        zyxIndex = new int[3];
        zyxIndex[2] = in.readInt();
        zyxIndex[1] = in.readInt();
        zyxIndex[0] = in.readInt();
        accessTimeMinutes = in.readInt();
        numHits = new AtomicLong(in.readLong());
        numTilesInPage = new AtomicLong(in.readLong());
    }

    /**
     * Increments by one the counter of available tiles for the page and returns the new value for
     * the counter
     */
    public long addTile() {
        return this.numTilesInPage.incrementAndGet();
    }

    /**
     * Decrements by one the counter of available tiles for the page and returns the new value for
     * the counter
     */
    public long removeTile() {
        return this.numTilesInPage.decrementAndGet();
    }

}
