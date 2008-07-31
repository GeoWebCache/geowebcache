package org.geowebcache.seeder;

import org.geowebcache.util.wms.BBOX;
import org.geowebcache.layer.SRS;
import org.geowebcache.mime.MimeType;

public class SeedRequest {
    private String name = null;
    private BBOX bounds = null;
    private SRS projection = null;
    private int zoomstart = -1;
    private int zoomstop = -1;
    private MimeType format = null;
    
    public void setName(String name) {
        this.name = name;
    }
    public void setBounds(String bounds) {
        this.bounds = new BBOX(bounds);
    }
    public void setProjection(SRS projection) {
        this.projection = projection;
    }
    public void setFormat(MimeType mt) {
        this.format = mt;
    }
    public void setZoomStart(int zs) {
        this.zoomstart = zs;
    }
    public void setZoomStop(int zs) {
        this.zoomstop = zs;
    }
    
    public String toString(){
        return "SeedRequest: "
                +"\n name: " + this.name
                +"\n bounds: " + this.bounds.toString()
                +"\n projection: " + this.projection.toString()
                +"\n zoomstart: " + this.zoomstart
                +"\n zoomstop: " + this.zoomstop
                +"\n format: " + this.format.toString();
                
    }
}
