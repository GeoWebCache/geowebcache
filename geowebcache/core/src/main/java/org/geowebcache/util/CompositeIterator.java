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

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class CompositeIterator<T> implements Iterator<T> {

    private Iterator<Iterator<T>> iterators;

    private Iterator<T> curr;

    public CompositeIterator(List<Iterator<T>> iterators) {
        this.iterators = iterators.iterator();
        curr = this.iterators.hasNext() ? this.iterators.next() : null;
    }

    /** @see java.util.Iterator#hasNext() */
    @Override
    public boolean hasNext() {
        if (curr == null) {
            return false;
        }
        if (curr.hasNext()) {
            return true;
        }
        if (iterators.hasNext()) {
            curr = iterators.next();
        } else {
            curr = null;
        }
        return hasNext();
    }

    /** @see java.util.Iterator#next() */
    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return curr.next();
    }

    /** @see java.util.Iterator#remove() */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
