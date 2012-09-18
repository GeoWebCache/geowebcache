package org.geowebcache.diskquota.storage;

import org.springframework.util.Assert;

public class SystemUtils {

    private static SystemUtils INSTANCE = new SystemUtils();

    public static void set(SystemUtils instance) {
        Assert.notNull(instance);
        INSTANCE = instance;
    }

    public static SystemUtils get() {
        return INSTANCE;
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public int currentTimeMinutes() {
        return (int) (currentTimeMillis() / 1000 / 60);
    }
}
