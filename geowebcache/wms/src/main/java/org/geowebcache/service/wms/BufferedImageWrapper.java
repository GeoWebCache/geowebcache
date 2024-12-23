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
 * @author Joseph Miller, Copyright 2022
 */
package org.geowebcache.service.wms;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

class BufferedImageWrapper {
    /** Mosaic image */
    protected BufferedImage canvas;
    /** Graphics object used for drawing the tiles into a mosaic */
    protected Graphics2D gfx;

    private int[] canvasSize;
    private int canvasType;
    private Color bgColor;
    private RenderingHints hints;

    public BufferedImageWrapper(int[] canvasSize, int canvasType, Color bgColor, RenderingHints hints) {
        this.canvasSize = canvasSize;
        this.canvasType = canvasType;
        this.bgColor = bgColor;
        this.hints = hints;
    }

    public void drawImage(BufferedImage tileImg, int canvasx, int canvasy) {
        getGraphics().drawImage(tileImg, canvasx, canvasy, null);
    }

    public void disposeGraphics() {
        if (gfx != null) {
            gfx.dispose();
        }
    }

    public BufferedImage getCanvas() {
        if (canvas == null) {
            canvas = new BufferedImage(canvasSize[0], canvasSize[1], canvasType);
            gfx = (Graphics2D) canvas.getGraphics();

            if (bgColor != null) {
                gfx.setColor(bgColor);
                gfx.fillRect(0, 0, canvasSize[0], canvasSize[1]);
            }

            // Hints settings
            RenderingHints hintsTemp = WMSTileFuser.HintsLevel.DEFAULT.getRenderingHints();

            if (hints != null) {
                hintsTemp = hints;
            }
            gfx.addRenderingHints(hintsTemp);
        }
        return canvas;
    }

    public void updateCanvas(BufferedImage canvas) {
        this.canvas = canvas;
    }

    public Graphics2D getGraphics() {
        if (gfx == null) {
            gfx = (Graphics2D) getCanvas().getGraphics();
        }
        if (bgColor != null) {
            gfx.setColor(bgColor);
            gfx.fillRect(0, 0, canvasSize[0], canvasSize[1]);
        }
        return gfx;
    }
}
