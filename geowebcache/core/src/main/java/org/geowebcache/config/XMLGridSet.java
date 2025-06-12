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
import java.util.Arrays;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;

/**
 * GridSet model for XStream persistence
 *
 * @see GridSet
 */
public class XMLGridSet implements Serializable {

    @Serial
    private static final long serialVersionUID = 2684804068163933728L;

    private String name;

    private String description;

    private SRS srs;

    private BoundingBox extent;

    private Boolean alignTopLeft;

    private double[] resolutions;

    private double[] scaleDenominators;

    private Integer levels;

    private Double metersPerUnit;

    private Double pixelSize;

    private String[] scaleNames;

    private Integer tileHeight;

    private Integer tileWidth;

    private Boolean yCoordinateFirst;

    /** Default constructor for XStream */
    public XMLGridSet() {
        // do nothing
    }

    /** Copy constructor */
    public XMLGridSet(XMLGridSet orig) {
        setAlignTopLeft(orig.getAlignTopLeft());
        setExtent(orig.getExtent() == null ? null : new BoundingBox(orig.getExtent()));
        setResolutions(
                orig.getResolutions() == null ? null : orig.getResolutions().clone());
        setLevels(orig.getLevels());
        setScaleDenominators(
                orig.getScaleDenominators() == null
                        ? null
                        : orig.getScaleDenominators().clone());
        setMetersPerUnit(orig.getMetersPerUnit());
        setName(orig.getName());
        setDescription(orig.getDescription());
        setPixelSize(orig.getPixelSize());
        setScaleNames(orig.getScaleNames() == null ? null : orig.getScaleNames().clone());
        setSrs(orig.getSrs());
        setTileWidth(orig.getTileWidth());
        setTileHeight(orig.getTileHeight());
    }

    /** Builds an XMLGridSet from a GridSet */
    public XMLGridSet(GridSet gset) {
        setAlignTopLeft(gset.isTopLeftAligned());
        setYCoordinateFirst(gset.isyCoordinateFirst());
        setExtent(gset.getOriginalExtent());

        setLevels(null);
        if (gset.isResolutionsPreserved()) {
            setResolutions(resolutions(gset));
            setScaleDenominators(null);
        } else {
            setResolutions(null);
            setScaleDenominators(scaleDenominators(gset));
        }

        setMetersPerUnit(gset.getMetersPerUnit());
        setName(gset.getName());
        setDescription(gset.getDescription());
        setPixelSize(gset.getPixelSize());
        setScaleNames(scaleNames(gset));
        setSrs(gset.getSrs());
        setTileWidth(gset.getTileWidth());
        setTileHeight(gset.getTileHeight());
    }

    private static double[] resolutions(GridSet gridSet) {
        double[] resolutions = new double[gridSet.getNumLevels()];
        for (int i = 0; i < resolutions.length; i++) {
            resolutions[i] = gridSet.getGrid(i).getResolution();
        }
        return resolutions;
    }

    private static double[] scaleDenominators(GridSet gridSet) {
        double[] scales = new double[gridSet.getNumLevels()];
        for (int i = 0; i < scales.length; i++) {
            scales[i] = gridSet.getGrid(i).getScaleDenominator();
        }
        return scales;
    }

    private static String[] scaleNames(GridSet gridSet) {
        String[] scaleNames = new String[gridSet.getNumLevels()];
        for (int i = 0; i < scaleNames.length; i++) {
            scaleNames[i] = gridSet.getGrid(i).getName();
        }
        return scaleNames;
    }

    public String getName() {
        return name;
    }

    /** @return the description */
    public String getDescription() {
        return description;
    }

    /** @param description the description to set */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @param name the name to set */
    public void setName(String name) {
        this.name = name;
    }

