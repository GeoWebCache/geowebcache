package org.geowebcache.storage;

import java.io.IOException;

public class StorageException extends IOException {
    private static final long serialVersionUID = -8168546310999238936L;
    
    String msg;
    
    public StorageException(String msg) {
        this.msg = msg;
    }
    
    public StorageException(String msg, Throwable cause) {
        this.msg = msg;
        initCause(cause);
    }
    
    public String getMessage() {
        return msg;
    }
}
