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
 * <p>Copyright 2026
 */
package org.geowebcache.storage.memorycache;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import java.io.Serial;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Backend settings whose class is unknown, as when the backend module was removed: the cache is unusable, and the
 * settings are saved back as they were read, so that reinstalling the module restores them.
 */
public class UnresolvedKeyValueStoreInfo implements KeyValueStoreInfo {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String CLASS = "class";

    private final String alias;

    /** The whole {@code <backend>} element. */
    private final String xml;

    UnresolvedKeyValueStoreInfo(String alias, String xml) {
        this.alias = alias;
        this.xml = xml;
    }

    /** The {@code class} attribute of the backend element, null when missing. */
    public String getAlias() {
        return alias;
    }

    /** Always fails, as the class of these settings is not installed. */
    @Override
    public KeyValueStore createStore() {
        throw new IllegalStateException("Backend " + alias + " is not installed");
    }

    /** Equal when both hold the same backend element. */
    @Override
    public boolean equals(Object o) {
        return o instanceof UnresolvedKeyValueStoreInfo other && xml.equals(other.xml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xml);
    }

    /**
     * Lets {@code xs} read {@code <memoryCache>} with an unknown backend instead of failing the whole configuration.
     */
    public static void register(XStream xs) {
        // an unknown class attribute fails in the reflection converter, before any converter of the field runs, so
        // MemoryCacheInfo gets a reflection converter of its own resolving it to this class
        Mapper mapper = new MapperWrapper(xs.getMapper()) {
            @Override
            public Class realClass(String elementName) {
                try {
                    return super.realClass(elementName);
                } catch (CannotResolveClassException e) {
                    return UnresolvedKeyValueStoreInfo.class;
                }
            }

            // no class attribute written for this class, the converter writes the original one
            @Override
            public Class defaultImplementationOf(Class type) {
                if (type == KeyValueStoreInfo.class) return UnresolvedKeyValueStoreInfo.class;
                return super.defaultImplementationOf(type);
            }
        };
        xs.registerConverter(new ReflectionConverter(mapper, xs.getReflectionProvider(), MemoryCacheInfo.class));
        xs.registerConverter(new RawConverter());
    }

    /** Copies the backend element as raw XML. */
    private static class RawConverter implements Converter {

        @Override
        public boolean canConvert(Class type) {
            return type == UnresolvedKeyValueStoreInfo.class;
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            StringWriter xml = new StringWriter();
            CompactWriter writer = new CompactWriter(xml);
            new HierarchicalStreamCopier().copy(reader, writer);
            // the writer buffers
            writer.flush();
            return new UnresolvedKeyValueStoreInfo(reader.getAttribute(CLASS), xml.toString());
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            UnresolvedKeyValueStoreInfo info = (UnresolvedKeyValueStoreInfo) source;
            HierarchicalStreamReader reader = new DomDriver().createReader(new StringReader(info.xml));
            if (info.alias != null) writer.addAttribute(CLASS, info.alias);
            HierarchicalStreamCopier copier = new HierarchicalStreamCopier();
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                copier.copy(reader, writer);
                reader.moveUp();
            }
        }
    }
}
