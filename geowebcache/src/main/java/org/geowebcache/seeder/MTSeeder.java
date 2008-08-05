package org.geowebcache.seeder;

import java.util.concurrent.Callable;

public class MTSeeder implements Callable<SeedTask> {
    private SeedTask seedTask= null;
    
    public MTSeeder(SeedTask st){
        this.seedTask = st;
    }
    public SeedTask call(){
        this.seedTask.doSeed();
        return this.seedTask;
    }
}
