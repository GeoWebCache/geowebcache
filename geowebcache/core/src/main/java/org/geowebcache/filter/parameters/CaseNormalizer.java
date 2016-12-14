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
 */
package org.geowebcache.filter.parameters;

import java.util.Locale;
import java.io.Serializable;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Normalizes the case of strings based on a particular locale.
 * 
 * @author Kevin Smith, Boundless
 *
 */
public class CaseNormalizer implements Function<String, String>, Serializable, Cloneable {
    /**
     * Ways to normalize case
     * 
     * @author Kevin Smith, Boundless
     *
     */
    public static enum Case {
        /**
         * Preserve case
         */
        NONE(){
            @Override
            public String apply(String input, Locale loc) {
                return input;
            }
            
        },
        /**
         * Upper case
         */
        UPPER {
            @Override
            public String apply(String input, Locale loc) {
                return input.toUpperCase(loc);
            }
        },
        /**
         * Upper case
         */
        LOWER {
            @Override
            public String apply(String input, Locale loc) {
                return input.toLowerCase(loc);
            }
        }
        ;
        /**
         * Normalize the case of the given string according to the rules of the given locale
         * @param input string to normalize
         * @param loc locale to use for case changes
         * @return
         */
        abstract public String apply(String input, Locale loc);
    }
    
    @XStreamAlias("case")
    Case kase;
    Locale locale;
    
    /**
     * Create a Case Normalizer with default case and locale
     */
    public CaseNormalizer() {
        this((Case) null);
    }
    
    /**
     * Create a Case Normalizer with the given case and default locale
     */
    public CaseNormalizer(Case kase) {
        this(kase, (Locale) null);
    }
    
    /**
     * Create a Case Normalizer with the given case and locale
     */
    public CaseNormalizer(Case kase, Locale locale) {
        super();
        this.kase = kase;
        this.locale = locale;
    }
    
    /**
     * Apply normalisation to given string.  Guaranteed to be idempotent.
     * @param input
     * @return The normalised string.
     */
    public String apply(String input) {
        return getCase().apply(input, getLocale());
    }
    
    /**
     * Get the case
     * @return
     */
    public Case getCase() {
        if(kase==null) {
            return Case.NONE;
        } else {
            return kase;
        }
    }
    
    /**
     * Set the case
     * @param kase
     */
    public void setCase(Case kase) {
        this.kase = kase;
    }
    
    /**
     * Get the locale.  If unset, the default locale will be returned.
     * @return
     */
    public Locale getLocale() {
        if (locale==null) {
            return Locale.getDefault();
        } else {
            return locale;
        }
    }
    
    /**
     * Get the locale.  If unset, returns {@code null}.
     * @return
     */
    public @Nullable Locale getConfiguredLocale() {
        return locale;
    }
    
    /**
     * Set the locale.
     * @param locale The locale.  {@code} to set to the default.
     */
    public void setConfiguredLocale(@Nullable Locale locale) {
        this.locale = locale;
    }
    
    @Override
    public CaseNormalizer clone() {
        return new CaseNormalizer(kase, locale);
    }
}
