/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.config;

import java.io.Serial;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;

/**
 * GridSubSet model for XStream persistence
 *
 * @see GridSubset
 */
public class XMLGridSubset implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 2758612849329765806L;

    private static Logger log = Logging.getLogger(XMLGridSubset.class.getName());

    private String gridSetName;

    private BoundingBox extent;

    private Integer zoomStart;

    private Integer zoomStop;

    private Integer minCachedLevel;

    private Integer maxCachedLevel;

    /** Empty constructor */
    public XMLGridSubset() {
        // nothing to do
        readResolve();
    }

    private Object readResolve() {
        return this;
    }

    /** Copy constructor */
    public XMLGridSubset(XMLGridSubset sset) {
        setGridSetName(sset.getGridSetName());
        setExtent(sset.getExtent() == null ? null : new BoundingBox(sset.getExtent()));
        setZoomStart(sset.getZoomStart());
        setZoomStop(sset.getZoomStop());
        setMinCachedLevel(sset.getMinCachedLevel());
        setMaxCachedLevel(sset.getMaxCachedLevel());
    }

    /** Builds an XMLGridSubset out of a {@link GridSubset} */
    public XMLGridSubset(GridSubset sset) {
        setGridSetName(sset.getName());
        setExtent(sset.getOriginalExtent() == null ? null : new BoundingBox(sset.getOriginalExtent()));
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
            log.log(Level.SEVERE, "Unable to find GridSet for \"" + getGridSetName() + "\"");
            return null;
        }
        return GridSubsetFactory.createGridSubSet(
                gridSet, getExtent(), getZoomStart(), getZoomStop(), minCachedLevel, maxCachedLevel);
    }

    public String getGridSetName() {
        return gridSetName;
    }

    /** @param gridSetName the gridSetName to set */
    public void setGridSetName(String gridSetName) {
        this.gridSetName = gridSetName;
    }

    /** @return the extent */
    public BoundingBox getExtent() {
        return extent;
    }

    /** @param extent the extent to set */
    public void setExtent(BoundingBox extent) {
        this.extent = extent;
    }

    /** @return the zoomStart */
    public Integer getZoomStart() {
        return zoomStart;
    }

    /** @param zoomStart the zoomStart to set */
    public void setZoomStart(Integer zoomStart) {
        this.zoomStart = zoomStart;
    }

    /** @return the zoomStop */
    public Integer getZoomStop() {
        return zoomStop;
    }

    /** @param zoomStop the zoomStop to set */
    public void setZoomStop(Integer zoomStop) {
        this.zoomStop = zoomStop;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((extent == null) ? 0 : extent.hashCode());
        result = prime * result + ((gridSetName == null) ? 0 : gridSetName.hashCode());
        result = prime * result + ((maxCachedLevel == null) ? 0 : maxCachedLevel.hashCode());
        result = prime * result + ((minCachedLevel == null) ? 0 : minCachedLevel.hashCode());
        result = prime * result + ((zoomStart == null) ? 0 : zoomStart.hashCode());
        result = prime * result + ((zoomStop == null) ? 0 : zoomStop.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        XMLGridSubset other = (XMLGridSubset) obj;
        if (extent == null) {
            if (other.extent != null) return false;
        } else if (!extent.equals(other.extent)) return false;
        if (gridSetName == null) {
            if (other.gridSetName != null) return false;
        } else if (!gridSetName.equals(other.gridSetName)) return false;
        if (maxCachedLevel == null) {
            if (other.maxCachedLevel != null) return false;
        } else if (!maxCachedLevel.equals(other.maxCachedLevel)) return false;
        if (minCachedLevel == null) {
            if (other.minCachedLevel != null) return false;
        } else if (!minCachedLevel.equals(other.minCachedLevel)) return false;
        if (zoomStart == null) {
            if (other.zoomStart != null) return false;
        } else if (!zoomStart.equals(other.zoomStart)) return false;
        if (zoomStop == null) {
            if (other.zoomStop != null) return false;
        } else if (!zoomStop.equals(other.zoomStop)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "XMLGridSubset [gridSetName="
                + gridSetName
                + ", extent="
                + extent
                + ", zoomStart="
                + zoomStart
                + ", zoomStop="
                + zoomStop
                + ", minCachedLevel="
                + minCachedLevel
                + ", maxCachedLevel="
                + maxCachedLevel
                + "]";
    }
}
