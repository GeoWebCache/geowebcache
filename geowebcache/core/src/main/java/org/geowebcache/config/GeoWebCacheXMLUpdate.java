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
package org.geowebcache.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.geotools.xml.XMLUtils;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * Updates a geowebcache.xml document to the current schema ... very carefully.
 *
 * <ul>
 *   <li>Completing the XSL transforms applied when loading older configurations.
 *   <li>Elements are sorted into schema order, comments move with the element that follows them.
 *   <li>However a comment containing an example element, is sorted as if it were that element.
 *   <li>Gotcha: Elements unknown to the schema stay after their preceding sibling.
 * </ul>
 */
public class GeoWebCacheXMLUpdate {

    private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    /** Matches the first element name in a comment, used to detect commented out examples. */
    private static final Pattern COMMENTED_ELEMENT = Pattern.compile("<([A-Za-z_][\\w.-]*)");

    /** Global xs:element declarations by name. */
    private final Map<String, Element> globalElements = new HashMap<>();

    /** Named xs:complexType definitions by name. */
    private final Map<String, Element> complexTypes = new HashMap<>();

    /** Global elements that may be used in place of a substitution group head, by head name. */
    private final Map<String, List<Element>> substitutions = new HashMap<>();

    /** Content model for each element declaration, built as required. */
    private final IdentityHashMap<Element, ContentModel> models = new IdentityHashMap<>();

    /** Schema version produced by this update. */
    private final String version;

    /**
     * Reads the current geowebcache.xsd schema.
     *
     * @throws IOException if the schema could not be read
     */
    public GeoWebCacheXMLUpdate() throws IOException {
        try (InputStream schema =
                XMLConfiguration.class.getResourceAsStream(XMLConfiguration.DEFAULT_SCHEMA_RESOURCE)) {
            DocumentBuilderFactory factory = XMLUtils.newDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            Element root = XMLUtils.newDocumentBuilder(factory).parse(schema).getDocumentElement();
            version = root.getAttribute("version");
            for (Element child : children(root)) {
                String name = child.getAttribute("name");
                if ("element".equals(child.getLocalName())) {
                    globalElements.put(name, child);
                    if (child.hasAttribute("substitutionGroup")) {
                        String head = localName(child.getAttribute("substitutionGroup"));
                        if (!substitutions.containsKey(head)) {
                            substitutions.put(head, new ArrayList<>());
                        }
                        substitutions.get(head).add(child);
                    }
                } else if ("complexType".equals(child.getLocalName())) {
                    complexTypes.put(name, child);
                }
            }
        } catch (Exception huh) {
            throw new IOException("Unable to read schema", huh);
        }
    }

    /** Schema version this update produces. */
    public String getVersion() {
        return version;
    }

    /** Updates the document in place, returning true if any nodes were moved. */
    public boolean update(Element root) {
        Element declaration = globalElements.get(root.getLocalName());
        return declaration != null && sort(root, declaration);
    }

    /**
     * Sorts the children of element into schema order, and then sorts each child in turn.
     *
     * <ul>
     *   <li>Children are grouped into chunks, each ending with an anchor element or commented out element.
     *   <li>Each chunk carries the whitespace and descriptive comments that come before its anchor.
     *   <li>A comment on the same line as the previous anchor is kept with that anchor.
     *   <li>Chunks are sorted by schema position, unknown elements keep the position of their predecessor.
     * </ul>
     *
     * @param element element to sort
     * @param declaration xs:element declaration for element
     * @return true if any nodes were moved
     */
    private boolean sort(Element element, Element declaration) {
        ContentModel model = models.get(declaration);
        if (model == null) {
            model = new ContentModel(declaration);
            models.put(declaration, model);
        }
        List<Chunk> chunks = new ArrayList<>();
        List<Node> pending = new ArrayList<>();
        int position = -1;
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            pending.add(node);
            String name = anchorName(node, model);
            if (name != null) {
                position = model.position(name, position);
                chunks.add(new Chunk(name, node, position, pending));
                pending = new ArrayList<>();
            } else if (node instanceof Comment && !chunks.isEmpty() && sameLine(pending)) {
                chunks.get(chunks.size() - 1).nodes().addAll(pending);
                pending = new ArrayList<>();
            }
        }
        List<Chunk> sorted = new ArrayList<>(chunks);
        sorted.sort(Comparator.comparingInt(Chunk::position));

