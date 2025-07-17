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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.filter.parameters;

import com.google.common.base.Preconditions;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@XStreamAlias("stringParameterFilter")
public class StringParameterFilter extends CaseNormalizingParameterFilter {

    @Serial
    private static final long serialVersionUID = 7383381085250203901L;

    private List<String> values;

    public StringParameterFilter() {
        values = new ArrayList<>();
    }

    protected @Override Object readResolve() {
        super.readResolve();
        if (values == null) {
            values = new ArrayList<>();
        }
        for (String value : values) {
            Preconditions.checkNotNull(value, "Value list included a null pointer.");
        }
        return this;
    }

    @Override
    public String apply(@Nullable String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return getDefaultValue();
        }

        str = getNormalize().apply(str);

        if (getLegalValues().contains(str)) {
            return str;
        }

        throw new ParameterException(str + " violates filter for parameter " + getKey());
    }

    /** @return the values the parameter can take. */
    @Override
    public List<String> getValues() {
        return Collections.unmodifiableList(values);
    }

    /** Set the values the parameter can take */
    public void setValues(List<String> values) {
        Preconditions.checkNotNull(values);
        for (String value : values) {
            Preconditions.checkNotNull(value, "Value list included a null pointer.");
        }
        this.values = new ArrayList<>(values);
    }

    /**
     * Checks whether a given parameter value applies to this filter.
     *
     * @param parameterValue the value to check if applies to this parameter filter
     * @return {@code true} if {@code parameterValue} is valid according to this filter, {@code false} otherwise
     */
    @Override
    public boolean applies(@Nullable String parameterValue) {
        return getLegalValues().contains(getNormalize().apply(parameterValue));
    }

    @Override
    public StringParameterFilter clone() {
        StringParameterFilter clone = (StringParameterFilter) super.clone();
        if (values != null) {
            clone.values = new ArrayList<>(values);
        }
        if (super.normalize != null) {
            clone.setNormalize(super.normalize.clone());
        }
        return clone;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((values == null) ? 0 : values.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        StringParameterFilter other = (StringParameterFilter) obj;
        if (values == null) {
            if (other.values != null) return false;
        } else if (!values.equals(other.values)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "StringParameterFilter [values=" + values + ", " + super.toString() + "]";
    }
}
