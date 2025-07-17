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
 * <p>Copyright 2019
 */
package org.geowebcache.filter.parameters;

import com.google.common.collect.Lists;
import java.io.Serial;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public abstract class CaseNormalizingParameterFilter extends ParameterFilter {
    @Serial
    private static final long serialVersionUID = 1761619452677321350L;

    protected CaseNormalizer normalize;

    public CaseNormalizingParameterFilter() {
        super();
    }

    public CaseNormalizingParameterFilter(String key, String defaultValue) {
        super(key, defaultValue);
    }

    public abstract List<String> getValues();

    public CaseNormalizingParameterFilter(String key) {
        super(key);
    }

    protected @Override Object readResolve() {
        super.readResolve();
        return this;
    }

    public CaseNormalizer getNormalize() {
        if (normalize != null) {
            return normalize;
        } else {
            return new CaseNormalizer();
        }
    }

    public void setNormalize(CaseNormalizer normalize) {
        this.normalize = normalize;
    }

    @Override
    public @Nullable List<String> getLegalValues() {
        List<String> values = getValues();
        if (values == null) {
            return null;
        } else {
            return Lists.transform(values, getNormalize());
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        // hashCode based on getNormalize() as it provides an transient default value when null
        result = prime * result + getNormalize().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        CaseNormalizingParameterFilter other = (CaseNormalizingParameterFilter) obj;
        // equals based on getNormalize() as it provides an transient default value when null
        return Objects.equals(getNormalize(), other.getNormalize());
    }

    @Override
    public String toString() {
        return "CaseNormalizingParameterFilter [normalize=" + normalize + ", " + super.toString() + "]";
    }
}
