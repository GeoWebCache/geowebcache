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
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class XMLBuilder {
    Appendable builder;
    
    public XMLBuilder(Appendable builder) {
        super();
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
        
        ESCAPE_ENTITIES=Collections.unmodifiableMap(entities);
    }
    
    class NodeInfo {
        String name;
        boolean indented;
        boolean containsIndented=false;
    }
    
    Deque<NodeInfo> nodeStack = new LinkedList<>();
    boolean startOfElement = false;
    
    /**
     * Append the given string without escaping
     * @param s
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder appendUnescaped(String s) throws IOException {
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
        NodeInfo ni = nodeStack.pop();

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
     * Append an element that contains only text
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder simpleElement(String name, String text, boolean indent) throws IOException {
        return startElement(name, indent).text(text).endElement();
    }
    
    /**
     * Add text to the body of the element
     * @param str
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder text(String str) throws IOException {
        if(startOfElement) appendUnescaped(">");
        startOfElement=false;
        return appendEscaped(str);
    }
    
    /**
     * Append the string, escaping special characters.
     * @param str
     * @return
     * @throws IOException thrown if the underlying Appendable throws IOException
     */
    public XMLBuilder appendEscaped(String str) throws IOException {

        int offset = 0, strLen = str.length();
        while (offset < strLen) {
          int curChar = str.codePointAt(offset);
          offset += Character.charCount(curChar);
          
          char[] chars = ESCAPE_ENTITIES.get(curChar);
          if (chars==null) chars = Character.toChars(curChar);
          
          builder.append(new String(chars));
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
        if(! startOfElement) throw new IllegalArgumentException();
        appendUnescaped(" ").appendUnescaped(name).appendUnescaped("=\"").appendEscaped(value).appendUnescaped("\"");
        return this;
    }
}
