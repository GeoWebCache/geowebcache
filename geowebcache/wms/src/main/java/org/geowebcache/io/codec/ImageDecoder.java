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
 * @author Nicola Lagomarsini, GeoSolutions S.A.S., Copyright 2014
 */
package org.geowebcache.io.codec;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Interface for each decoder object. Each class implementing this interface can be added to the spring application
 * context as a bean and then will be automatically included in the class {@link ImageDecoderContainer}.
 */
public interface ImageDecoder {

    /** Returns the list of the supported mimetypes */
    public List<String> getSupportedMimeTypes();

    /** Decodes the selected input object. */
    public BufferedImage decode(Object input, boolean aggressiveInputStreamOptimization, Map<String, Object> map)
            throws Exception;

    /** Indicates if Aggressive inputStream is supported */
    public boolean isAggressiveInputStreamSupported();
}
