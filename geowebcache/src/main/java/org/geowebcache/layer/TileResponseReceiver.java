package org.geowebcache.layer;

public interface TileResponseReceiver {
        public void setStatus(int status);
        public int getStatus();
        public void setError();
        public boolean getError();
        public void setErrorMessage(String message);
        public String getErrorMessage();
}

