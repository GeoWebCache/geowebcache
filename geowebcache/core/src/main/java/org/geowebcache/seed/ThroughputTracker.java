package org.geowebcache.seed;

/**
 * Used to monitor throughput of any repeated actions by keeping a history of time the actions takes
 * to perform. By keeping a history the throughput tracker determines average throughput for a
 * moving window of repeating actions.
 */
public class ThroughputTracker {

    // each entry is the time it took to perform the action
    private int[] throughputHistory;

    // the length of the throughput history.
    private int throughputSampleSize;

    // the next location in the history to store a throughput sample at
    private int currentThroughputIndex;

    // Has the tracker received enough samples to have a full history?
    private boolean hasFullHistory;

    /**
     * Create a new throughput tracker with a specified number of samples to track
     * 
     * @param throughputSampleCount
     *            The length of the throughput history
     */
    public ThroughputTracker(int throughputSampleSize) {
        this.throughputSampleSize = throughputSampleSize;

        this.throughputHistory = new int[this.throughputSampleSize];
        this.currentThroughputIndex = 0;

        hasFullHistory = false;
    }

    /**
     * Adds a sample to the history of samples.
     * 
     * @param sample
     *            Time in milliseconds some action took
     */
    public void addSample(int sample) {
        throughputHistory[currentThroughputIndex] = sample;
        if (currentThroughputIndex + 1 >= throughputSampleSize) {
            currentThroughputIndex = 0;
            hasFullHistory = true;
        } else {
            currentThroughputIndex++;
        }
    }

    /**
     * Calculate average throughput based on history of samples collected. If there isn't yet a full
     * history of samples, any available history will be used.
     * 
     * @return The calculated throughput in requests per second. 0 if there is no history available.
     */
    public float getThroughput() {
        long totalTime = 0;
        int sampleCount = (hasFullHistory ? throughputHistory.length : currentThroughputIndex);

        if (sampleCount == 0) {
            return 0;
        } else {
            for (int i = 0; i < sampleCount; i++) {
                totalTime += throughputHistory[i];
            }

            float avgTimePerAction = (float)totalTime / (float)sampleCount;
            
            if(avgTimePerAction == 0) {
                return 0; // would mean an infinite number of requests per second ... and a div/0 error
            } else {
                return 1000 / avgTimePerAction;
            }
        }
    }
}
