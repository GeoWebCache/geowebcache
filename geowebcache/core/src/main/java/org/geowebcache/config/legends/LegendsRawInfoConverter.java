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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/** XML converter for {@link LegendsRawInfo}. */
public class LegendsRawInfoConverter implements Converter {

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        LegendsRawInfo legendsRawInfo = (LegendsRawInfo) source;
        // encode default values
        writer.addAttribute("defaultWidth", String.valueOf(legendsRawInfo.getDefaultWidth()));
        writer.addAttribute("defaultHeight", String.valueOf(legendsRawInfo.getDefaultHeight()));
        writer.addAttribute("defaultFormat", String.valueOf(legendsRawInfo.getDefaultFormat()));
        // encode legends information
        for (LegendRawInfo legendRawInfo : legendsRawInfo.getLegendsRawInfo()) {
            writer.startNode("legend");
            writer.addAttribute("style", legendRawInfo.getStyle());
            encodeAttribute(writer, "width", legendRawInfo.getWidth());
            encodeAttribute(writer, "height", legendRawInfo.getHeight());
            encodeAttribute(writer, "format", legendRawInfo.getFormat());
            encodeAttribute(writer, "url", legendRawInfo.getUrl());
            encodeAttribute(writer, "completeUrl", legendRawInfo.getCompleteUrl());
            encodeAttribute(writer, "minScale", legendRawInfo.getMinScale());
            encodeAttribute(writer, "maxScale", legendRawInfo.getMaxScale());
            writer.endNode();
        }
    }

    private void encodeAttribute(HierarchicalStreamWriter writer, String name, Object value) {
        if (value == null) {
            return;
        }
        writer.startNode(name);
        writer.setValue(value.toString());
        writer.endNode();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        LegendsRawInfo legendsRawInfo = new LegendsRawInfo();
        // setting the defaults based on legends element attributes
        legendsRawInfo.setDefaultWidth(toInteger(reader.getAttribute("defaultWidth")));
        legendsRawInfo.setDefaultHeight(toInteger(reader.getAttribute("defaultHeight")));
        legendsRawInfo.setDefaultFormat(reader.getAttribute("defaultFormat"));
        // parsing all legends information
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            legendsRawInfo.addLegendRawInfo(parseLegendRawInfo(reader));
            reader.moveUp();
        }
        return legendsRawInfo;
    }

    /** Helper method that converts a non NULL string value to an integer. */
    private Integer toInteger(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return Integer.valueOf(rawValue);
    }

    /** Helper method that converts a non NULL string value to an double. */
    private Double toDouble(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return Double.valueOf(rawValue);
    }

    /** Helper method that retrieves from the reader a legend raw information. */
    private LegendRawInfo parseLegendRawInfo(HierarchicalStreamReader reader) {
        LegendRawInfo legendRawInfo = new LegendRawInfo();
        legendRawInfo.setStyle(reader.getAttribute("style"));
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            switch (reader.getNodeName()) {
                case "width":
                    legendRawInfo.setWidth(toInteger(reader.getValue()));
                    break;
                case "height":
                    legendRawInfo.setHeight(toInteger(reader.getValue()));
                    break;
                case "format":
                    legendRawInfo.setFormat(reader.getValue());
                    break;
                case "url":
                    legendRawInfo.setUrl(reader.getValue());
                    break;
                case "completeUrl":
                    legendRawInfo.setCompleteUrl(reader.getValue());
                    break;
                case "minScale":
                    legendRawInfo.setMinScale(toDouble(reader.getValue()));
                    break;
                case "maxScale":
                    legendRawInfo.setMaxScale(toDouble(reader.getValue()));
                    break;
            }
            reader.moveUp();
        }
        return legendRawInfo;
    }

    @Override
    public boolean canConvert(Class type) {
        return type.equals(LegendsRawInfo.class);
    }
}
