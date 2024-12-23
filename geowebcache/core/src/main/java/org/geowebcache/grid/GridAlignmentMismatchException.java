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
 * @author Arne Kepp, Copyright 2010
 */
package org.geowebcache.grid;

public class GridAlignmentMismatchException extends GridMismatchException {

    public GridAlignmentMismatchException(double x, long posX, double y, long posY) {
        super("X,Y values for the tile index were calculated to be {"
                + x
                + ", "
                + y
                + "} "
                + " which had to be rounded to {"
                + posX
                + ", "
                + posY
                + "} "
                + " and exceeds the threshold of 10%. Perhaps the client is using"
                + " the wrong origin ?");
    }
}
