package org.geowebcache.diskquota;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSMetaTile;
import org.geowebcache.layer.wms.WMSSourceHelper;

/**
 * A bean that takes all WMS layers and sets them a <strong>mock</strong>
 * {@link WMSLayer#setSourceHelper(WMSSourceHelper) sourceHelper}, only for test purposes.
 * 
 * @author groldan
 * 
 */
public class MockWMSLayerSource {

    private static final Log log = LogFactory.getLog(MockWMSLayerSource.class);

    public MockWMSLayerSource(TileLayerDispatcher tld) {
        log
                .info("'\n---------------------------------------------------------------------------------\n"
                        + "Replacing all WMS layer backend helpers by a mock one, don't forget to remove this\n"
                        + "---------------------------------------------------------------------------------");

        for (TileLayer layer : tld.getLayers().values()) {
            if (layer instanceof WMSLayer) {
                ((WMSLayer) layer).setSourceHelper(fakeWMSSource);
            }
        }
    }

    private static WMSSourceHelper fakeWMSSource = new WMSSourceHelper() {

        private Font font = Font.decode("Arial-BOLD-14");

        private Map<List<Integer>, byte[]> images = new HashMap<List<Integer>, byte[]>();

        @Override
        protected byte[] makeRequest(TileResponseReceiver tileRespRecv, WMSLayer layer,
                String wmsParams, String expectedMimeType) throws GeoWebCacheException {
            long ts = System.currentTimeMillis();
            long[][] tiles;
            int tileW;
            int tileH;
            String format;
            if (tileRespRecv instanceof ConveyorTile) {
                ConveyorTile conveyorTile = (ConveyorTile) tileRespRecv;
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
                GridSubset gridSubset = layer.getGridSubsets().values().iterator().next();
                tileW = gridSubset.getTileWidth();
                tileH = gridSubset.getTileHeight();
            }
            Map<String, String> params = toRequestMap(wmsParams);
            int width = Integer.parseInt(params.get("WIDTH"));
            int height = Integer.parseInt(params.get("HEIGHT"));
            int tilesX = width / tileW;
            int tilesY = height / tileH;

            List<Integer> wh = Arrays.asList(width, height);

            byte[] result = images.get(wh);
            if (result == null) {
                synchronized (images) {
                    if ((result = images.get(wh)) == null) {
                        BufferedImage img;
                        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = img.createGraphics();
                        graphics.setColor(Color.LIGHT_GRAY);
                        graphics.fillRect(0, 0, width, height);
                        // graphics.setColor(Color.BLACK);
                        // graphics.setFont(font);
                        // int tileN = 0;
                        // for (int y = 0; y < tilesY; y++) {
                        // for (int x = 0; x < tilesX; x++) {
                        // long[] t = tiles[tileN];
                        // tileN++;
                        // int gx = x * tileW;
                        // int gy = y * tileH;
                        // gx += 15;
                        // gy += tileH / 2;
                        // graphics.drawString(Arrays.toString(t), gx, gy);
                        // }
                        // }
                        graphics.dispose();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try {
                            ImageIO.write(img, format, output);
                        } catch (IOException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                        result = output.toByteArray();
                        images.put(wh, result);
                    }
                }
            }
            // ts = System.currentTimeMillis() - ts;
            // System.err.println(tiles.length + " tiles generated in " + ts + "ms");
            return result;
        }

        private Map<String, String> toRequestMap(String wmsParams) {
            String[] split = wmsParams.split("&");
            Map<String, String> params = new HashMap<String, String>();
            for (String s : split) {
                int index = s.indexOf('=');
                if (index > 0) {
                    params.put(s.substring(0, index), s.substring(index + 1));
                }
            }
            return params;
        }

    };

}
