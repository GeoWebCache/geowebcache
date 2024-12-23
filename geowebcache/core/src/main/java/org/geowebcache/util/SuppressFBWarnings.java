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
 * @author Andrea Aime / GeoSolutions 2019
 */
package org.geowebcache.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows to suppress SpotBugs/FindBugs warnings without depending on external jars, the tools match by annotatin name
 */
@Retention(RetentionPolicy.CLASS)
public @interface SuppressFBWarnings {
    /**
     * The set of FindBugs warnings that are to be suppressed in annotated element. The value can be a bug category,
     * kind or pattern.
     */
    String[] value() default {};

    /** Optional documentation of the reason why the warning is suppressed */
    String justification() default "";
}
