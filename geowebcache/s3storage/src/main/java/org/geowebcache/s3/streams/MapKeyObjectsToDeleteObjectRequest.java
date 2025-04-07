package org.geowebcache.s3.streams;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.geowebcache.s3.delete.DeleteTileInfo;

public class MapKeyObjectsToDeleteObjectRequest implements Function<List<DeleteTileInfo>, Map<String, DeleteTileInfo>> {

    @Override
    public Map<String, DeleteTileInfo> apply(List<DeleteTileInfo> keyObjects) {

        return keyObjects.stream().collect(Collectors.toMap(DeleteTileInfo::toFullPath, info -> info));
    }
}
