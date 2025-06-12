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
 * @author Chris Whitney, Copyright 2011
 */
package org.geowebcache.grid;

import java.io.Serial;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

public class BoundingBox implements Serializable {

    @Serial
    private static final long serialVersionUID = -2555598825074884627L;

    private NumberFormat getCoordinateFormatter() {
        NumberFormat COORD_FORMATTER = NumberFormat.getNumberInstance(Locale.ENGLISH);

        COORD_FORMATTER.setMinimumFractionDigits(1);
        COORD_FORMATTER.setGroupingUsed(false);
        COORD_FORMATTER.setMaximumFractionDigits(16);
        return COORD_FORMATTER;
    }

    private static Logger log = Logging.getLogger(BoundingBox.class.getName());

    private static String DELIMITER = ",";

    private static double EQUALITYTHRESHOLD = 0.03;

    public static final BoundingBox WORLD4326 = new BoundingBox(-180.0, -90.0, 180.0, 90.0);

    public static final BoundingBox WORLD3857 = new BoundingBox(-20037508.34, -20037508.34, 20037508.34, 20037508.34);

    // exactly as defined in the OGC TMS specification
    public static final BoundingBox WORLD3857_TMS =
            new BoundingBox(-20037508.3427892, -20037508.3427892, 20037508.3427892, 20037508.3427892);

    // minx, miny, maxx, maxy
    private double[] coords = new double[4];

    BoundingBox() {
        // default constructor for XStream
    }

    public BoundingBox(BoundingBox bbox) {
        coords[0] = bbox.coords[0];
        coords[1] = bbox.coords[1];
        coords[2] = bbox.coords[2];
        coords[3] = bbox.coords[3];
    }

    public BoundingBox(String BBOX) {
        setFromBBOXString(BBOX, 0);
        if (log.isLoggable(Level.FINER)) {
            log.finer("Created BBOX: " + getReadableString());
        }
    }

    public BoundingBox(String[] BBOX) {
        setFromStringArray(BBOX);
        if (log.isLoggable(Level.FINER)) {
            log.finer("Created BBOX: " + getReadableString());
        }
    }

    public BoundingBox(double minx, double miny, double maxx, double maxy) {
        coords[0] = minx;
        coords[1] = miny;
        coords[2] = maxx;
        coords[3] = maxy;

        if (log.isLoggable(Level.FINER)) {
            log.finer("Created BBOX: " + getReadableString());
        }
    }

    public double getMinX() {
        return coords[0];
    }

    public void setMinX(double minx) {
        coords[0] = minx;
    }

    public double getMinY() {
        return coords[1];
    }

    public void setMinY(double miny) {
        coords[1] = miny;
    }

    public double getMaxX() {
        return coords[2];
    }

    public void setMaxX(double maxx) {
        coords[2] = maxx;
    }

    public double getMaxY() {
        return coords[3];
    }

    public void setMaxY(double maxy) {
        coords[3] = maxy;
    }

    /** @return [minx, miny, maxx, maxy] */
    public double[] getCoords() {
        return coords.clone();
    }

    public double getWidth() {
        return coords[2] - coords[0];
    }

    public double getHeight() {
        return coords[3] - coords[1];
    }

    /** Sets from an array of strings */
    public void setFromStringArray(String[] BBOX) {
        setFromStringArray(BBOX, 0);
    }

    public void setFromStringArray(String[] BBOX, int recWatch) {
        if (BBOX.length == 4) {
            coords[0] = Double.parseDouble(BBOX[0]);
            coords[1] = Double.parseDouble(BBOX[1]);
            coords[2] = Double.parseDouble(BBOX[2]);
            coords[3] = Double.parseDouble(BBOX[3]);

        } else if (recWatch < 4) {
            setFromBBOXString(BBOX[0], recWatch);
        } else {
            log.severe("Doesnt understand " + Arrays.toString(BBOX));
        }
    }

    /** Parses the BBOX parameters from a comma separted value list */
    public void setFromBBOXString(String BBOX, int recWatch) {
        String[] tokens = BBOX.split(DELIMITER);
        setFromStringArray(tokens, recWatch + 1);
    }

    /**
     * Outputs a string suitable for logging and other human-readable tasks
     *
     * @return a readable string
     */
    public String getReadableString() {
        return "Min X: " + coords[0] + " Min Y: " + coords[1] + " Max X: " + coords[2] + " Max Y: " + coords[3];
    }

