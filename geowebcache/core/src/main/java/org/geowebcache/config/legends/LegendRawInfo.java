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

/** Contains the raw information about a style legend as it may appear in the XML configuration for example. */
public class LegendRawInfo {

    private String style;
    private Integer width;
    private Integer height;
    private String format;
    private String url;
    private String completeUrl;
    private Double minScale;
    private Double maxScale;

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCompleteUrl() {
        return completeUrl;
    }

    public void setCompleteUrl(String completeUrl) {
        this.completeUrl = completeUrl;
    }

    public Double getMinScale() {
        return minScale;
    }

    public void setMinScale(Double minScale) {
        this.minScale = minScale;
    }

    public Double getMaxScale() {
        return maxScale;
    }

    public void setMaxScale(Double maxScale) {
        this.maxScale = maxScale;
    }

    /** Build the concrete legend information using the provided layer information and defaults values. */
    public LegendInfo getLegendInfo(
            String layerName, String layerUrl, Integer defaultWidth, Integer defaultHeight, String defaultFormat) {
        return new LegendInfoBuilder()
                .withLayerName(layerName)
                .withLayerUrl(layerUrl)
                .withDefaultWidth(defaultWidth)
                .withDefaultHeight(defaultHeight)
                .withDefaultFormat(defaultFormat)
                .withStyleName(style)
                .withWidth(width)
                .withHeight(height)
                .withFormat(format)
                .withUrl(url)
                .withCompleteUrl(completeUrl)
                .withMinScale(minScale)
                .withMaxScale(maxScale)
                .build();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        LegendRawInfo that = (LegendRawInfo) other;
        if (style != null ? !style.equals(that.style) : that.style != null) return false;
        if (width != null ? !width.equals(that.width) : that.width != null) return false;
        if (height != null ? !height.equals(that.height) : that.height != null) return false;
        if (format != null ? !format.equals(that.format) : that.format != null) return false;
        if (url != null ? !url.equals(that.url) : that.url != null) return false;
        if (completeUrl != null ? !completeUrl.equals(that.completeUrl) : that.completeUrl != null) return false;
        if (minScale != null ? !minScale.equals(that.minScale) : that.minScale != null) return false;
        return maxScale != null ? maxScale.equals(that.maxScale) : that.maxScale == null;
    }

    @Override
    public int hashCode() {
        int result = style != null ? style.hashCode() : 0;
        result = 31 * result + (width != null ? width.hashCode() : 0);
        result = 31 * result + (height != null ? height.hashCode() : 0);
        result = 31 * result + (format != null ? format.hashCode() : 0);
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + (completeUrl != null ? completeUrl.hashCode() : 0);
        result = 31 * result + (minScale != null ? minScale.hashCode() : 0);
        result = 31 * result + (maxScale != null ? maxScale.hashCode() : 0);
        return result;
    }
}
