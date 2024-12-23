/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan - 2011
 */
package org.geowebcache.diskquota.bdb;

import com.sleepycat.persist.model.ClassMetadata;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.EntityModel;
import com.sleepycat.persist.model.FieldMetadata;
import com.sleepycat.persist.model.PrimaryKeyMetadata;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKeyMetadata;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassMetadataBuilder {

    private Class entityClass;

    private PrimaryKeyMetadata primaryKey;

    private List<SecondaryKeyMetadata> secondaryKeys = new ArrayList<>();

    public void entity(Class type) {
        this.entityClass = type;
    }

    public void primaryKey(String field, String sequence) {
        Field pkField = getField(field);

        primaryKey =
                new PrimaryKeyMetadata(pkField.getName(), pkField.getType().getName(), entityClass.getName(), sequence);
    }

    public void secondaryKey(
            String field, String keyName, Relationship relationship, Class relatedEntity, DeleteAction deleteAction) {

        Field skField = getField(field);
        secondaryKeys.add(new SecondaryKeyMetadata(
                skField.getName(),
                entityClass.getName(),
                entityClass.getName(),
                skField.getType().getName(),
                keyName,
                relationship,
                relatedEntity != null ? relatedEntity.getName() : null,
                deleteAction));
    }

    private Field getField(String lookup) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.getName().equals(lookup)) {
                return field;
            }
        }

        throw new IllegalArgumentException("Field " + lookup + " not found in " + entityClass);
    }

    public ClassMetadata build() throws ClassNotFoundException {
        Class type = EntityModel.classForName(entityClass.getName());

        // check it's a valid type
        if (type.isEnum() || type.isInterface() || type.isPrimitive()) {
            throw new IllegalArgumentException(
                    "Enumerations, interfaces or primitive types cannot be entities: " + type.getName());
        }

        // setup the fields
        List<FieldMetadata> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            // skip non persistent fields
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            // grab the field
            fields.add(new FieldMetadata(field.getName(), field.getType().getName(), type.getName()));
        }

        // create the class metadata
        Map<String, SecondaryKeyMetadata> secondaryKeyMap = new HashMap<>();
        if (secondaryKeys != null) {
            for (SecondaryKeyMetadata metadata : secondaryKeys) {
                secondaryKeyMap.put(metadata.getName(), metadata);
            }
            secondaryKeys.clear();
        }
        ClassMetadata classMetadata =
                new ClassMetadata(type.getName(), 0, null, true, primaryKey, secondaryKeyMap, null, fields);
        primaryKey = null;
        entityClass = null;
        return classMetadata;
    }
}