        boolean changed = !sorted.equals(chunks);
        if (changed) {
            for (Chunk chunk : sorted) {
                for (Node node : chunk.nodes()) {
                    element.removeChild(node);
                }
            }
            Node trailing = element.getFirstChild();
            for (Chunk chunk : sorted) {
                for (Node node : chunk.nodes()) {
                    element.insertBefore(node, trailing);
                }
            }
        }
        for (Chunk chunk : chunks) {
            Element childDeclaration = model.declaration(chunk.name());
            if (chunk.anchor() instanceof Element child && childDeclaration != null) {
                changed |= sort(child, childDeclaration);
            }
        }
        return changed;
    }

    /** Element name for an element, or a comment containing an element known to the content model. */
    private static String anchorName(Node node, ContentModel model) {
        if (node instanceof Element) {
            return node.getLocalName();
        }
        if (node instanceof Comment) {
            Matcher matcher = COMMENTED_ELEMENT.matcher(((Comment) node).getData());
            if (matcher.find() && model.contains(matcher.group(1))) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Checks if the nodes are on the same line as the previous anchor.
     *
     * @param nodes whitespace and comments following an anchor
     * @return true if none of the whitespace contains a newline
     */
    private static boolean sameLine(List<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof Text text && text.getData().contains("\n")) {
                return false;
            }
        }
        return true;
    }

    /** Local part of a prefixed name, as used in type, ref and base attributes. */
    private static String localName(String qname) {
        return qname.substring(qname.indexOf(':') + 1);
    }

    /** XML Schema child elements of a schema node. */
    private static List<Element> children(Element parent) {
        List<Element> children = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && XS.equals(node.getNamespaceURI())) {
                children.add((Element) node);
            }
        }
        return children;
    }

    /**
     * Child elements allowed by an element declaration, used to sort that element's children.
     *
     * <p>Built from the declaration's complex type, including any extension base type:
     *
     * <ul>
     *   <li>Each child element name has a position in schema order, elements in a choice share a position.
     *   <li>Each child element name has a declaration, used to sort the child's own children.
     *   <li>Gotcha: elements in a substitution group take the position of the group head.
     * </ul>
     */
    private class ContentModel {
        /** Schema order position by child element name. */
        private final Map<String, Integer> positions = new HashMap<>();

        /** xs:element declaration by child element name. */
        private final Map<String, Element> declarations = new HashMap<>();

        /** Position assigned to the next element added. */
        private int next;

        /**
         * Content model for the child elements allowed by a declaration.
         *
         * @param declaration xs:element declaration, simple types result in an empty content model
         */
        ContentModel(Element declaration) {
            Element type = type(declaration);
            if (type != null) {
                addType(type);
            }
        }

        /** True if name is an element allowed by this content model. */
        boolean contains(String name) {
            return positions.containsKey(name);
        }

        /**
         * Schema order position of an element.
         *
         * @param name element name
         * @param fallback position to use for elements unknown to the schema
         * @return schema order position, or fallback
         */
        int position(String name, int fallback) {
            return positions.getOrDefault(name, fallback);
        }

        /** xs:element declaration of an element, or null if unknown to the schema. */
        Element declaration(String name) {
            return declarations.get(name);
        }

        /**
         * Complex type of an element declaration, either named by the type attribute or defined inline.
         *
         * @param declaration xs:element declaration
         * @return xs:complexType, or null for simple types
         */
        private Element type(Element declaration) {
            if (declaration.hasAttribute("type")) {
                String typeName = localName(declaration.getAttribute("type"));
                return complexTypes.get(typeName);
            }
            for (Element child : children(declaration)) {
                if ("complexType".equals(child.getLocalName())) {
                    return child;
                }
            }
            return null;
        }

        /**
         * Adds the child elements of a complex type, including those inherited from an extension base type.
         *
         * @param type xs:complexType or xs:extension
         */
        private void addType(Element type) {
            for (Element child : children(type)) {
                switch (child.getLocalName()) {
                    case "sequence" -> addSequence(child);
                    case "choice" -> addChoice(child);
                    case "complexContent" -> {
                        for (Element extension : children(child)) {
                            Element base = complexTypes.get(localName(extension.getAttribute("base")));
                            if (base != null) {
                                addType(base);
                            }
                            addType(extension);
                        }
                    }
                    default -> {}
                }
            }
        }

        /**
         * Adds the elements of a sequence, each at the next position.
         *
         * @param sequence xs:sequence
         */
        private void addSequence(Element sequence) {
            for (Element child : children(sequence)) {
                switch (child.getLocalName()) {
                    case "element" -> addElement(child, next++);
                    case "sequence" -> addSequence(child);
                    case "choice" -> addChoice(child);
                    default -> {}
                }
            }
        }

        /**
         * Adds the elements of a choice, all sharing the same position so they keep their document order.
         *
         * @param choice xs:choice
         */
        private void addChoice(Element choice) {
            int position = next++;
            for (Element child : children(choice)) {
                if ("element".equals(child.getLocalName())) {
                    addElement(child, position);
                }
            }
        }

        /**
         * Adds an element at position, resolving references and substitution groups.
         *
         * @param particle xs:element declaration or reference
         * @param position schema order position
         */
        private void addElement(Element particle, int position) {
            Element declaration = particle;
            if (particle.hasAttribute("ref")) {
                declaration = globalElements.get(localName(particle.getAttribute("ref")));
                if (declaration == null) {
                    return;
                }
            }
            String name = declaration.getAttribute("name");
            positions.put(name, position);
            declarations.put(name, declaration);
            for (Element substitute : substitutions.getOrDefault(name, List.of())) {
                addElement(substitute, position);
            }
        }
    }

    /**
     * Group of nodes moved together when sorting.
     *
     * @param name element name of the anchor
     * @param anchor element, or comment containing a commented out element
     * @param position schema order position of the anchor
     * @param nodes anchor with the whitespace and comments that go with it
     */
    private record Chunk(String name, Node anchor, int position, List<Node> nodes) {}
}