    /** Returns a comma separated value String suitable for URL output */
    @Override
    public String toString() {
        NumberFormat formatter = getCoordinateFormatter();
        StringBuilder buff = new StringBuilder(40);
        buff.append(formatter.format(coords[0]));
        buff.append(',');
        buff.append(formatter.format(coords[1]));
        buff.append(',');
        buff.append(formatter.format(coords[2]));
        buff.append(',');
        buff.append(formatter.format(coords[3]));
        return buff.toString();
    }

    public String toKMLLatLonBox() {
        return "<LatLonBox>"
                + "<north>"
                + Double.toString(coords[3])
                + "</north>"
                + "<south>"
                + Double.toString(coords[1])
                + "</south>"
                + "<east>"
                + Double.toString(coords[2])
                + "</east>"
                + "<west>"
                + Double.toString(coords[0])
                + "</west>"
                + "</LatLonBox>";
    }

    public String toKMLLatLonAltBox() {
        return "<LatLonAltBox>"
                + "<north>"
                + Double.toString(coords[3])
                + "</north>"
                + "<south>"
                + Double.toString(coords[1])
                + "</south>"
                + "<east>"
                + Double.toString(coords[2])
                + "</east>"
                + "<west>"
                + Double.toString(coords[0])
                + "</west>"
                + "</LatLonAltBox>";
    }

    /**
     * Comparing whether the differences between the bounding boxes can be ignored.
     *
     * @return whether the boxes are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BoundingBox other) {
            return this.equals(other, EQUALITYTHRESHOLD);
        }
        return false;
    }

    public boolean equals(BoundingBox other, double threshold) {
        return Math.abs(getMinX() - other.getMinX()) < threshold
                && Math.abs(getMinY() - other.getMinY()) < threshold
                && Math.abs(getWidth() - other.getWidth()) < threshold
                && Math.abs(getHeight() - other.getHeight()) < threshold;
    }

    /**
     * Check whether this bbox contains the bbox
     *
     * @return whether other is contained by this
     */
    public boolean contains(BoundingBox other) {
        return (coords[0] - EQUALITYTHRESHOLD <= other.coords[0]
                && coords[1] - EQUALITYTHRESHOLD <= other.coords[1]
                && coords[2] + EQUALITYTHRESHOLD >= other.coords[2]
                && coords[3] + EQUALITYTHRESHOLD >= other.coords[3]);
    }

    /**
     * Minimal sanity check
     *
     * @return whether min x < max x, min y < max y
     */
    public boolean isSane() {
        return (coords[0] < coords[2] && coords[1] < coords[3]);
    }

    public boolean isNull() {
        return (coords[0] > coords[2] || coords[1] > coords[3]);
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits((float) coords[0]) ^ Float.floatToIntBits((float) coords[1]);
    }

    public boolean intersects(BoundingBox other) {
        if (isNull() || other.isNull()) {
            return false;
        }
        return !(other.getMinX() > getMaxX()
                || other.getMaxX() < getMinX()
                || other.getMinY() > getMaxY()
                || other.getMaxY() < getMinY());
    }

    @SuppressWarnings("AmbiguousMethodReference")
    public BoundingBox intersection(BoundingBox bboxB) {
        return BoundingBox.intersection(this, bboxB);
    }

    @SuppressWarnings("AmbiguousMethodReference")
    public static BoundingBox intersection(BoundingBox bboxA, BoundingBox bboxB) {
        BoundingBox retBbox = new BoundingBox(0, 0, -1, -1);
        if (bboxA.intersects(bboxB)) {
            for (int i = 0; i < 2; i++) {
                if (bboxA.coords[i] > bboxB.coords[i]) {
                    retBbox.coords[i] = bboxA.coords[i];
                } else {
                    retBbox.coords[i] = bboxB.coords[i];
                }
            }

            for (int i = 2; i < 4; i++) {
                if (bboxA.coords[i] < bboxB.coords[i]) {
                    retBbox.coords[i] = bboxA.coords[i];
                } else {
                    retBbox.coords[i] = bboxB.coords[i];
                }
            }
        }
        return retBbox;
    }

    public void scale(double xFactor, double yFactor) {
        double x = coords[2] - coords[0];
        double xdiff = (x * xFactor - x) / 2;
        double y = coords[3] - coords[1];
        double ydiff = (y * yFactor - y) / 2;

        coords[0] = coords[0] - xdiff;
        coords[1] = coords[1] - ydiff;
        coords[2] = coords[2] + xdiff;
        coords[3] = coords[3] + ydiff;
    }

    public void scale(double factor) {
        scale(factor, factor);
    }
}
