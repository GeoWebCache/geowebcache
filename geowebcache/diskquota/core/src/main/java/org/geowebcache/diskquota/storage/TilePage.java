package org.geowebcache.diskquota.storage;


public class TilePage {

    private long id;

    private String tileSetId;

    private String key;

    private byte pageZ;

    private int pageY;;

    private int pageX;

    private int creationTimeMinutes;

    public TilePage() {
    }

    public TilePage(String tileSetId, int pageX, int pageY, int zoomLevel) {
        this.tileSetId = tileSetId;
        this.pageX = pageX;
        this.pageY = pageY;
        this.pageZ = (byte) zoomLevel;
        StringBuilder sb = new StringBuilder(128);
        computeId(tileSetId, pageX, pageY, zoomLevel, sb);
        this.key = sb.toString();
        this.creationTimeMinutes = SystemUtils.get().currentTimeMinutes();
    }

    public static void computeId(String tileSetId, int pageX, int pageY, int pageZ,
            StringBuilder target) {

        target.append(tileSetId).append('@').append(pageX).append(',').append(pageY).append(',')
                .append(pageZ).toString();
    }

    public long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TilePage)) {
            return false;
        }
        TilePage t = (TilePage) o;
        return key.equals(t.key);
    }

    @Override
    public int hashCode() {
        return 17 * key.hashCode();
    }

    public String getTileSetId() {
        return tileSetId;
    }

    public byte getZoomLevel() {
        return pageZ;
    }

    public int getPageY() {
        return pageY;
    }

    public int getPageX() {
        return pageX;
    }

    public int getCreationTimeMinutes() {
        return creationTimeMinutes;
    }

    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('[').append(key).append(']')
                .toString();
    }

}
