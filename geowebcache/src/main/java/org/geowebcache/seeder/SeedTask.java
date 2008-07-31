package org.geowebcache.seeder;

public class SeedTask {
    private SeedRequest req = null;
    
    public SeedTask(SeedRequest req){
        this.req = req;
    }
    
    protected int doSeed(){
        System.out.println("begun seeding " + this.req.toString());
        return 0;
    }
    
}
