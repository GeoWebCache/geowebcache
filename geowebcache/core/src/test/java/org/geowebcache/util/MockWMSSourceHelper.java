package org.geowebcache.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSMetaTile;
import org.geowebcache.layer.wms.WMSSourceHelper;
import org.geowebcache.mime.MimeType;

public class MockWMSSourceHelper extends WMSSourceHelper {
    private Map<List<Integer>, byte[]> images = new HashMap<>();

    @Override
    protected void makeRequest(
            TileResponseReceiver tileRespRecv,
            WMSLayer layer,
            Map<String, String> wmsParams,
            MimeType expectedMimeType,
            Resource target)
            throws GeoWebCacheException {
        long[][] tiles;
        int tileW;
        int tileH;
        String format;
        if (tileRespRecv instanceof ConveyorTile conveyorTile) {
            long[] tileIndex = conveyorTile.getTileIndex();
            tiles = new long[1][1];
            tiles[0] = tileIndex;
            format = conveyorTile.getMimeType().getInternalName().toUpperCase();
            tileW = conveyorTile.getGridSubset().getTileWidth();
            tileH = conveyorTile.getGridSubset().getTileHeight();
        } else {
            WMSMetaTile metaTile = (WMSMetaTile) tileRespRecv;
            tiles = metaTile.getTilesGridPositions();
            format = metaTile.getResponseFormat().getInternalName().toUpperCase();
            GridSubset gridSubset =
                    layer.getGridSubset(layer.getGridSubsets().iterator().next());
            tileW = gridSubset.getTileWidth();
            tileH = gridSubset.getTileHeight();
        }
        int width = Integer.parseInt(wmsParams.get("WIDTH"));
        int height = Integer.parseInt(wmsParams.get("HEIGHT"));
        int tilesX = width / tileW;
        int tilesY = height / tileH;

        List<Integer> wh = Arrays.asList(width, height);

        byte[] result = images.get(wh);
        if (result == null) {
            synchronized (images) {
                if ((result = images.get(wh)) == null) {
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = img.createGraphics();
                    graphics.setColor(Color.LIGHT_GRAY);
                    graphics.fillRect(0, 0, width, height);

                    for (int y = 0; y < tilesY; y++) {
                        for (int x = 0; x < tilesX; x++) {
                            int gx = x * tileW;
                            int gy = y * tileH;
                            // gx += 15;
                            // gy += tileH / 2;
                            // graphics.drawString(Arrays.toString(t), gx, gy);
                            graphics.setColor(Color.RED);
                            graphics.setStroke(new BasicStroke(2));
                            graphics.drawRect(gx + 1, gy + 1, tileW - 2, tileH - 2);
                        }
                    }
                    graphics.dispose();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    try {
                        ImageIO.write(img, format, output);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    result = output.toByteArray();
                    images.put(wh, result);
                }
            }
        }
        try {
            target.transferFrom(Channels.newChannel(new ByteArrayInputStream(result)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // ts = System.currentTimeMillis() - ts;
        // System.err.println(tiles.length + " tiles generated in " + ts + "ms");
    }
}
