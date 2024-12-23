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
 * <p>Copyright 2019
 */
package org.geowebcache.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class CompositeIterable<T> implements Iterable<T> {

    private List<Iterable<T>> iterables;

    @SuppressWarnings("unchecked")
    public CompositeIterable(Iterable<T>... iterables) {
        this(iterables == null ? Collections.EMPTY_LIST : Arrays.asList(iterables));
    }

    public CompositeIterable(List<Iterable<T>> iteratbles) {
        this.iterables = new ArrayList<>(iteratbles);
    }

    @Override
    public Iterator<T> iterator() {
        List<Iterator<T>> iterators = new ArrayList<>(4);
        for (Iterable<T> iterable : iterables) {
            Iterator<T> iterator = iterable.iterator();
            iterators.add(iterator);
        }
        return new CompositeIterator<>(iterators);
    }
}
