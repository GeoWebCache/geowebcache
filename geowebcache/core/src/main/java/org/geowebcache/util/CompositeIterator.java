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

    /**
     * @see java.util.Iterator#hasNext()
     */
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

    /**
     * @see java.util.Iterator#next()
     */
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return curr.next();
    }

    /**
     * @see java.util.Iterator#remove()
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
