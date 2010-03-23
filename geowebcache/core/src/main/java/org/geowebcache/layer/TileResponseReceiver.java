package org.geowebcache.layer;

public interface TileResponseReceiver {
    public void setStatus(int status);

    public int getStatus();

    public void setExpiresHeader(long seconds);

    public long getExpiresHeader();

    public void setError();

    public boolean getError();

    public void setErrorMessage(String message);

    public String getErrorMessage();
}
