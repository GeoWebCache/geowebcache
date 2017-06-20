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
 * @author Kevin Smith, Boundless, Copyright 2015
 * 
 * Based on FloatParameterFilter: Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.filter.parameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Preconditions;
import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Filter to select the closest Integer value within a threshold.
 */
@ParametersAreNonnullByDefault
@XStreamAlias("integerParameterFilter")
public class IntegerParameterFilter extends ParameterFilter {
    
    
    private static Integer DEFAULT_THRESHOLD = Integer.valueOf(1);

    // These members get set by XStream
    private List<Integer> values;

    private Integer threshold;


    public IntegerParameterFilter() {
        super();
        values = new ArrayList<Integer>(0);
    }

    protected IntegerParameterFilter readResolve() {
        super.readResolve();
        if (values == null) {
            values = new ArrayList<Integer>(0);
        }
        if (threshold == null) {
            threshold = getDefaultThreshold();
        }
        for(Integer value: values) {
            Preconditions.checkNotNull(value, "Value list included a null pointer.");
        }
        return this;
    }

    /**
     * @return
     */
    protected Integer getDefaultThreshold() {
        return DEFAULT_THRESHOLD;
    }

    /**
     * @return the values the parameter can take.  Altering this list is deprecated and in future 
     * it will be unmodifiable; use {@link setValues} instead.
     */
    public List<Integer> getValues() {
        // TODO: apply Collections.unmodifiableList(...)
        return values;
    }

    /**
     *  Set the values
     */
    public void setValues(List<Integer> values) {
        Preconditions.checkNotNull(values);
        for(Integer f: values) {
            Preconditions.checkNotNull(f);
        }
        
        this.values = new ArrayList<Integer>(values);
    }

    /**
     * @return the threshold
     */
    public Integer getThreshold() {
        return threshold;
    }

    /**
     * @param threshold
     *            the threshold to set
     */
    public void setThreshold(@Nullable Integer threshold) {
        if(threshold==null) threshold = getDefaultThreshold();
        this.threshold = threshold;
    }

    @Override
    public String apply(@Nullable String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return getDefaultValue();
        }

        int val = Integer.parseInt(str);
        if (values.isEmpty()) {
            // this is an accept all filter. At least it will match 2.0 and 2.000 as the same value
            return String.valueOf(val);
        }

        int best = Integer.MIN_VALUE;

        int bestMismatch = Integer.MAX_VALUE;

        Iterator<Integer> iter = getValues().iterator();
        while (iter.hasNext()) {
            int fl = iter.next();

            int mismatch = Math.abs(fl - val);
            if (mismatch < bestMismatch) {
                best = fl;
                bestMismatch = mismatch;
            }
        }

        if (threshold != null && threshold > 0 && Math.abs(bestMismatch) < threshold) {
            return Integer.toString(best);
        }

        throw new ParameterException("Closest match for " + super.getKey() + "=" + str + " is "
                + Integer.toString(best) + ", but this exceeds the threshold of "
                + Integer.toString(threshold));
    }

    @Override
    public @Nullable List<String> getLegalValues() {
        List<String> ret = new LinkedList<String>();

        Iterator<Integer> iter = getValues().iterator();
        while (iter.hasNext()) {
            ret.add(Integer.toString(iter.next()));
        }

        return ret;
    }

    @Override
    public IntegerParameterFilter clone() {
        IntegerParameterFilter clone = new IntegerParameterFilter();
        clone.setDefaultValue(getDefaultValue());
        clone.setKey(getKey());
        if (values != null) {
            clone.values = new ArrayList<Integer>(values);
        }
        clone.setThreshold(this.threshold);
        return clone;
    }
}
