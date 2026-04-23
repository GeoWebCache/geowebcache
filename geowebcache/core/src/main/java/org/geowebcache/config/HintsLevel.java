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
 * <p>Copyright 2026
 */
package org.geowebcache.config;

import java.awt.RenderingHints;
import java.util.Map;

/** Enum storing the Hints associated to one of the 3 configurations(SPEED, QUALITY, DEFAULT) */
public enum HintsLevel {
    QUALITY(0, "quality"),
    DEFAULT(1, "default"),
    SPEED(2, "speed");

    @SuppressWarnings("ImmutableEnumChecker") // RenderingHints is mutable
    private final RenderingHints hints;

    private final String mode;

    HintsLevel(int numHint, String mode) {
        this.mode = mode;
        switch (numHint) {
                // QUALITY HINTS
            case 0:
                hints = new RenderingHints(
                        RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                hints.add(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC));
                hints.add(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON));
                hints.add(new RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE));
                break;
                // DEFAULT HINTS
            case 1:
                hints = new RenderingHints(
                        RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_DEFAULT);
                hints.add(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_DEFAULT));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR));
                hints.add(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT));
                hints.add(new RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT));
                break;
                // SPEED HINTS
            case 2:
                hints = new RenderingHints(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
                hints.add(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR));
                hints.add(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED));
                hints.add(new RenderingHints(
                        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF));
                hints.add(new RenderingHints(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE));
                break;
            default:
                hints = null;
        }
    }

    public RenderingHints getRenderingHints() {
        if (hints == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        RenderingHints copy = new RenderingHints((Map) hints);
        return copy;
    }

    public String getModeName() {
        return mode;
    }

    public static HintsLevel getHintsForMode(String mode) {

        if (mode != null) {
            if (mode.equalsIgnoreCase(QUALITY.getModeName())) {
                return QUALITY;
            } else if (mode.equalsIgnoreCase(SPEED.getModeName())) {
                return SPEED;
            } else {
                return DEFAULT;
            }
        } else {
            return DEFAULT;
        }
    }
}
