package org.geowebcache.storage;

public class SettingsObject {

    private long clearOldJobs = -1;

    public long getClearOldJobs() {
        return clearOldJobs;
    }

    public void setClearOldJobs(long clearOldJobs) {
        this.clearOldJobs = clearOldJobs;
    }

}
