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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** Filter to select the closest floating point value within a threshold. */
@ParametersAreNonnullByDefault
@XStreamAlias("floatParameterFilter")
public class FloatParameterFilter extends ParameterFilter {

    @Serial
    private static final long serialVersionUID = 4186888723396139208L;

    private static Float DEFAULT_THRESHOLD = Float.valueOf(1E-6f);

    // These members get set by XStream
    private List<Float> values;

    private Float threshold;

    public FloatParameterFilter() {
        super();
        values = new ArrayList<>(0);
    }

    protected @Override Object readResolve() {
        super.readResolve();
        if (values == null) {
            values = new ArrayList<>(0);
        }
        if (threshold == null) {
            threshold = getDefaultThreshold();
        }
        for (Float value : values) {
            Preconditions.checkNotNull(value, "Value list included a null pointer.");
        }
        return this;
    }

    /** @return */
    protected Float getDefaultThreshold() {
        return DEFAULT_THRESHOLD;
    }

    /**
     * @return the values the parameter can take. Altering this list is deprecated and in future it will be
     *     unmodifiable; use {@link #setValues(List)} instead.
     */
    public List<Float> getValues() {
        // TODO: apply Collections.unmodifiableList(...)
        return values;
    }

    /** Set the values */
    public void setValues(List<Float> values) {
        Preconditions.checkNotNull(values);
        for (Float f : values) {
            Preconditions.checkNotNull(f);
        }

        this.values = new ArrayList<>(values);
    }

    /** @return the threshold */
    public Float getThreshold() {
        return threshold;
    }

    /** @param threshold the threshold to set */
    public void setThreshold(@Nullable Float threshold) {
        if (threshold == null) threshold = getDefaultThreshold();
        this.threshold = threshold;
    }

    @Override
    public String apply(@Nullable String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return getDefaultValue();
        }

        float val = Float.parseFloat(str);
        if (values.isEmpty()) {
            // this is an accept all filter. At least it will match 2.0 and 2.000 as the same value
            return String.valueOf(val);
        }

        float best = Float.MIN_VALUE;

        float bestMismatch = Float.MAX_VALUE;

        Iterator<Float> iter = getValues().iterator();
        while (iter.hasNext()) {
            Float fl = iter.next();

            float mismatch = Math.abs(fl - val);
            if (mismatch < bestMismatch) {
                best = fl;
                bestMismatch = mismatch;
            }
        }

        if (threshold != null && threshold > 0 && Math.abs(bestMismatch) < threshold) {
            return Float.toString(best);
        }

        throw new ParameterException("Closest match for "
                + super.getKey()
                + "="
                + str
                + " is "
                + best
                + ", but this exceeds the threshold of "
                + threshold);
    }

    @Override
    public @Nullable List<String> getLegalValues() {
        List<String> ret = new LinkedList<>();

        Iterator<Float> iter = getValues().iterator();
        while (iter.hasNext()) {
            ret.add(Float.toString(iter.next()));
        }

        return ret;
    }

    @Override
    public FloatParameterFilter clone() {
        FloatParameterFilter clone = (FloatParameterFilter) super.clone();
        if (values != null) {
            clone.values = new ArrayList<>(values);
        }
        return clone;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((threshold == null) ? 0 : threshold.hashCode());
        result = prime * result + ((values == null) ? 0 : values.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        FloatParameterFilter other = (FloatParameterFilter) obj;
        if (threshold == null) {
            if (other.threshold != null) return false;
        } else if (!threshold.equals(other.threshold)) return false;
        if (values == null) {
            if (other.values != null) return false;
        } else if (!values.equals(other.values)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "FloatParameterFilter [values=" + values + ", threshold=" + threshold + ", " + super.toString() + "]";
    }
}
