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
 * @author Nicola Lagomarsini, GeoSolutions S.A.S., Copyright 2014
 */
package org.geowebcache.io.codec;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Class used for containing all the ImageDecoder implementations in a map. The user should only
 * call the decode() method and internally it uses the reader associated to the input mimetype.
 */
public class ImageDecoderContainer implements ApplicationContextAware {
    /** Collection of all the ImageDecoder interface implementation */
    private Collection<ImageDecoder> decoders;
    /** Map of all the decoders for mimetype */
    private Map<String, ImageDecoder> mapDecoders;

    public ImageDecoderContainer() {}

    public BufferedImage decode(
            String mimeType,
            Object input,
            boolean aggressiveInputStreamOptimization,
            Map<String, Object> map)
            throws Exception {
        if (mapDecoders == null) {
            throw new IllegalArgumentException("ApplicationContext must be set before decoding");
        }
        return mapDecoders.get(mimeType).decode(input, aggressiveInputStreamOptimization, map);
    }

    public boolean isAggressiveInputStreamSupported(String mimeType) {
        if (mapDecoders == null) {
            throw new IllegalArgumentException(
                    "ApplicationContext must be set before checking the AggressiveInputStrean support");
        }
        return mapDecoders.get(mimeType).isAggressiveInputStreamSupported();
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        decoders = context.getBeansOfType(ImageDecoder.class).values();
        if (decoders == null || decoders.isEmpty()) {
            throw new IllegalArgumentException("No Encoder found");
        }

        mapDecoders = new HashMap<>();

        for (ImageDecoder encoder : decoders) {

            List<String> supportedMimeTypes = encoder.getSupportedMimeTypes();

            for (String mimeType : supportedMimeTypes) {
                if (!mapDecoders.containsKey(mimeType)) {
                    mapDecoders.put(mimeType, encoder);
                }
            }
        }
    }
}
