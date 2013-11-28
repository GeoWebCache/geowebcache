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
 * @author Björn Hartell, Copyright 2010
 * 
 */
package org.geowebcache.filter.parameters;

import java.util.List;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.geotools.data.wms.xml.Dimension;
import org.geotools.data.wms.xml.Extent;

import com.google.common.base.Preconditions;

/**
 * This class is used to forward information about WMS 1.1.x dimensions from the getcapabilities
 * configuration to GWC clients.
 * 
 * It is naive in the sense that it does not really parse or understand the values it is dealing
 * with, anything is accepted.
 */
public class NaiveWMSDimensionFilter extends ParameterFilter implements WMSDimensionProvider {

    private static final long serialVersionUID = 8217550988333856916L;

    private Dimension dimension;

    private Extent extent;

    public NaiveWMSDimensionFilter(Dimension dimension, Extent extent) {
        Preconditions.checkNotNull(dimension);
        Preconditions.checkNotNull(extent);
        this.dimension = dimension;
        this.extent = extent;

        String keyName = dimension.getName();

        if (keyName.compareToIgnoreCase("time") != 0
                && keyName.compareToIgnoreCase("elevation") != 0) {
            keyName = "dim_" + keyName;
        }

        this.setKey(keyName);
        this.setDefaultValue(extent.getDefaultValue());
    }

    public String apply(String str) throws ParameterException {
        if(str==null || str.isEmpty()) return getDefaultValue();
        return str;
    }

    public List<String> getLegalValues() {
        return null;
    }

    public void appendDimensionElement(StringBuilder str, String indent) {
        str.append(indent).append("<Dimension name=\"").append(dimension.getName());
        str.append("\" units=\"").append(dimension.getUnits()).append("\"></Dimension>\n");
    }

    public void appendExtentElement(StringBuilder str, String indent) {
        str.append(indent).append("<Extent name=\"" + extent.getName() + "\"");
        if (extent.getDefaultValue() != null) {
            str.append(" default=\"" + extent.getDefaultValue() + "\"");
        }
        str.append(">");

        if (extent.getValue() != null) {
            str.append(extent.getValue());
        }
        str.append("</Extent>\n");
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public NaiveWMSDimensionFilter clone() {
        return new NaiveWMSDimensionFilter(dimension, extent);
    }
}
