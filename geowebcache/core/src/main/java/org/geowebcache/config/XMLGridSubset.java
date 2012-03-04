/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.config;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;

public class XMLGridSubset implements Serializable, Cloneable {

    private static final long serialVersionUID = 2758612849329765806L;

    private static Log log = LogFactory.getLog(XMLGridSubset.class);

    private String gridSetName;

    private BoundingBox extent;

    private Integer zoomStart;

    private Integer zoomStop;

    private Integer minCachedLevel;

    private Integer maxCachedLevel;

    /**
     * Empty constructor
     */
    public XMLGridSubset() {
        // nothing to do
        readResolve();
    }

    private XMLGridSubset readResolve() {
        return this;
    }

    /**
     * Copy constructor
     */
    public XMLGridSubset(XMLGridSubset sset) {
        setGridSetName(sset.getGridSetName());
        setExtent(sset.getExtent() == null ? null : new BoundingBox(sset.getExtent()));
        setZoomStart(sset.getZoomStart());
        setZoomStop(sset.getZoomStop());
        setMinCachedLevel(sset.getMinCachedLevel());
        setMaxCachedLevel(sset.getMaxCachedLevel());
    }

    /**
     * Builds an XMLGridSubset out of a {@link GridSubset}
     */
    public XMLGridSubset(GridSubset sset) {
        setGridSetName(sset.getName());
        setExtent(sset.getOriginalExtent() == null ? null : new BoundingBox(
                sset.getOriginalExtent()));
        setZoomStart(sset.getZoomStart());
        setZoomStop(sset.getZoomStop());
        setMinCachedLevel(sset.getMinCachedZoom());
        setMaxCachedLevel(sset.getMaxCachedZoom());
    }

    @Override
    public XMLGridSubset clone() {
        return new XMLGridSubset(this);
    }

    public GridSubset getGridSubSet(GridSetBroker gridSetBroker) {

        GridSet gridSet = gridSetBroker.get(getGridSetName());

        if (gridSet == null) {
            log.error("Unable to find GridSet for \"" + getGridSetName() + "\"");
            return null;
        }
        return GridSubsetFactory.createGridSubSet(gridSet, getExtent(), getZoomStart(),
                getZoomStop(), minCachedLevel, maxCachedLevel);
    }

    public String getGridSetName() {
        return gridSetName;
    }

    /**
     * @param gridSetName
     *            the gridSetName to set
     */
    public void setGridSetName(String gridSetName) {
        this.gridSetName = gridSetName;
    }

    /**
     * @return the extent
     */
    public BoundingBox getExtent() {
        return extent;
    }

    /**
     * @param extent
     *            the extent to set
     */
    public void setExtent(BoundingBox extent) {
        this.extent = extent;
    }

    /**
     * @return the zoomStart
     */
    public Integer getZoomStart() {
        return zoomStart;
    }

    /**
     * @param zoomStart
     *            the zoomStart to set
     */
    public void setZoomStart(Integer zoomStart) {
        this.zoomStart = zoomStart;
    }

    /**
     * @return the zoomStop
     */
    public Integer getZoomStop() {
        return zoomStop;
    }

    /**
     * @param zoomStop
     *            the zoomStop to set
     */
    public void setZoomStop(Integer zoomStop) {
        this.zoomStop = zoomStop;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    public Integer getMinCachedLevel() {
        return minCachedLevel;
    }

    public void setMinCachedLevel(Integer minCachedLevel) {
        this.minCachedLevel = minCachedLevel;
    }

    public Integer getMaxCachedLevel() {
        return maxCachedLevel;
    }

    public void setMaxCachedLevel(Integer maxCachedLevel) {
        this.maxCachedLevel = maxCachedLevel;
    }
}
