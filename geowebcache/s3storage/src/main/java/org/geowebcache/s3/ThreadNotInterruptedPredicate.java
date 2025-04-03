package org.geowebcache.s3;

import java.util.function.Predicate;

public class ThreadNotInterruptedPredicate implements Predicate<Object> {

    @Override
    public boolean test(Object o) {
        return !Thread.interrupted();
    }
}
