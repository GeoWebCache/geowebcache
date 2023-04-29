/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Smith, Boundless, Copyright 2015
 */
package org.geowebcache.filter.parameters;

import com.google.common.base.Function;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Normalizes the case of strings based on a particular locale.
 *
 * @author Kevin Smith, Boundless
 */
public class CaseNormalizer implements Function<String, String>, Serializable, Cloneable {
    private static final long serialVersionUID = -4175693577236472098L;

    /**
     * Ways to normalize case
     *
     * @author Kevin Smith, Boundless
     */
    public static enum Case {
        /** Preserve case */
        NONE() {
            @Override
            public String apply(String input, Locale loc) {
                return input;
            }
        },
        /** Upper case */
        UPPER {
            @Override
            public String apply(String input, Locale loc) {
                return input.toUpperCase(loc);
            }
        },
        /** Upper case */
        LOWER {
            @Override
            public String apply(String input, Locale loc) {
                return input.toLowerCase(loc);
            }
        };
        /**
         * Normalize the case of the given string according to the rules of the given locale
         *
         * @param input string to normalize
         * @param loc locale to use for case changes
         */
        public abstract String apply(String input, Locale loc);
    }

    @XStreamAlias("case")
    Case kase;

    Locale locale;

    /** Create a Case Normalizer with default case and locale */
    public CaseNormalizer() {
        this(null);
    }

    /** Create a Case Normalizer with the given case and default locale */
    public CaseNormalizer(Case kase) {
        this(kase, null);
    }

    /** Create a Case Normalizer with the given case and locale */
    public CaseNormalizer(Case kase, Locale locale) {
        super();
        this.kase = kase;
        this.locale = locale;
    }

    /**
     * Apply normalisation to given string. Guaranteed to be idempotent.
     *
     * @return The normalised string.
     */
    @Override
    public String apply(String input) {
        return getCase().apply(input, getLocale());
    }

    /** Get the case */
    public Case getCase() {
        if (kase == null) {
            return Case.NONE;
        } else {
            return kase;
        }
    }

    /** Set the case */
    public void setCase(Case kase) {
        this.kase = kase;
    }

    /** Get the locale. If unset, the default locale will be returned. */
    public Locale getLocale() {
        if (locale == null) {
            return Locale.getDefault();
        } else {
            return locale;
        }
    }

    /** Get the locale. If unset, returns {@code null}. */
    public @Nullable Locale getConfiguredLocale() {
        return locale;
    }

    /**
     * Set the locale.
     *
     * @param locale The locale. {@code} to set to the default.
     */
    public void setConfiguredLocale(@Nullable Locale locale) {
        this.locale = locale;
    }

    @Override
    public CaseNormalizer clone() {
        return new CaseNormalizer(kase, locale);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((kase == null) ? 0 : kase.hashCode());
        result = prime * result + ((locale == null) ? 0 : locale.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        CaseNormalizer other = (CaseNormalizer) obj;
        return Objects.equals(kase, other.kase) && Objects.equals(locale, other.locale);
    }

    @Override
    public String toString() {
        return "CaseNormalizer [kase=" + kase + ", locale=" + locale + "]";
    }
}
