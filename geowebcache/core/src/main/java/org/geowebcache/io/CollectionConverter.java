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
 * <p>Copyright 2019
 */
package org.geowebcache.io;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CollectionConverter extends com.thoughtworks.xstream.converters.collections.CollectionConverter {

    public static final String UNMODIFIABLE_LIST = "java.util.Collections$UnmodifiableList";
    public static final String UNMODIFIABLE_SET = "java.util.Collections$UnmodifiableSet";
    public static final String ARRAY_LIST = "java.util.Arrays$ArrayList";

    public CollectionConverter(Mapper mapper) {
        super(mapper);
    }

    public CollectionConverter(Mapper mapper, Class type) {
        super(mapper, type);
    }

    @Override
    public boolean canConvert(Class type) {
        if (type != null) {
            String typeName = type.getName();
            if (typeName.equals(ARRAY_LIST)
                    || typeName.equals(UNMODIFIABLE_LIST)
                    || typeName.equals(UNMODIFIABLE_SET)) {
                return true;
            }
        }
        return super.canConvert(type);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Class requiredType = context.getRequiredType();
        if (requiredType != null) {
            String typeName = requiredType.getName();
            if (UNMODIFIABLE_LIST.equals(typeName)) {
                List<Object> list = new ArrayList<>();
                populateCollection(reader, context, list);
                return Collections.unmodifiableList(list);
            } else if (UNMODIFIABLE_SET.equals(typeName)) {
                Set<Object> set = new HashSet<>();
                populateCollection(reader, context, set);
                return Collections.unmodifiableSet(set);
            } else if (ARRAY_LIST.equals(typeName)) {
                List<Object> list = new ArrayList<>();
                populateCollection(reader, context, list);
                return Arrays.asList(list.toArray());
            }
        }
        return super.unmarshal(reader, context);
    }
}
