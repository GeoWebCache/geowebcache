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
 * @author Cécile Vuilleumier, Camptocamp, Copyright 2026
 */
package org.geowebcache.rest.negotiation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Spring ContentNegotiationStrategy for GeoWebCache
 *
 * <p>Reads the media type stored by {@link org.geowebcache.rest.filter.SuffixStripFilter}
 */
public class SuffixContentNegotiationStrategy implements ContentNegotiationStrategy {

    public static final String FORMAT_ATTRIBUTE = "gwc.formatExtension";

    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest request) {
        HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
        if (servletRequest != null) {
            // Check if filter stored the extension
            String extension = (String) servletRequest.getAttribute(FORMAT_ATTRIBUTE);

            if (extension != null) {
                if ("json".equals(extension)) {
                    return Collections.singletonList(MediaType.APPLICATION_JSON);
                } else if ("xml".equals(extension)) {
                    return Collections.singletonList(MediaType.APPLICATION_XML);
                }
            }
        }
        return Collections.emptyList();
    }
}
