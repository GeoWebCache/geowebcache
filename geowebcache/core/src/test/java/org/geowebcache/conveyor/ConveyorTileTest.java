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
 * @author Kevin Smith, Boundless, Copyright 2018
 */
package org.geowebcache.conveyor;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ConveyorTileTest {

    @Test
    public void testGetGridSubsetWithNullGridsetId() {
        // All these nulls may need to be replace with mocks eventually.
        ConveyorTile tile = new ConveyorTile(null, null, null, null);
        tile.setGridSetId(null); // Should be this already but just to make sure.
        assertNull(tile.getGridSubset());
    }
}
