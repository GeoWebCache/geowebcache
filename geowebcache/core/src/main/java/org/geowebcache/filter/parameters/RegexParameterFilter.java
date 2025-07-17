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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.geowebcache.filter.parameters.CaseNormalizer.Case;

@ParametersAreNonnullByDefault
@XStreamAlias("regexParameterFilter")
public class RegexParameterFilter extends CaseNormalizingParameterFilter {

    @Serial
    private static final long serialVersionUID = -1496940509350980799L;

    public static final String DEFAULT_EXPRESSION = "";

    private String regex = DEFAULT_EXPRESSION;

    private transient Pattern pat;

    public RegexParameterFilter() {
        super();
        pat = compile(regex, getNormalize().getCase());
    }

    /** Get a {@link Matcher} for this filter's regexp against the given string. */
    public synchronized Matcher getMatcher(String value) {
        return pat.matcher(value);
    }

    static Pattern compile(String regex, Case c) {
        int flags = 0;
        if (c != Case.NONE) {
            flags += Pattern.CASE_INSENSITIVE;
            flags += Pattern.UNICODE_CASE;
        }
        return Pattern.compile(regex, flags);
    }

    protected @Override Object readResolve() {
        super.readResolve();
        Preconditions.checkNotNull(regex);
        this.pat = compile(regex, getNormalize().getCase());
        return this;
    }

    @Override
    public String apply(String str) throws ParameterException {
        if (str == null || str.length() == 0) {
            return getDefaultValue();
        }

        if (getMatcher(str).matches()) {
            return getNormalize().apply(str);
        }

        throw new ParameterException(str + " violates filter for parameter " + getKey());
    }

    @Override
    public @Nullable List<String> getLegalValues() {
        return null;
    }

    /**
     * Checks whether a given parameter value applies to this filter.
     *
     * @param parameterValue the value to check if applies to this parameter filter
     * @return {@code true} if {@code parameterValue} is valid according to this filter, {@code false} otherwise
     */
    @Override
    public boolean applies(@Nullable String parameterValue) {
        return getMatcher(parameterValue).matches();
    }

    /** @return the regex */
    public String getRegex() {
        return regex;
    }

    /** @param regex the regex to set. {@literal null} will be treated as default value. */
    public void setRegex(@Nullable String regex) {
        if (regex == null) regex = DEFAULT_EXPRESSION;
        this.regex = regex;
        this.pat = compile(this.regex, getNormalize().getCase());
    }

    @Override
    public void setNormalize(CaseNormalizer normalize) {
        super.setNormalize(normalize);
        this.pat = compile(this.regex, getNormalize().getCase());
    }

    @Override
    public RegexParameterFilter clone() {
        RegexParameterFilter clone = (RegexParameterFilter) super.clone();
        if (super.normalize != null) {
            clone.setNormalize(super.normalize.clone());
        }
        return clone;
    }

    @Override
    public List<String> getValues() {
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((regex == null) ? 0 : regex.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        RegexParameterFilter other = (RegexParameterFilter) obj;
        if (regex == null) {
            if (other.regex != null) return false;
        } else if (!regex.equals(other.regex)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "RegexParameterFilter [regex=" + regex + ", " + super.toString() + "]";
    }
}