    public GridSet makeGridSet() {
        if (getTileWidth() == null) {
            setTileWidth(256);
        }
        if (getTileHeight() == null) {
            setTileHeight(256);
        }

        if (getAlignTopLeft() == null) {
            setAlignTopLeft(false);
        }

        if (getPixelSize() == null) {
            setPixelSize(GridSetFactory.DEFAULT_PIXEL_SIZE_METER);
        }

        if (getYCoordinateFirst() == null) {
            setYCoordinateFirst(false);
        }

        GridSet gridSet;

        String name = getName();
        SRS srs = getSrs();
        BoundingBox extent = getExtent();
        Boolean alignTopLeft = getAlignTopLeft();
        double[] resolutions = getResolutions();
        double[] scaleDenominators = getScaleDenominators();
        Double metersPerUnit = getMetersPerUnit();
        Double pixelSize = getPixelSize();
        String[] scaleNames = getScaleNames();
        Integer tileWidth = getTileWidth();
        Integer tileHeight = getTileHeight();
        Boolean yCoordinateFirst = getYCoordinateFirst();

        if (getResolutions() != null || getScaleDenominators() != null) {
            gridSet = GridSetFactory.createGridSet(
                    name,
                    srs,
                    extent,
                    alignTopLeft,
                    resolutions,
                    scaleDenominators,
                    metersPerUnit,
                    pixelSize,
                    scaleNames,
                    tileWidth,
                    tileHeight,
                    yCoordinateFirst);
        } else {
            if (getLevels() == null) {
                setLevels(18);
            }

            Integer levels = getLevels();
            gridSet = GridSetFactory.createGridSet(
                    name,
                    srs,
                    extent,
                    alignTopLeft,
                    levels,
                    metersPerUnit,
                    pixelSize,
                    tileWidth,
                    tileHeight,
                    yCoordinateFirst);
        }

        gridSet.setDescription(getDescription());

        return gridSet;
    }

    /** @return the srs */
    SRS getSrs() {
        return srs;
    }

    /** @param srs the srs to set */
    void setSrs(SRS srs) {
        this.srs = srs;
    }

    /** @return the extent */
    public BoundingBox getExtent() {
        return extent;
    }

    /** @param extent the extent to set */
    public void setExtent(BoundingBox extent) {
        this.extent = extent;
    }

    /** @return the alignTopLeft */
    public Boolean getAlignTopLeft() {
        return alignTopLeft;
    }

    /** @param alignTopLeft the alignTopLeft to set */
    public void setAlignTopLeft(Boolean alignTopLeft) {
        this.alignTopLeft = alignTopLeft;
    }

    /** @return the resolutions */
    public double[] getResolutions() {
        return resolutions;
    }

    /** @param resolutions the resolutions to set */
    public void setResolutions(double[] resolutions) {
        this.resolutions = resolutions;
    }

    /** @return the scaleDenominators */
    public double[] getScaleDenominators() {
        return scaleDenominators;
    }

    /** @param scaleDenominators the scaleDenominators to set */
    public void setScaleDenominators(double[] scaleDenominators) {
        this.scaleDenominators = scaleDenominators;
    }

    /** @return the levels */
    public Integer getLevels() {
        return levels;
    }

    /** @param levels the levels to set */
    public void setLevels(Integer levels) {
        this.levels = levels;
    }

    /** @return the metersPerUnit */
    public Double getMetersPerUnit() {
        return metersPerUnit;
    }

    /** @param metersPerUnit the metersPerUnit to set */
    public void setMetersPerUnit(Double metersPerUnit) {
        this.metersPerUnit = metersPerUnit;
    }

    /** @return the pixelSize */
    public Double getPixelSize() {
        return pixelSize;
    }

    /** @param pixelSize the pixelSize to set */
    public void setPixelSize(Double pixelSize) {
        this.pixelSize = pixelSize;
    }

    /** @return the scaleNames */
    public String[] getScaleNames() {
        return scaleNames;
    }

    /** @param scaleNames the scaleNames to set */
    public void setScaleNames(String[] scaleNames) {
        this.scaleNames = scaleNames;
    }

    /** @return the tileHeight */
    public Integer getTileHeight() {
        return tileHeight;
    }

