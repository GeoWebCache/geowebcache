package org.geowebcache.diskquota.lru;

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
     * 
     */
    private static final long serialVersionUID = -17011977L;

    /**
     * Current time with near-minute precision, meaning we can hold values up till January 1, 2115
     * UTC
     */
    private static final AtomicInteger currentTime = new AtomicInteger((int) System
            .currentTimeMillis() / 1000 / 1000);
    static {
        CustomizableThreadFactory tf = new CustomizableThreadFactory("Minute-Timer");
        tf.setDaemon(true);
        tf.setThreadPriority((Thread.MAX_PRIORITY - Thread.MIN_PRIORITY) / 5);
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, tf);
        Runnable command = new Runnable() {
            public void run() {
                int minutesSinceJan1_1970 = (int) (System.currentTimeMillis() / 1000L / 1000L);
                TilePage.currentTime.set(minutesSinceJan1_1970);
            }
        };
        executorService.scheduleAtFixedRate(command, 1L, 1L, TimeUnit.MINUTES);
    }

    /**
     * x, y, z index for this page, in reverse order (z, y, x) to aid in a more efficient short
     * circuit comparison at {@link #equals(Object)}
     */
    private int[] zyxIndex;

    private AtomicLong numHits;

    /**
     * Last access time, with near-minute precision
     */
    private int accessTimeMinutes;

    public TilePage(final int x, final int y, final int z) {
        this(x, y, z, 0);
    }

    public TilePage(final int x, final int y, final int z, final long numHits) {
        this.zyxIndex = new int[] { z, y, x };
        this.numHits = new AtomicLong(numHits);
        this.accessTimeMinutes = 0;// not accessed yet
    }

    public void markHit() {
        numHits.addAndGet(1L);
        accessTimeMinutes = currentTime.get();
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
        return 17 * (zyxIndex[0] + zyxIndex[1] ^ 2 + zyxIndex[2] ^ 3);
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append("[").append(zyxIndex[2])
                .append(',').append(zyxIndex[1]).append(',').append(zyxIndex[0]).append(". Hits: ")
                .append(getNumHits()).append(". Access: ").append(
                        currentTime.get() - accessTimeMinutes).append("m ago.]").toString();
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt((int) TilePage.serialVersionUID);
        out.writeInt(zyxIndex[2]);
        out.writeInt(zyxIndex[1]);
        out.writeInt(zyxIndex[0]);
        out.writeInt(accessTimeMinutes);
        out.writeLong(numHits.get());
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
    }

}
