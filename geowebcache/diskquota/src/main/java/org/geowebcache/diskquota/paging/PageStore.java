package org.geowebcache.diskquota.paging;

import java.io.IOException;
import java.util.List;

public interface PageStore {

    List<TilePage> getPages(String layerName, String gridSetId) throws IOException;

    void savePages(String layerName, String gridSetId, List<TilePage> availablePages)
            throws IOException;

}
