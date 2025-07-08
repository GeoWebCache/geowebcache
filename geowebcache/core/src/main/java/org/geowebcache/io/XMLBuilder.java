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
 * @author Kevin Smith, Boundless, Copyright 2014
 */
package org.geowebcache.io;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class XMLBuilder {
    Appendable builder;

    public XMLBuilder(Appendable builder) {
        super();
        Preconditions.checkNotNull(builder);
        this.builder = builder;
    }

    static final Map<Integer, char[]> ESCAPE_ENTITIES;

    static {
        Map<Integer, char[]> entities = new HashMap<>();
        entities.put(Integer.valueOf('<'), "&lt;".toCharArray());
        entities.put(Integer.valueOf('>'), "&gt;".toCharArray());
        entities.put(Integer.valueOf('&'), "&amp;".toCharArray());
        entities.put(Integer.valueOf('"'), "&quot;".toCharArray());
        entities.put(Integer.valueOf('\''), "&apos;".toCharArray());

        ESCAPE_ENTITIES = Collections.unmodifiableMap(entities);
    }

    static class NodeInfo {
        String name;
        boolean indented;
        boolean containsIndented = false;
    }

    Deque<NodeInfo> nodeStack = new LinkedList<>();
    boolean startOfElement = false;

    /**
     * Append the given string without escaping
     *
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder appendUnescaped(@Nullable String s) throws IOException {
        builder.append(s);
        return this;
    }

    /**
     * Start an XML Element on a new line indented for its depth
     *
     * @param name name of the element
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder indentElement(String name) throws IOException {
        return startElement(name, true);
    }

    /**
     * Start an XML Element
     *
     * @param name name of the element
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder startElement(String name, boolean indent) throws IOException {
        Preconditions.checkNotNull(name);
        if (startOfElement) appendUnescaped(">");
        startOfElement = false;
        if (indent) {
            text("\n");
            for (int i = 0; i < nodeStack.size(); i++) {
                text("  ");
            }
        }
        appendUnescaped("<").appendUnescaped(name);
        if (!nodeStack.isEmpty()) nodeStack.peek().containsIndented = true;
        NodeInfo ni = new NodeInfo();
        ni.name = name;
        ni.indented = indent;
        nodeStack.push(ni);
        startOfElement = true;
        return this;
    }
    /**
     * Start an XML Element
     *
     * @param name name of the element
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder startElement(String name) throws IOException {
        return startElement(name, false);
    }

    /**
     * End an XML element
     *
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder endElement() throws IOException {
        return endElement(null);
    }
    /**
     * End an XML element
     *
     * @param name if not null and assertions are enabled, will check that the element being closed has this name.
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder endElement(@Nullable String name) throws IOException {
        NodeInfo ni = nodeStack.pop();

        assert name == null || name.equals(ni.name);

        if (startOfElement) {
            appendUnescaped("/>");
        } else {
            if (ni.indented && ni.containsIndented) {
                text("\n");
                for (int i = 0; i < nodeStack.size(); i++) {
                    text("  ");
                }
            }
            appendUnescaped("</").appendUnescaped(ni.name).appendUnescaped(">");
        }
        startOfElement = false;
        return this;
    }

    /**
     * Append an element that contains only text.
     *
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder simpleElement(String name, @Nullable String text, boolean indent) throws IOException {
        return startElement(name, indent).text(text).endElement();
    }

    /**
     * Add text to the body of the element.
     *
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder text(@Nullable String str) throws IOException {
        if (str != null && !str.isEmpty()) {
            if (startOfElement) appendUnescaped(">");
            startOfElement = false;
            return appendEscaped(str);
        }
        return this;
    }

    /**
     * Append the string, escaping special characters.
     *
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder appendEscaped(@Nullable String str) throws IOException {

        if (str != null) {
            int offset = 0, strLen = str.length();
            while (offset < strLen) {
                int curChar = str.codePointAt(offset);
                offset += Character.charCount(curChar);

                char[] chars = ESCAPE_ENTITIES.get(curChar);
                if (chars == null) chars = Character.toChars(curChar);

                builder.append(new String(chars));
            }
        }
        return this;
    }

    /**
     * Add an entity to the text.
     *
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder entity(String name) throws IOException {
        Preconditions.checkNotNull(name);
        if (startOfElement) appendUnescaped(">");
        startOfElement = false;
        appendUnescaped("&").appendUnescaped(name).appendUnescaped(";");
        return this;
    }

    /**
     * Add an attribute to the current element. Must be called before any text is added.
     *
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder attribute(String name, String value) throws IOException {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        if (!startOfElement) throw new IllegalArgumentException();
        appendUnescaped(" ")
                .appendUnescaped(name)
                .appendUnescaped("=\"")
                .appendEscaped(value)
                .appendUnescaped("\"");
        return this;
    }

    /** Add minx, miny, maxx, and maxy attributes */
    public <T> XMLBuilder bboxAttributes(T minx, T miny, T maxx, T maxy) throws IOException {
        return attribute("minx", minx.toString())
                .attribute("miny", miny.toString())
                .attribute("maxx", maxx.toString())
                .attribute("maxy", maxy.toString());
    }

    /** Add a BoundingBox element */
    public <T> XMLBuilder boundingBox(@Nullable String srs, T minx, T miny, T maxx, T maxy) throws IOException {
        indentElement("BoundingBox");
        if (srs != null) attribute("SRS", srs);
        bboxAttributes(minx, miny, maxx, maxy);
        endElement();
        return this;
    }

    /** Add a LatLonBoundingBox element */
    public <T> XMLBuilder latLonBoundingBox(T minx, T miny, T maxx, T maxy) throws IOException {
        return indentElement("LatLonBoundingBox")
                .bboxAttributes(minx, miny, maxx, maxy)
                .endElement();
    }

    /** Append an XML header */
    public XMLBuilder header(String version, @Nullable String charset) throws IOException {
        Preconditions.checkNotNull(version);
        appendUnescaped("<?xml version=\"").appendEscaped(version).appendUnescaped("\"");
        if (charset != null) {
            appendUnescaped(" encoding=\"").appendEscaped(charset).appendUnescaped("\"");
        }
        appendUnescaped("?>\n");
        return this;
    }
    /** Append an XML header */
    public XMLBuilder header(String version, @Nullable Charset charset) throws IOException {
        String charsetName = charset.name();
        return header(version, charsetName);
    }
}
