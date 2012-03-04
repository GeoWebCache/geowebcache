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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.filter.parameters;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class FloatParameterFilter extends ParameterFilter {

    private static final long serialVersionUID = 4186888723396139208L;

    private List<Float> values;

    private Float threshold;

    public FloatParameterFilter() {
        readResolve();
    }

    private FloatParameterFilter readResolve() {
        if (values == null) {
            values = new ArrayList<Float>(2);
        }
        if (threshold == null) {
            threshold = Float.valueOf(1E-6f);
        }
        return this;
    }

    /**
     * @return the values
     */
    public List<Float> getValues() {
        return values;
    }

    /**
     * @return the threshold
     */
    public Float getThreshold() {
        return threshold;
    }

    /**
     * @param threshold
     *            the threshold to set
     */
    public void setThreshold(Float threshold) {
        this.threshold = threshold;
    }

    public String apply(String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return "";
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

        throw new ParameterException("Closest match for " + super.getKey() + "=" + str + " is "
                + Float.toString(best) + ", but this exceeds the threshold of "
                + Float.toString(threshold));
    }

    public List<String> getLegalValues() {
        List<String> ret = new LinkedList<String>();

        Iterator<Float> iter = getValues().iterator();
        while (iter.hasNext()) {
            ret.add(Float.toString(iter.next()));
        }

        return ret;
    }

    @Override
    public FloatParameterFilter clone() {
        FloatParameterFilter clone = new FloatParameterFilter();
        clone.setDefaultValue(getDefaultValue());
        clone.setKey(getKey());
        if (values != null) {
            clone.values = new ArrayList<Float>(values);
        }
        return clone;
    }
}