    /** @param tileHeight the tileHeight to set */
    public void setTileHeight(Integer tileHeight) {
        this.tileHeight = tileHeight;
    }

    /** @return the tileWidth */
    public Integer getTileWidth() {
        return tileWidth;
    }

    /** @param tileWidth the tileWidth to set */
    public void setTileWidth(Integer tileWidth) {
        this.tileWidth = tileWidth;
    }

    /** @return the yCoordinateFirst */
    public Boolean getYCoordinateFirst() {
        return yCoordinateFirst;
    }

    /** @param yCoordinateFirst the yCoordinateFirst to set */
    public void setYCoordinateFirst(Boolean yCoordinateFirst) {
        this.yCoordinateFirst = yCoordinateFirst;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((alignTopLeft == null) ? 0 : alignTopLeft.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((extent == null) ? 0 : extent.hashCode());
        result = prime * result + ((levels == null) ? 0 : levels.hashCode());
        result = prime * result + ((metersPerUnit == null) ? 0 : metersPerUnit.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((pixelSize == null) ? 0 : pixelSize.hashCode());
        result = prime * result + Arrays.hashCode(resolutions);
        result = prime * result + Arrays.hashCode(scaleDenominators);
        result = prime * result + Arrays.hashCode(scaleNames);
        result = prime * result + ((srs == null) ? 0 : srs.hashCode());
        result = prime * result + ((tileHeight == null) ? 0 : tileHeight.hashCode());
        result = prime * result + ((tileWidth == null) ? 0 : tileWidth.hashCode());
        result = prime * result + ((yCoordinateFirst == null) ? 0 : yCoordinateFirst.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        XMLGridSet other = (XMLGridSet) obj;
        if (alignTopLeft == null) {
            if (other.alignTopLeft != null) return false;
        } else if (!alignTopLeft.equals(other.alignTopLeft)) return false;
        if (description == null) {
            if (other.description != null) return false;
        } else if (!description.equals(other.description)) return false;
        if (extent == null) {
            if (other.extent != null) return false;
        } else if (!extent.equals(other.extent)) return false;
        if (levels == null) {
            if (other.levels != null) return false;
        } else if (!levels.equals(other.levels)) return false;
        if (metersPerUnit == null) {
            if (other.metersPerUnit != null) return false;
        } else if (!metersPerUnit.equals(other.metersPerUnit)) return false;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        if (pixelSize == null) {
            if (other.pixelSize != null) return false;
        } else if (!pixelSize.equals(other.pixelSize)) return false;
        if (!Arrays.equals(resolutions, other.resolutions)) return false;
        if (!Arrays.equals(scaleDenominators, other.scaleDenominators)) return false;
        if (!Arrays.equals(scaleNames, other.scaleNames)) return false;
        if (srs == null) {
            if (other.srs != null) return false;
        } else if (!srs.equals(other.srs)) return false;
        if (tileHeight == null) {
            if (other.tileHeight != null) return false;
        } else if (!tileHeight.equals(other.tileHeight)) return false;
        if (tileWidth == null) {
            if (other.tileWidth != null) return false;
        } else if (!tileWidth.equals(other.tileWidth)) return false;
        if (yCoordinateFirst == null) {
            if (other.yCoordinateFirst != null) return false;
        } else if (!yCoordinateFirst.equals(other.yCoordinateFirst)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "XMLGridSet [name="
                + name
                + ", description="
                + description
                + ", srs="
                + srs
                + ", extent="
                + extent
                + ", alignTopLeft="
                + alignTopLeft
                + ", resolutions="
                + Arrays.toString(resolutions)
                + ", scaleDenominators="
                + Arrays.toString(scaleDenominators)
                + ", levels="
                + levels
                + ", metersPerUnit="
                + metersPerUnit
                + ", pixelSize="
                + pixelSize
                + ", scaleNames="
                + Arrays.toString(scaleNames)
                + ", tileHeight="
                + tileHeight
                + ", tileWidth="
                + tileWidth
                + ", yCoordinateFirst="
                + yCoordinateFirst
                + "]";
    }
}
