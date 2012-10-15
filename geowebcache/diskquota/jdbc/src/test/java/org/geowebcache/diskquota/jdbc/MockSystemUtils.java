package org.geowebcache.diskquota.jdbc;

import org.geowebcache.diskquota.storage.SystemUtils;

public class MockSystemUtils extends SystemUtils {

    private int currTimeMinutes = -1;

    private long currTimeMillis = -1;

    public void setCurrentTimeMinutes(int currTimeMinutes) {
        this.currTimeMinutes = currTimeMinutes;
    }

    public void setCurrentTimeMillis(long currTimeMillis) {
        this.currTimeMillis = currTimeMillis;
    }

    @Override
    public int currentTimeMinutes() {
        return currTimeMinutes != -1 ? currTimeMinutes : super.currentTimeMinutes();
    }

    @Override
    public long currentTimeMillis() {
        return currTimeMillis != -1 ? currTimeMillis : super.currentTimeMillis();
    }
}
