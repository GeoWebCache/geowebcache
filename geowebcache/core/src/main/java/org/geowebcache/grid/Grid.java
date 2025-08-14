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
package org.geowebcache.grid;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** @author groldan */
public class Grid implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long numTilesWide;

    private long numTilesHigh;

    private double resolution;

    private double scaleDenom;

    private String name;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Grid)) return false;

        Grid other = (Grid) obj;

        if (numTilesWide != other.numTilesWide) return false;

        if (numTilesHigh != other.numTilesHigh) return false;

        if (Math.abs(other.resolution - resolution) / Math.abs(other.resolution + resolution) > 0.005) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numTilesWide, numTilesHigh, resolution, scaleDenom, name);
    }

    public String getName() {
        return name;
    }

    public double getScaleDenominator() {
        return scaleDenom;
    }

    /** @return the resolution */
    public double getResolution() {
        return resolution;
    }

    /** @param resolution the resolution to set */
    public void setResolution(double resolution) {
        this.resolution = resolution;
    }

    /** @param scaleDenom the scaleDenom to set */
    public void setScaleDenominator(double scaleDenom) {
        this.scaleDenom = scaleDenom;
    }

    /** @param name the name to set */
    public void setName(String name) {
        this.name = name;
    }

    /** @return the numTilesWide */
    public long getNumTilesWide() {
        return numTilesWide;
    }

    /** @param numTilesWide the numTilesWide to set */
    public void setNumTilesWide(long numTilesWide) {
        this.numTilesWide = numTilesWide;
    }

    /** @return the numTilesHigh */
    public long getNumTilesHigh() {
        return numTilesHigh;
    }

    /** @param numTilesHigh the numTilesHigh to set */
    public void setNumTilesHigh(long numTilesHigh) {
        this.numTilesHigh = numTilesHigh;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append("[name: '")
                .append(name)
                .append("', resolution: ")
                .append(resolution)
                .append(", scale denom: ")
                .append(scaleDenom)
                .append(", grid extent: ")
                .append(numTilesWide)
                .append(" x ")
                .append(numTilesHigh)
                .append("]")
                .toString();
    }

    @Override
    public Grid clone() {
        Grid clon;
        try {
            clon = (Grid) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        return clon;
    }
}
