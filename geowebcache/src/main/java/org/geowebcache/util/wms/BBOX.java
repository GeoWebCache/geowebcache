/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Chris Whitney
 *  
 */
package org.geowebcache.util.wms;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BBOX {
    private static Log log = LogFactory
            .getLog(org.geowebcache.util.wms.BBOX.class);

    private static String DELIMITER = ",";

    private static double EQUALITYTHRESHOLD = 0.03;

    public static final BBOX WORLD4326 = 
        new BBOX(-180.0,-90.0,180.0,90.0);
    
    public static final BBOX WORLD3785 = 
        new BBOX(-20037508.34,-20037508.34,20037508.34,20037508.34);
    
    // minx, miny, maxx, maxy
    public double[] coords = new double[4];

    public BBOX(BBOX bbox) {
        coords[0] = bbox.coords[0];
        coords[1] = bbox.coords[1];
        coords[2] = bbox.coords[2];
        coords[3] = bbox.coords[3];
    }
    
    public BBOX(String BBOX) {
        setFromBBOXString(BBOX, 0);
        if (log.isTraceEnabled()) {
            log.trace("Created BBOX: " + getReadableString());
        }
    }

    public BBOX(String[] BBOX) {
        setFromStringArray(BBOX);
        if (log.isTraceEnabled()) {
            log.trace("Created BBOX: " + getReadableString());
        }
    }

    public BBOX(double minx, double miny, double maxx, double maxy) {
        coords[0] = minx;
        coords[1] = miny;
        coords[2] = maxx;
        coords[3] = maxy;

        if (log.isTraceEnabled()) {
            log.trace("Created BBOX: " + getReadableString());
        }
    }
    
    public double getWidth() {
        return coords[2] - coords[0];
    }
    
    public double getHeight() {
        return coords[3] - coords[1];
    }

    /**
     * Sets from an array of strings
     * 
     * @param BBOX
     */
    public void setFromStringArray(String[] BBOX) {
        setFromStringArray(BBOX, 0);
    }

    public void setFromStringArray(String[] BBOX, int recWatch) {
        if (BBOX.length == 4) {
            coords[0] = Double.valueOf(BBOX[0]).doubleValue();
            coords[1] = Double.valueOf(BBOX[1]).doubleValue();
            coords[2] = Double.valueOf(BBOX[2]).doubleValue();
            coords[3] = Double.valueOf(BBOX[3]).doubleValue();

        } else if (recWatch < 4) {
            setFromBBOXString(BBOX[0], recWatch);
        } else {
            log.error("Doesnt understand " + Arrays.toString(BBOX));
        }
    }

    /**
     * Parses the BBOX parameters from a comma separted value list
     * 
     * @param BBOX
     */
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
        return "Min X: " + coords[0] + " Min Y: " + coords[1] + " Max X: "
                + coords[2] + " Max Y: " + coords[3];
    }

    /**
     * Returns a comma separated value String suitable for URL output
     */
    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer(40);
        buff.append(Double.toString(coords[0]));
        buff.append(',');
        buff.append(Double.toString(coords[1]));
        buff.append(',');
        buff.append(Double.toString(coords[2]));
        buff.append(',');
        buff.append(Double.toString(coords[3]));
        return buff.toString();
    }

    public String toKML() {
        return "<LatLonAltBox>"
        +"<north>"+Double.toString(coords[3])+"</north>"
        +"<south>"+Double.toString(coords[1])+"</south>"
        +"<east>"+Double.toString(coords[2])+"</east>"
        +"<west>"+Double.toString(coords[0])+"</west>"
        +"</LatLonAltBox>";
    }
    
    /**
     * Comparing whether the differences between the bounding boxes can be
     * ignored.
     * 
     * @param other
     * @return whether the boxes are equal
     */
    public boolean equals(Object obj) {
        if (obj != null && obj.getClass() == this.getClass()) {
            BBOX other = (BBOX) obj;
            boolean result = true;
            for (int i = 0; i < 4 && result; i++) {
                result = (Math.abs(coords[i]) < EQUALITYTHRESHOLD) 
                        && Math.abs(other.coords[i]) < EQUALITYTHRESHOLD
                        || (coords[i] - other.coords[i])
                                / (coords[i] + other.coords[i]) < EQUALITYTHRESHOLD;
            }
            return result;
        }
        return false;
    }

    /**
     * Check whether this bbox contains the bbox
     * 
     * @param other
     * @return whether other is contained by this
     */
    public boolean contains(BBOX other) {
        return (coords[0] - EQUALITYTHRESHOLD <= other.coords[0]
                && coords[1] - EQUALITYTHRESHOLD <= other.coords[1]
                && coords[2] + EQUALITYTHRESHOLD >= other.coords[2] && coords[3]
                + EQUALITYTHRESHOLD >= other.coords[3]);
    }

    /**
     * Minimal sanity check
     * 
     * @return whether min x < max x, min y < max y
     */
    public boolean isSane() {
        return (coords[0] < coords[2] && coords[1] < coords[3]);
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits((float) coords[0])
                ^ Float.floatToIntBits((float) coords[2]);
    }

    
    public static BBOX intersection(BBOX bboxA, BBOX bboxB) {
        BBOX retBbox = new BBOX(0,0,0,0);

        for(int i=0; i<2; i++) {
            if(bboxA.coords[i] > bboxB.coords[i]) {
                retBbox.coords[i] = bboxA.coords[i];
            } else {
                retBbox.coords[i] = bboxB.coords[i];
            }
        }
        
        for(int i=2; i<4; i++) {
            if(bboxA.coords[i] < bboxB.coords[i]) {
                retBbox.coords[i] = bboxA.coords[i];
            } else {
                retBbox.coords[i] = bboxB.coords[i];
            }
        }
        
        return retBbox;
    }
    
    public void scale(double factor) {
       double x = coords[2] - coords[0];
       double xdiff = (x*factor - x)/2;
       double y = coords[3] - coords[1];
       double ydiff = (y*factor - y)/2;
       
       coords[0] = coords[0] - xdiff;
       coords[1] = coords[1] - ydiff;
       coords[2] = coords[2] + xdiff;
       coords[3] = coords[3] + ydiff;
    }
}
