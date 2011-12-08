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
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;

/**
 * GridSet model for XStream persistence
 */
public class XMLGridSet implements Serializable {

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

    /**
     * Default constructor for XStream
     */
    public XMLGridSet() {
        // do nothing
    }

    /**
     * Copy constructor
     */
    public XMLGridSet(XMLGridSet orig) {
        setAlignTopLeft(orig.getAlignTopLeft());
        setExtent(orig.getExtent() == null ? null : new BoundingBox(orig.getExtent()));
        setResolutions(orig.getResolutions() == null ? null : orig.getResolutions().clone());
        setLevels(orig.getLevels());
        setScaleDenominators(orig.getScaleDenominators() == null ? null : orig
                .getScaleDenominators().clone());
        setMetersPerUnit(orig.getMetersPerUnit());
        setName(orig.getName());
        setDescription(orig.getDescription());
        setPixelSize(orig.getPixelSize());
        setScaleNames(orig.getScaleNames() == null ? null : orig.getScaleNames().clone());
        setSrs(orig.getSrs());
        setTileWidth(orig.getTileWidth());
        setTileHeight(orig.getTileHeight());
    }

    /**
     * Builds an XMLGridSet from a GridSet
     */
    public XMLGridSet(GridSet gset) {
        setAlignTopLeft(gset.isTopLeftAligned());
        setYCoordinateFirst(gset.isyCoordinateFirst());
        setExtent(gset.getOriginalExtent());

        setLevels(null);
        if (gset.isResolutionsPreserved()) {
            setResolutions(resolutions(gset.getGridLevels()));
            setScaleDenominators(null);
        } else {
            setResolutions(null);
            setScaleDenominators(scaleDenominators(gset.getGridLevels()));
        }

        setMetersPerUnit(gset.getMetersPerUnit());
        setName(gset.getName());
        setDescription(gset.getDescription());
        setPixelSize(gset.getPixelSize());
        setScaleNames(scaleNames(gset.getGridLevels()));
        setSrs(gset.getSrs());
        setTileWidth(gset.getTileWidth());
        setTileHeight(gset.getTileHeight());
    }

    private static double[] resolutions(Grid[] grids) {
        double[] resolutions = new double[grids.length];
        for (int i = 0; i < resolutions.length; i++) {
            resolutions[i] = grids[i].getResolution();
        }
        return resolutions;
    }

    private static double[] scaleDenominators(Grid[] grids) {
        double[] scales = new double[grids.length];
        for (int i = 0; i < scales.length; i++) {
            scales[i] = grids[i].getScaleDenominator();
        }
        return scales;
    }

    private static String[] scaleNames(Grid[] grids) {
        String[] scaleNames = new String[grids.length];
        for (int i = 0; i < scaleNames.length; i++) {
            scaleNames[i] = grids[i].getName();
        }
        return scaleNames;
    }

    public String getName() {
        return name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description
     *            the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @param name
     *            the name to set
     */
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
            gridSet = GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, resolutions,
                    scaleDenominators, metersPerUnit, pixelSize, scaleNames, tileWidth, tileHeight,
                    yCoordinateFirst);
        } else {
            if (getLevels() == null) {
                setLevels(18);
            }

            Integer levels = getLevels();
            gridSet = GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, levels,
                    metersPerUnit, pixelSize, tileWidth, tileHeight, yCoordinateFirst);
        }

        gridSet.setDescription(getDescription());

        return gridSet;
    }

    /**
     * @return the srs
     */
    SRS getSrs() {
        return srs;
    }

    /**
     * @param srs
     *            the srs to set
     */
    void setSrs(SRS srs) {
        this.srs = srs;
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
     * @return the alignTopLeft
     */
    public Boolean getAlignTopLeft() {
        return alignTopLeft;
    }

    /**
     * @param alignTopLeft
     *            the alignTopLeft to set
     */
    public void setAlignTopLeft(Boolean alignTopLeft) {
        this.alignTopLeft = alignTopLeft;
    }

    /**
     * @return the resolutions
     */
    public double[] getResolutions() {
        return resolutions;
    }

    /**
     * @param resolutions
     *            the resolutions to set
     */
    public void setResolutions(double[] resolutions) {
        this.resolutions = resolutions;
    }

    /**
     * @return the scaleDenominators
     */
    public double[] getScaleDenominators() {
        return scaleDenominators;
    }

    /**
     * @param scaleDenominators
     *            the scaleDenominators to set
     */
    public void setScaleDenominators(double[] scaleDenominators) {
        this.scaleDenominators = scaleDenominators;
    }

    /**
     * @return the levels
     */
    public Integer getLevels() {
        return levels;
    }

    /**
     * @param levels
     *            the levels to set
     */
    public void setLevels(Integer levels) {
        this.levels = levels;
    }

    /**
     * @return the metersPerUnit
     */
    public Double getMetersPerUnit() {
        return metersPerUnit;
    }

    /**
     * @param metersPerUnit
     *            the metersPerUnit to set
     */
    public void setMetersPerUnit(Double metersPerUnit) {
        this.metersPerUnit = metersPerUnit;
    }

    /**
     * @return the pixelSize
     */
    public Double getPixelSize() {
        return pixelSize;
    }

    /**
     * @param pixelSize
     *            the pixelSize to set
     */
    public void setPixelSize(Double pixelSize) {
        this.pixelSize = pixelSize;
    }

    /**
     * @return the scaleNames
     */
    public String[] getScaleNames() {
        return scaleNames;
    }

    /**
     * @param scaleNames
     *            the scaleNames to set
     */
    public void setScaleNames(String[] scaleNames) {
        this.scaleNames = scaleNames;
    }

    /**
     * @return the tileHeight
     */
    public Integer getTileHeight() {
        return tileHeight;
    }

    /**
     * @param tileHeight
     *            the tileHeight to set
     */
    public void setTileHeight(Integer tileHeight) {
        this.tileHeight = tileHeight;
    }

    /**
     * @return the tileWidth
     */
    public Integer getTileWidth() {
        return tileWidth;
    }

    /**
     * @param tileWidth
     *            the tileWidth to set
     */
    public void setTileWidth(Integer tileWidth) {
        this.tileWidth = tileWidth;
    }

    /**
     * @return the yCoordinateFirst
     */
    public Boolean getYCoordinateFirst() {
        return yCoordinateFirst;
    }

    /**
     * @param yCoordinateFirst
     *            the yCoordinateFirst to set
     */
    public void setYCoordinateFirst(Boolean yCoordinateFirst) {
        this.yCoordinateFirst = yCoordinateFirst;
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
}