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
 * <p>Copyright 2025
 */
package org.geowebcache.s3.streams;

import static java.util.Spliterator.ORDERED;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BatchingIterator<T> implements Iterator<List<T>> {
    /**
     * Given a stream, convert it to a stream of batches no greater than the batchSize.
     *
     * @param originalStream to convert
     * @param batchSize maximum size of a batch
     * @param <T> type of items in the stream
     * @return a stream of batches taken sequentially from the original stream
     */
    public static <T> Stream<List<T>> batchedStreamOf(Stream<T> originalStream, int batchSize) {
        return asStream(new BatchingIterator<>(originalStream.iterator(), batchSize));
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, ORDERED), false);
    }

    private final int batchSize;
    private final Iterator<T> sourceIterator;

    private BatchingIterator(Iterator<T> sourceIterator, int batchSize) {
        this.batchSize = batchSize;
        this.sourceIterator = sourceIterator;
    }

    @Override
    public boolean hasNext() {
        return sourceIterator.hasNext();
    }

    @Override
    public List<T> next() {
        List<T> currentBatch = new ArrayList<>(batchSize);
        while (sourceIterator.hasNext() && currentBatch.size() < batchSize) {
            currentBatch.add(sourceIterator.next());
        }
        return currentBatch;
    }
}
