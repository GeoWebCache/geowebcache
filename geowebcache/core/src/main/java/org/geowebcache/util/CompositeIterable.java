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
        this.iterables = new ArrayList<Iterable<T>>(iteratbles);
    }

    public Iterator<T> iterator() {
        List<Iterator<T>> iterators = new ArrayList<Iterator<T>>(4);
        for (Iterable<T> iterable : iterables) {
            Iterator<T> iterator = iterable.iterator();
            iterators.add(iterator);
        }
        return new CompositeIterator<T>(iterators);
    }

}
