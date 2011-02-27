package org.geowebcache.diskquota.storage;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;

@Entity
public class PageLocation {

    @PrimaryKey
    String key;

    private byte pageZ;

    private int pageY;

    private int pageX;

    private transient int hashCode;

    PageLocation() {
        hashCode = Integer.MIN_VALUE;
    }

    public PageLocation(int pageX, int pageY, int pageZ) {
        this.key = new StringBuilder().append(pageZ).append(',').append(pageY).append(',')
                .append(pageX).toString();
        this.pageX = pageX;
        this.pageY = pageY;
        this.pageZ = (byte) pageZ;
        hashCode = Integer.MIN_VALUE;
    }

    public String getKey() {
        return key;
    }

    public int getPageX() {
        return pageX;
    }

    public int getPageY() {
        return pageY;
    }

    public byte getPageZ() {
        return pageZ;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PageLocation)) {
            return false;
        }
        PageLocation p = (PageLocation) o;
        return pageZ == p.pageZ && pageY == p.pageY && pageX == p.pageX;
    }

    @Override
    public int hashCode() {
        if (hashCode == Integer.MIN_VALUE) {
            hashCode = new HashCodeBuilder().append(pageZ).append(pageY).append(pageX).toHashCode();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        return new StringBuilder("[").append(pageX).append(',').append(pageY).append(',')
                .append(pageZ).append(']').toString();
    }
}
