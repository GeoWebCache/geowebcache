package org.geowebcache.diskquota.storage;

import org.geowebcache.diskquota.QuotaStore;

public interface TileSetVisitor {

    void visit(TileSet tileSet, QuotaStore bdbQuotaStore);

}
