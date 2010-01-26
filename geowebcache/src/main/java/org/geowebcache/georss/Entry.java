package org.geowebcache.georss;

import java.net.URI;
import java.util.Date;

import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Geometry;

class Entry {

    private String title;

    private String subtitle;

    private URI link;

    private String id;

    private Date updated;

    private Geometry where;

    private String SRS;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public URI getLink() {
        return link;
    }

    public void setLink(URI link) {
        this.link = link;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Geometry getWhere() {
        return where;
    }

    public void setWhere(Geometry where) {
        this.where = where;
    }

    public String getSRS() {
        return SRS;
    }

    public void setSRS(final String srsName) {
        SRS = srsName;
    }

}
