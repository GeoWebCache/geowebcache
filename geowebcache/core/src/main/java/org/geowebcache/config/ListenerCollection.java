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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.geowebcache.GeoWebCacheException;

/**
 * Aggregates listeners and allows for their handlers to be called in a consistent way.
 *
 * @author smithkm
 */
public class ListenerCollection<Listener> {

    List<Listener> listeners = new LinkedList<>();

    /** Add a listener */
    public synchronized void add(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Remove a listener */
    public synchronized void remove(Listener listener) {
        listeners.remove(listener);
    }

    @FunctionalInterface
    public static interface HandlerMethod<Listener> {
        void callOn(Listener listener) throws GeoWebCacheException, IOException;
    }

    /**
     * Perform an operation on each listener. If one throws an exception, the others will still execute. If more than
     * one exception is thrown, the last will be the one propagated, with the others added as suppressed exceptions. If
     * an Error is thrown, it will be propagated immediately.
     */
    public synchronized void safeForEach(HandlerMethod<Listener> method) throws GeoWebCacheException, IOException {
        LinkedList<Exception> exceptions = listeners.stream()
                .map(l -> {
                    try {
                        method.callOn(l);
                        return Optional.<Exception>empty();
                    } catch (Exception ex) {
                        return Optional.of(ex);
                    }
                })
                .flatMap(Optional::stream)
                .collect(Collectors.collectingAndThen(Collectors.toList(), LinkedList::new));
        if (!exceptions.isEmpty()) {
            Iterator<Exception> it = exceptions.descendingIterator();
            Exception ex = it.next();
            while (it.hasNext()) {
                ex.addSuppressed(it.next());
            }
            if (ex instanceof GeoWebCacheException exception1) {
                throw exception1;
            } else if (ex instanceof IOException exception) {
                throw exception;
            } else {
                throw (RuntimeException) ex;
            }
        }
    }
}
