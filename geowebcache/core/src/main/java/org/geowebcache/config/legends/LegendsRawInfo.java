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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Container for raw legends and the related default values. */
public class LegendsRawInfo {

    private Integer defaultWidth;
    private Integer defaultHeight;
    private String defaultFormat;

    private List<LegendRawInfo> legendsRawInfo = new ArrayList<>();

    public Integer getDefaultWidth() {
        return defaultWidth;
    }

    public void setDefaultWidth(Integer defaultWidth) {
        this.defaultWidth = defaultWidth;
    }

    public Integer getDefaultHeight() {
        return defaultHeight;
    }

    public void setDefaultHeight(Integer defaultHeight) {
        this.defaultHeight = defaultHeight;
    }

    public String getDefaultFormat() {
        return defaultFormat;
    }

    public void setDefaultFormat(String defaultFormat) {
        this.defaultFormat = defaultFormat;
    }

    public void addLegendRawInfo(LegendRawInfo legendRawInfo) {
        legendsRawInfo.add(legendRawInfo);
    }

    public List<LegendRawInfo> getLegendsRawInfo() {
        return legendsRawInfo;
    }

    /**
     * Builds the concrete legends info for each raw legend info using the provided layer information. The returned map
     * index the legend info per layer.
     */
    public Map<String, LegendInfo> getLegendsInfo(String layerName, String layerUrl) {
        Map<String, LegendInfo> legendsInfo = new HashMap<>();
        for (LegendRawInfo legendRawInfo : legendsRawInfo) {
            legendsInfo.put(
                    legendRawInfo.getStyle(),
                    legendRawInfo.getLegendInfo(layerName, layerUrl, defaultWidth, defaultHeight, defaultFormat));
        }
        return legendsInfo;
    }
}
