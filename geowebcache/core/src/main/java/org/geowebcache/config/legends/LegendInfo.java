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
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.config.legends;

/**
 * Simple container for the information related with a style legends. Builder {@link LegendInfoBuilder} should be used
 * to create instances of this class.
 */
public class LegendInfo {

    private final String styleName;
    private final Integer width;
    private final Integer height;
    private final String format;
    private final String legendUrl;
    private final Double minScale;
    private final Double maxScale;

    LegendInfo(
            String styleName,
            Integer width,
            Integer height,
            String format,
            String legendUrl,
            Double minScale,
            Double maxScale) {
        this.styleName = styleName;
        this.width = width;
        this.height = height;
        this.format = format;
        this.legendUrl = legendUrl;
        this.minScale = minScale;
        this.maxScale = maxScale;
    }

    public String getStyleName() {
        return styleName;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getFormat() {
        return format;
    }

    public String getLegendUrl() {
        return legendUrl;
    }

    public Double getMinScale() {
        return minScale;
    }

    public Double getMaxScale() {
        return maxScale;
    }
}
