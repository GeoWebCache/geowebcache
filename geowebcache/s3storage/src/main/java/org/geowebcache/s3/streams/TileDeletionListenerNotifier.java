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
 * <p>Copyright 2025
 */
package org.geowebcache.s3.streams;

import static com.google.common.base.Preconditions.checkNotNull;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.util.TMSKeyBuilder;

/**
 * TileDeletionListenerNotifier is responsible for informing BlobStoreListeners that a tile has been deleted. The method
 * is called when the
 */
public class TileDeletionListenerNotifier implements Consumer<List<S3ObjectSummary>> {
    public static final String LAYER_GROUP_POS = "layer";
    public static final String GRID_SET_ID_GROUP_POS = "gridSetId";
    public static final String FORMAT_GROUP_POS = "format";
    public static final String PARAMETERS_ID_GROUP_POS = "parametersId";
    public static final String X_GROUP_POS = "x";
    public static final String Y_GROUP_POS = "y";
    public static final String Z_GROUP_POS = "z";

    public static final Pattern keyRegex = Pattern.compile(
            "((?<prefix>.+)/)?(?<layer>.+)/(?<gridSetId>.+)/(?<format>.+)/(?<parametersId>.+)/(?<z>\\d+)/(?<x>\\d+)/(?<y>\\d+)\\.(?<extension>.+)");

    private final BlobStoreListenerList listenerList;
    private final TMSKeyBuilder keyBuilder;

    public TileDeletionListenerNotifier(BlobStoreListenerList listenerList, TMSKeyBuilder keyBuilder, Logger logger) {
        checkNotNull(listenerList, "listenerList cannot be null");
        checkNotNull(keyBuilder, "keyBuilder cannot be null");
        checkNotNull(logger, "logger cannot be null");

        this.listenerList = listenerList;
        this.logger = logger;
        this.keyBuilder = keyBuilder;
    }

    private final Logger logger;

    @Override
    public void accept(List<S3ObjectSummary> tileObjectList) {
        if (tileObjectList == null || tileObjectList.isEmpty()) {
            logger.fine("There are no tiles successfully deleted in this batch");
            return;
        }

        if (listenerList.isEmpty()) {
            logger.fine("There are no listeners to be notified");
            return;
        }

        // All the S3Objects are from the same layer
        String layerName = null;
        long count = 0;
        for (S3ObjectSummary s3ObjectSummary : tileObjectList) {
            Matcher matcher = keyRegex.matcher(s3ObjectSummary.getKey());
            if (matcher.matches()) {
                String layerId = matcher.group(LAYER_GROUP_POS);
                String gridSetId = matcher.group(GRID_SET_ID_GROUP_POS);
                String extension = matcher.group(FORMAT_GROUP_POS);
                String parametersId = matcher.group(PARAMETERS_ID_GROUP_POS);
                long x = Long.parseLong(matcher.group(X_GROUP_POS));
                long y = Long.parseLong(matcher.group(Y_GROUP_POS));
                int z = Integer.parseInt(matcher.group(Z_GROUP_POS));

                if (layerName == null) {
                    layerName = keyBuilder.layerNameFromId(layerId);
                    if (layerName == null) {
                        logger.warning("No layer found for id " + layerId
                                + "skipping tile listener notification as the tiles will not match");
                        return;
                    }
                }

                if (Objects.equals(parametersId, "default")) {
                    parametersId = null;
                }

                MimeType mimeType = getMimeType(extension);
                if (mimeType == null) {
                    logger.warning("Unknown extension " + extension + " cannot match a mimetype");
                    continue;
                }

                listenerList.sendTileDeleted(
                        layerName, gridSetId, mimeType.getMimeType(), parametersId, x, y, z, s3ObjectSummary.getSize());
            } else {
                logger.warning("Key is in an invalid format " + s3ObjectSummary.getKey());
            }
        }
        logger.fine("Notified " + count + " tiles successfully deleted from a batch of " + tileObjectList.size());
    }

    private MimeType getMimeType(String extension) {
        MimeType mimeType = null;
        try {
            mimeType = MimeType.createFromExtension(extension);
        } catch (MimeException e) {
            logger.warning("Unable to parse find mime type for extension " + extension);
        }
        return mimeType;
    }
}
