package org.geowebcache.diskquota.bdb.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.geowebcache.diskquota.storage.PageStats;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TileSet;

import com.sleepycat.persist.model.AnnotationModel;
import com.sleepycat.persist.model.ClassMetadata;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.EntityMetadata;
import com.sleepycat.persist.model.Relationship;

public class DiskQuotaEntityModel extends AnnotationModel {

    private Map<String, ClassMetadata> classes = new HashMap<String, ClassMetadata>();

    private Map<String, EntityMetadata> entities = new HashMap<String, EntityMetadata>();

    public DiskQuotaEntityModel() {
        try {
            ClassMetadataBuilder builder = new ClassMetadataBuilder();

            builder.entity(Quota.class);
            builder.primaryKey("id", "quota_id");
            builder.secondaryKey("tileSetId", "tileset_id", Relationship.ONE_TO_ONE, TileSet.class,
                    DeleteAction.CASCADE);
            registerClassMetadata(builder.build());
            
            builder.entity(PageStats.class);
            builder.primaryKey("id", "page_stats_seq");
            builder.secondaryKey("pageId", "page_stats_by_page_id", Relationship.ONE_TO_ONE, TilePage.class, DeleteAction.CASCADE);
            builder.secondaryKey("frequencyOfUse", "LFU", Relationship.MANY_TO_ONE, null, DeleteAction.ABORT);
            builder.secondaryKey("lastAccessTimeMinutes", "LRU", Relationship.MANY_TO_ONE, null, DeleteAction.ABORT);
            builder.secondaryKey("fillFactor", "fill_factory", Relationship.MANY_TO_ONE, null, DeleteAction.ABORT);
            registerClassMetadata(builder.build());

            builder.entity(TilePage.class);
            builder.primaryKey("id", "page_id");
            builder.secondaryKey("tileSetId", "tileset_id_fk", Relationship.MANY_TO_ONE, TileSet.class, DeleteAction.CASCADE);
            builder.secondaryKey("key", "page_key", Relationship.ONE_TO_ONE, null, DeleteAction.ABORT);
            registerClassMetadata(builder.build());
            
            builder.entity(TileSet.class);
            builder.primaryKey("key", null);
            builder.secondaryKey("layerName", "layer", Relationship.MANY_TO_ONE, null, DeleteAction.ABORT);
            registerClassMetadata(builder.build());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unexpected setup exception occurred: " + e.getMessage(), e);
        }

    }

    private void registerClassMetadata(ClassMetadata classMetadata) {
        classes.put(classMetadata.getClassName(), classMetadata);

        // go on with the entity
        EntityMetadata entityMetadata = new EntityMetadata(classMetadata.getClassName(),
                classMetadata.getPrimaryKey(), classMetadata.getSecondaryKeys());
        entities.put(classMetadata.getClassName(), entityMetadata);
    }

    @Override
    public ClassMetadata getClassMetadata(String className) {
        ClassMetadata metadata = super.getClassMetadata(className);
        if (metadata != null) {
            return metadata;
        } else {
            return classes.get(className);
        }
    }

    @Override
    public EntityMetadata getEntityMetadata(String className) {
        EntityMetadata metadata = super.getEntityMetadata(className);
        if (metadata != null) {
            return metadata;
        } else {
            return entities.get(className);
        }
    }

    @Override
    public Set<String> getKnownClasses() {
        return classes.keySet();
    }

}
