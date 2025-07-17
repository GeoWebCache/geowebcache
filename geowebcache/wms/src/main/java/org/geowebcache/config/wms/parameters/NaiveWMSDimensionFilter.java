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
 * @author Björn Hartell, Copyright 2010
 */
package org.geowebcache.config.wms.parameters;

import com.google.common.base.Preconditions;
import java.io.Serial;
import java.util.List;
import java.util.Objects;
import org.geotools.ows.wms.xml.Dimension;
import org.geotools.ows.wms.xml.Extent;
import org.geowebcache.filter.parameters.ParameterException;
import org.geowebcache.filter.parameters.ParameterFilter;

/**
 * This class is used to forward information about WMS 1.1.x dimensions from the getcapabilities configuration to GWC
 * clients.
 *
 * <p>It is naive in the sense that it does not really parse or understand the values it is dealing with, anything is
 * accepted.
 */
public class NaiveWMSDimensionFilter extends ParameterFilter implements WMSDimensionProvider {

    @Serial
    private static final long serialVersionUID = 8217550988333856916L;

    private Dimension dimension;

    private Extent extent;

    public NaiveWMSDimensionFilter(Dimension dimension, Extent extent) {
        Preconditions.checkNotNull(dimension);
        Preconditions.checkNotNull(extent);
        this.dimension = dimension;
        this.extent = extent;

        String keyName = dimension.getName();

        if (keyName.compareToIgnoreCase("time") != 0 && keyName.compareToIgnoreCase("elevation") != 0) {
            keyName = "dim_" + keyName;
        }

        this.setKey(keyName);
        this.setDefaultValue(extent.getDefaultValue());
    }

    @Override
    public String apply(String str) throws ParameterException {
        if (str == null || str.isEmpty()) return getDefaultValue();
        return str;
    }

    @Override
    public List<String> getLegalValues() {
        return null;
    }

    @Override
    public void appendDimensionElement(StringBuilder str, String indent) {
        str.append(indent).append("<Dimension name=\"").append(dimension.getName());
        str.append("\" units=\"").append(dimension.getUnits()).append("\"></Dimension>\n");
    }

    @Override
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
        return ((o instanceof NaiveWMSDimensionFilter nwmsdf) && super.equals(o))
                && equals(dimension, nwmsdf.dimension)
                && equals(extent, nwmsdf.extent);
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = 31 * hashCode + hashCode(dimension);
        hashCode = 31 * hashCode + hashCode(extent);
        return hashCode;
    }

    @Override
    public NaiveWMSDimensionFilter clone() {
        return new NaiveWMSDimensionFilter(dimension, extent);
    }

    // Extent does not implement equals, so do it here
    // protected String name;
    // protected String defaultValue;
    // protected boolean nearestValue = false;
    // protected boolean multipleValues;
    // protected boolean current = false;
    private boolean equals(Extent a, Extent b) {
        if (a == null || b == null) return a == b;
        return Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getDefaultValue(), b.getDefaultValue())
                && Objects.equals(a.getNearestValue(), b.getNearestValue())
                && Objects.equals(a.isMultipleValues(), b.isMultipleValues())
                && Objects.equals(a.isCurrent(), b.isCurrent());
    }

    // Extent does not implement hashCode, so do it here
    // protected String name;
    // protected String defaultValue;
    // protected boolean nearestValue = false;
    // protected boolean multipleValues;
    // protected boolean current = false;
    private int hashCode(Extent a) {
        return a == null
                ? 1
                : Objects.hash(
                        a.getName(), a.getDefaultValue(), a.getNearestValue(), a.isMultipleValues(), a.isCurrent());
    }

    // Dimension does not implement equals, so do it here
    // protected String name;
    // protected String units;
    // protected String unitSymbol;
    // protected boolean current;
    // protected Extent extent = null;
    private boolean equals(Dimension a, Dimension b) {
        if (a == null || b == null) return a == b;
        return Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getUnits(), b.getUnits())
                && Objects.equals(a.getUnitSymbol(), b.getUnitSymbol())
                && Objects.equals(a.isCurrent(), b.isCurrent())
                && equals(a.getExtent(), b.getExtent());
    }

    // Dimension does not implement hashCode, so do it here
    // protected String name;
    // protected String units;
    // protected String unitSymbol;
    // protected boolean current;
    // protected Extent extent = null;
    private int hashCode(Dimension a) {
        int hash = Objects.hash(a.getName(), a.getUnits(), a.getUnitSymbol(), a.isCurrent());
        return 31 * hash + hashCode(a.getExtent());
    }

    @Override
    public String toString() {
        return "NaiveWMSDimensionFilter [dimension=" + dimension + ", extent=" + extent + "]";
    }
}
