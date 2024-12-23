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

import java.awt.image.RenderedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.geowebcache.mime.MimeType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Class used for containing all the ImageEncoder implementations in a map. The user should only call the encode()
 * method and internally it uses the writer associated to the input mimetype.
 */
public class ImageEncoderContainer implements ApplicationContextAware {
    /** Collection of all the ImageEncoder interface implementation */
    private Collection<ImageEncoder> encoders;
    /** Map of all the encoders for mimetype */
    private Map<String, ImageEncoder> mapEncoders;

    public ImageEncoderContainer() {}

    public void encode(
            RenderedImage image,
            MimeType mimeType,
            Object destination,
            boolean aggressiveOutputStreamOptimization,
            Map<String, Object> map)
            throws Exception {
        if (mapEncoders == null) {
            throw new IllegalArgumentException("ApplicationContext must be set before encoding");
        }
        mapEncoders
                .get(mimeType.getMimeType())
                .encode(image, destination, aggressiveOutputStreamOptimization, mimeType, map);
    }

    public boolean isAggressiveOutputStreamSupported(String mimeType) {
        if (mapEncoders == null) {
            throw new IllegalArgumentException(
                    "ApplicationContext must be set before checking the AggressiveOutputStrean support");
        }
        return mapEncoders.get(mimeType).isAggressiveOutputStreamSupported();
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        encoders = context.getBeansOfType(ImageEncoder.class).values();
        if (encoders == null || encoders.isEmpty()) {
            throw new IllegalArgumentException("No Encoder found");
        }

        mapEncoders = new HashMap<>();

        for (ImageEncoder encoder : encoders) {

            List<String> supportedMimeTypes = encoder.getSupportedMimeTypes();

            for (String mimeType : supportedMimeTypes) {
                if (!mapEncoders.containsKey(mimeType)) {
                    mapEncoders.put(mimeType, encoder);
                }
            }
        }
    }
}
