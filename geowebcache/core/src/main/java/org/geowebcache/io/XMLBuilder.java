/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Kevin Smith, Boundless, Copyright 2014
 */

package org.geowebcache.io;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Preconditions;

@ParametersAreNonnullByDefault
public class XMLBuilder {
    Appendable builder;
    
    public XMLBuilder(Appendable builder) {
        super();
        Preconditions.checkNotNull(builder);
        this.builder = builder;
    }

    /**
     * The UTF-8 Encoding
     * 
     * @deprecated Use StandardCharsets.UTF_8 instead.  This is included for Java 6
     * compatibility in GWC 1.5 and will not be in GWC 1.6
     */
    @Deprecated
    public static final Charset UTF_8 = Charset.forName("UTF-8");
    
    static final Map<Integer, char[]> ESCAPE_ENTITIES;
    
    static {
        Map<Integer, char[]> entities = new HashMap<Integer, char[]>();
        entities.put(Integer.valueOf('<'), "&lt;".toCharArray());
        entities.put(Integer.valueOf('>'), "&gt;".toCharArray());
        entities.put(Integer.valueOf('&'), "&amp;".toCharArray());
        entities.put(Integer.valueOf('"'), "&quot;".toCharArray());
        entities.put(Integer.valueOf('\''), "&apos;".toCharArray());
        
        ESCAPE_ENTITIES=Collections.unmodifiableMap(entities);
    }
    
    class NodeInfo {
        String name;
        boolean indented;
        boolean containsIndented=false;
    }
    
    Deque<NodeInfo> nodeStack = new LinkedList<NodeInfo>();
    boolean startOfElement = false;
    
    /**
     * Append the given string without escaping
     * @param s
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder appendUnescaped(@Nullable String s) throws IOException {
        builder.append(s);
        return this;
    }
    
    /**
     * Start an XML Element on a new line indented for its depth
     * @param name name of the element
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder indentElement(String name) throws IOException {
        return startElement(name, true);
    }
    
    /**
     * Start an XML Element
     * @param name name of the element
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder startElement(String name, boolean indent) throws IOException {
        Preconditions.checkNotNull(name);
        if(startOfElement) appendUnescaped(">");
        startOfElement=false;
        if(indent) {
            text("\n");
            for(int i=0; i<nodeStack.size(); i++){
                text("  ");
            }
        }
        appendUnescaped("<").appendUnescaped(name);
        if(!nodeStack.isEmpty()) nodeStack.peek().containsIndented=true;
        NodeInfo ni = new NodeInfo();
        ni.name=name;
        ni.indented=indent;
        nodeStack.push(ni);
        startOfElement=true;
        return this;
    }
    /**
     * Start an XML Element
     * @param name name of the element
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder startElement(String name) throws IOException {
        return startElement(name, false);
    }
    
    /**
     * End an XML element
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder endElement() throws IOException {
        return endElement(null);
    }
    /**
     * End an XML element
     * @param name if not null and assertions are enabled, will check that the element being
     * closed has this name.
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder endElement(@Nullable String name) throws IOException {
        NodeInfo ni = nodeStack.pop();
        
        assert name==null || name.equals(ni.name);

        if(startOfElement) {
            appendUnescaped("/>");
        } else {
            if(ni.indented && ni.containsIndented) {
                text("\n");
                for(int i=0; i<nodeStack.size(); i++){
                    text("  ");
                }
            }
            appendUnescaped("</").appendUnescaped(ni.name).appendUnescaped(">");
        }
        startOfElement=false;
        return this;
    }
    
    /**
     * Append an element that contains only text.
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder simpleElement(String name, @Nullable String text, boolean indent) throws IOException {
        return startElement(name, indent).text(text).endElement();
    }
    
    /**
     * Add text to the body of the element.
     * @param str
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder text(@Nullable String str) throws IOException {
        if(str!=null && !str.isEmpty()) {
            if(startOfElement) appendUnescaped(">");
            startOfElement=false;
            return appendEscaped(str);
        }
        return this;
    }
    
    /**
     * Append the string, escaping special characters.
     * @param str
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder appendEscaped(@Nullable String str) throws IOException {

        if(str!=null) {
            int offset = 0, strLen = str.length();
            while (offset < strLen) {
              int curChar = str.codePointAt(offset);
              offset += Character.charCount(curChar);
              
              char[] chars = ESCAPE_ENTITIES.get(curChar);
              if (chars==null) chars = Character.toChars(curChar);
              
              builder.append(new String(chars));
            }
        }
        return this;
    }

    /**
     * Add an entity to the text.
     * @param name
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder entity(String name) throws IOException {
        Preconditions.checkNotNull(name);
        if(startOfElement) appendUnescaped(">");
        startOfElement=false;
        appendUnescaped("&").appendUnescaped(name).appendUnescaped(";");
        return this;
    }
    
    /**
     * Add an attribute to the current element.  Must be called before any text is added.
     * @param name
     * @param value
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder attribute(String name, String value) throws IOException {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        if(! startOfElement) throw new IllegalArgumentException();
        appendUnescaped(" ").appendUnescaped(name).appendUnescaped("=\"").appendEscaped(value).appendUnescaped("\"");
        return this;
    }
    
    /**
     * Add minx, miny, maxx, and maxy attributes
     * @param minx
     * @param miny
     * @param maxx
     * @param maxy
     * @return
     * @throws IOException
     */
    public <T> XMLBuilder bboxAttributes(T minx, T miny, T maxx, T maxy) throws IOException {
        return attribute("minx", minx.toString())
                .attribute("miny", miny.toString())
                .attribute("maxx", maxx.toString())
                .attribute("maxy", maxy.toString());
    }
    
    /**
     * Add a BoundingBox element
     * @param srs
     * @param minx
     * @param miny
     * @param maxx
     * @param maxy
     * @return
     * @throws IOException
     */
    public <T> XMLBuilder boundingBox(@Nullable String srs, T minx,T miny, T maxx, T maxy) throws IOException {
        indentElement("BoundingBox");
        if(srs!=null) attribute("SRS", srs);
        bboxAttributes(minx, miny, maxx, maxy);
        endElement();
        return this;
    }
    
    /**
     * Add a LatLonBoundingBox element
     * @param srs
     * @param minx
     * @param miny
     * @param maxx
     * @param maxy
     * @return
     * @throws IOException
     */
    public <T> XMLBuilder latLonBoundingBox(T minx, T miny, T maxx, T maxy) throws IOException {
        return indentElement("LatLonBoundingBox")
                .bboxAttributes(minx, miny, maxx, maxy)
                .endElement();
    }
    
    /**
     * Append an XML header
     * @param version
     * @param charset
     * @return
     * @throws IOException
     */
    public XMLBuilder header(String version, @Nullable String charset) throws IOException {
        Preconditions.checkNotNull(version);
        appendUnescaped("<?xml version=\"").appendEscaped(version).appendUnescaped("\"");
        if(charset!=null){
            appendUnescaped(" encoding=\"").appendEscaped(charset).appendUnescaped("\"");
        }
        appendUnescaped("?>\n");
        return this;
    }
    /**
     * Append an XML header
     * @param version
     * @param charset
     * @return
     * @throws IOException
     */
    public XMLBuilder header(String version, @Nullable Charset charset) throws IOException {
        String charsetName = charset.name();
        return header(version, charsetName);
    }
}
