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
 */
package org.geowebcache.filter.parameters;

import com.google.common.base.Preconditions;
import com.thoughtworks.xstream.annotations.XStreamAlias;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
@XStreamAlias("freeStringParameterFilter")
public class FreeStringParameterFilter extends CaseNormalizingParameterFilter {

    private static final long serialVersionUID = -6953327458471056267L;

    public FreeStringParameterFilter() {
        super();
    }

    public FreeStringParameterFilter(String key, String defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public List<String> getValues() {
        return null;
    }

    @Override
    public FreeStringParameterFilter clone() {
        FreeStringParameterFilter clone = new FreeStringParameterFilter(getKey(), getDefaultValue());
        return clone;
    }

    @Override
    public String apply(@Nullable String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return getDefaultValue();
        }
        else {
            return getNormalize().apply(str);
        }
    }
}
