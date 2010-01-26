/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, OpenGeo, Copyright 2010
 */
package org.geowebcache.layer.updatesource;

public class GeoRSSFeedDefinition extends UpdateSourceDefinition {

    private String feedUrl;

    private String gridSetId;

    private Integer pollInterval;

    private String mimeFormat;

    private Integer seedingThreads;

    private Integer maxMaskLevel;

    /**
     * The maximum zoom level which to create a backing tile mask for to track the tiles affected by
     * the feed geometries; defaults to {@code 10}
     * 
     * @return
     */
    public int getMaxMaskLevel() {
        return maxMaskLevel == null ? 10 : maxMaskLevel.intValue();
    }

    void setMaxMaskLevel(final int level) {
        this.maxMaskLevel = level;
    }

    public String getMimeFormat() {
        return mimeFormat;
    }

    void setMimeFormat(String mimeFormat) {
        this.mimeFormat = mimeFormat;
    }

    /**
     * Number of threads to spawn to seed based on the results of the GeoRSS feed; default to
     * {@code 1} if not set
     * 
     * @return
     */
    public int getSeedingThreads() {
        return seedingThreads == null ? 1 : seedingThreads.intValue();
    }

    void setSeedingThreads(int seedingThreads) {
        this.seedingThreads = seedingThreads;
    }

    /**
     * The URL to the feed. I think we should use templating for parameters, so in the initial
     * implementation we search the string for {lastEntryId} and replace any occurrences with the
     * actual last entry id.
     * 
     * @return
     */
    public String getFeedUrl() {
        return feedUrl;
    }

    void setFeedUrl(String url) {
        this.feedUrl = url;
    }

    /**
     * Grid set for which this feed is valid
     */
    public String getGridSetId() {
        return gridSetId;
    }

    void setGridSetId(String gridSetId) {
        this.gridSetId = gridSetId;
    }

    /**
     * @return the polling interval in seconds, or {@code -1} to mean polling is disabled
     */
    public int getPollInterval() {
        if (pollInterval == null) {
            return -1;
        }
        return pollInterval;
    }

    void setPollInterval(int seconds) {
        if (seconds <= 0) {
            pollInterval = null;
        } else {
            pollInterval = seconds;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GeoRSS feed[");
        sb.append("gridSetId: ").append(gridSetId);
        sb.append(", poll interval: ").append(getPollIntervalStr());
        sb.append(", feed URL: '").append(feedUrl).append("'");
        sb.append(", mime format: ").append(mimeFormat);
        sb.append(", seeding threads: ").append(seedingThreads);
        sb.append(", max masking level: ").append(maxMaskLevel);
        return sb.append("]").toString();
    }

    /**
     * @return human friendly representation of the poll interval
     */
    public String getPollIntervalStr() {
        return getPollIntervalStr(pollInterval);
    }

    private String getPollIntervalStr(final Integer pollInterval) {
        if (pollInterval == null) {
            return "Disabled";
        }
        final int MINUTE = 60;
        final int HOUR = MINUTE * 60;
        final int DAY = HOUR * 24;

        int interval = pollInterval.intValue();
        String pollIntervalStr;
        if (interval > DAY) {
            int days = interval / DAY;
            int remaining = interval % DAY;
            pollIntervalStr = days + " Day" + (days > 1 ? "s, " : ", ");
            pollIntervalStr += getPollIntervalStr(remaining);
        } else if (interval > HOUR) {
            int hours = interval / HOUR;
            int remaining = interval % HOUR;
            pollIntervalStr = hours + " Hour" + (hours > 1 ? "s, " : ", ");
            pollIntervalStr += getPollIntervalStr(remaining);
        } else if (interval > MINUTE) {
            int minutes = interval / MINUTE;
            int remaining = interval % MINUTE;
            pollIntervalStr = minutes + " Minute" + (minutes > 1 ? "s, " : ", ");
            pollIntervalStr += getPollIntervalStr(remaining);
        } else {
            pollIntervalStr = interval + " Second" + (interval > 1 ? "s" : "");
        }
        return pollIntervalStr;
    }
}
