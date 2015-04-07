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

import com.google.common.base.Function;
import com.thoughtworks.xstream.annotations.XStreamAlias;

public class CaseNormalizer implements Function<String, String>{
    public static enum Case {
        NONE(){
            @Override
            public String apply(String input, Locale loc) {
                return input;
            }
            
        },
        UPPER {
            @Override
            public String apply(String input, Locale loc) {
                return input.toUpperCase(loc);
            }
        },
        LOWER {
            @Override
            public String apply(String input, Locale loc) {
                return input.toLowerCase(loc);
            }
        }
        ;
        abstract public String apply(String input, Locale loc);
    }
    
    @XStreamAlias("case")
    Case kase;
    Locale locale;
    
    public CaseNormalizer() {
        this((Case) null);
    }
    
    public CaseNormalizer(Case kase) {
        this(kase, (Locale) null);
    }
    
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
    
    public Case getCase() {
        if(kase==null) {
            return Case.NONE;
        } else {
            return kase;
        }
    }
    
    public Locale getLocale() {
        if (locale==null) {
            return Locale.getDefault();
        } else {
            return locale;
        }
    }
}
