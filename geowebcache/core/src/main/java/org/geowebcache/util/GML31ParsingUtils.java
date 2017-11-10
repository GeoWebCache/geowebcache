/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geowebcache.util;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.geowebcache.grid.SRS;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;

/**
 * Utility class to parse GML 3.1 geometries out of an {@link XMLStreamReader}
 * <p>
 * Dislaimer: the code on this class was adapted from LGPL licensed GeoTools WFS module's
 * {@code XmlSimpleFeatureParser} class available <a href=
 * "http://svn.osgeo.org/geotools/trunk/modules/unsupported/wfs/src/main/java/org/geotools/data/wfs/v1_1_0/parsers/XmlSimpleFeatureParser.java"
 * >here</a>
 * </p>
 * 
 * @author Gabriel Roldan (TOPP)
 * @version $Id$
 */
@SuppressWarnings("nls")
public class GML31ParsingUtils {

    public static final class GML {

        public static final String GML_NS_URI = "http://www.opengis.net/gml";

        public static final QName Point = new QName(GML_NS_URI, "Point");

        public static final QName LineString = new QName(GML_NS_URI, "LineString");

        public static final QName Polygon = new QName(GML_NS_URI, "Polygon");

        public static final QName MultiPoint = new QName(GML_NS_URI, "MultiPoint");

        public static final QName MultiLineString = new QName(GML_NS_URI, "MultiLineString");

        public static final QName MultiSurface = new QName(GML_NS_URI, "MultiSurface");

        public static final QName MultiPolygon = new QName(GML_NS_URI, "MultiPolygon");

        public static final QName pointMembers = new QName(GML_NS_URI, "pointMembers");

        public static final QName pointMember = new QName(GML_NS_URI, "pointMember");

        public static final QName lineStringMember = new QName(GML_NS_URI, "lineStringMember");

        public static final QName surfaceMembers = new QName(GML_NS_URI, "surfaceMembers");

        public static final QName surfaceMember = new QName(GML_NS_URI, "surfaceMember");

        public static final QName polygonMember = new QName(GML_NS_URI, "polygonMember");

        public static final QName exterior = new QName(GML_NS_URI, "exterior");

        public static final QName interior = new QName(GML_NS_URI, "interior");

        public static final QName LinearRing = new QName(GML_NS_URI, "LinearRing");

        public static final QName pos = new QName(GML_NS_URI, "pos");

        public static final QName posList = new QName(GML_NS_URI, "posList");

    }

    private Map<SRS, GeometryFactory> factories = new HashMap<>();
    
    GeometryFactory getFactory(SRS srs) {
        return factories.computeIfAbsent(srs, srs2->new GeometryFactory(new PrecisionModel(), srs2.getNumber()));
    }

    /**
     * <p>
     * Precondition: reader cursor positioned on a geometry property (ej, {@code gml:Point}, etc)
     * </p>
     * <p>
     * Postcondition: reader gets positioned at the end tag of the element it started parsing the
     * geometry at
     * </p>
     * 
     * @return
     * @throws XMLStreamException
     */
    public Geometry parseGeometry(final XMLStreamReader reader) throws XMLStreamException {

        reader.require(START_ELEMENT, GML.GML_NS_URI, null);

        final QName startingGeometryTagName = reader.getName();
        int dimension = crsDimension(reader, 2);
        
        SRS srs = getSRS(reader.getAttributeValue(null, "srsName"));
        GeometryFactory fact = getFactory(srs);
        Geometry geom;
        if (GML.Point.equals(startingGeometryTagName)) {
            geom = parsePoint(reader, fact, dimension);
        } else if (GML.LineString.equals(startingGeometryTagName)) {
            geom = parseLineString(reader, fact, dimension);
        } else if (GML.Polygon.equals(startingGeometryTagName)) {
            geom = parsePolygon(reader, fact, dimension);
        } else if (GML.MultiPoint.equals(startingGeometryTagName)) {
            geom = parseMultiPoint(reader, fact, dimension);
        } else if (GML.MultiLineString.equals(startingGeometryTagName)) {
            geom = parseMultiLineString(reader, fact, dimension);
        } else if (GML.MultiSurface.equals(startingGeometryTagName)) {
            geom = parseMultiSurface(reader, fact, dimension);
        } else if (GML.MultiPolygon.equals(startingGeometryTagName)) {
            geom = parseMultiPolygon(reader, fact, dimension);
        } else {
            throw new IllegalStateException("Unrecognized geometry element "
                    + startingGeometryTagName);
        }
        
        reader.require(END_ELEMENT, startingGeometryTagName.getNamespaceURI(),
                startingGeometryTagName.getLocalPart());
        
        return geom;
    }
    
    SRS getSRS(String srsName) {
        Objects.requireNonNull(srsName);
        try {
            // Yes this is horrible but it's about all we can do given the current state of CRS/SRS
            // handling in GeoWebCache
            if(srsName.startsWith("urn:x-ogc:def:crs:EPSG:")) {
                return SRS.getSRS(Integer.parseInt(srsName.substring(srsName.lastIndexOf(':')+1)));
            } if(srsName.startsWith("urn:ogc:def:crs:EPSG:")) {
                return SRS.getSRS(Integer.parseInt(srsName.substring(srsName.lastIndexOf(':')+1)));
            } else if (srsName.startsWith("http://www.opengis.net/def/crs/EPSG/0/")) {
                return SRS.getSRS(Integer.parseInt(srsName.substring(srsName.lastIndexOf('/')+1)));
            } else if (srsName.matches("urn:ogc:def:crs:OGC:[0-9]\\.[0-9]:CRS:84")) {
                return SRS.getEPSG4326();
            }
            return null;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Illegal URI for srsName", e);
        }
        
    }

    /**
     * Parses a MultiPoint.
     * <p>
     * Precondition: reader positioned at a {@link GML#MultiPoint MultiPoint} start tag
     * </p>
     * <p>
     * Postcondition: reader positioned at the {@link GML#MultiPoint MultiPoint} end tag of the
     * starting tag
     * </p>
     * 
     * @throws XMLStreamException
     */
    private Geometry parseMultiPoint(XMLStreamReader reader, GeometryFactory geomFac, int dimension)
            throws XMLStreamException {
        Geometry geom;
        nextTag(reader);
        final QName memberTag = reader.getName();
        List<Point> points = new ArrayList<Point>(4);
        if (GML.pointMembers.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType()
                        && GML.pointMembers.equals(reader.getName())) {
                    // we're done
                    break;
                }
                Point p = parsePoint(reader, geomFac, dimension);
                points.add(p);
            }
            nextTag(reader);
        } else if (GML.pointMember.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                reader.require(START_ELEMENT, GML.GML_NS_URI, GML.Point.getLocalPart());

                Point p = parsePoint(reader, geomFac, dimension);
                points.add(p);
                nextTag(reader);
                reader.require(END_ELEMENT, GML.GML_NS_URI, GML.pointMember.getLocalPart());
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType() && GML.MultiPoint.equals(reader.getName())) {
                    // we're done
                    break;
                }
            }
        }
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.MultiPoint.getLocalPart());

        geom = geomFac.createMultiPoint(points.toArray(new Point[points.size()]));
        return geom;
    }

    /**
     * Parses a MultiLineString.
     * <p>
     * Precondition: reader positioned at a {@link GML#MultiLineString MultiLineString} start tag
     * </p>
     * <p>
     * Postcondition: reader positioned at the {@link GML#MultiLineString MultiLineString} end tag
     * of the starting tag
     * </p>
     * 
     * @throws XMLStreamException
     */
    private MultiLineString parseMultiLineString(XMLStreamReader reader, GeometryFactory geomFac, int dimension)
            throws XMLStreamException {
        MultiLineString geom;

        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.MultiLineString.getLocalPart());

        List<LineString> lines = new ArrayList<LineString>(2);

        while (true) {
            nextTag(reader);
            if (END_ELEMENT == reader.getEventType()
                    && GML.MultiLineString.equals(reader.getName())) {
                // we're done
                break;
            }
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.lineStringMember.getLocalPart());
            nextTag(reader);
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.LineString.getLocalPart());

            LineString line = parseLineString(reader, geomFac, dimension);
            lines.add(line);
            nextTag(reader);
            reader.require(END_ELEMENT, GML.GML_NS_URI, GML.lineStringMember.getLocalPart());
        }

        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.MultiLineString.getLocalPart());

        geom = geomFac.createMultiLineString(lines.toArray(new LineString[lines.size()]));
        return geom;
    }

    /**
     * Parses a MultiPolygon out of a MultiSurface element (because our geometry model only supports
     * MultiPolygon).
     * <p>
     * Precondition: reader positioned at a {@link GML#MultiSurface MultiSurface} start tag
     * </p>
     * <p>
     * Postcondition: reader positioned at the {@link GML#MultiSurface MultiSurface} end tag of the
     * starting tag
     * </p>
     * 
     * @param reader
     */
    private Geometry parseMultiSurface(XMLStreamReader reader, GeometryFactory geomFac, int dimension)
            throws XMLStreamException {
        Geometry geom;
        nextTag(reader);
        final QName memberTag = reader.getName();
        List<Polygon> polygons = new ArrayList<Polygon>(2);
        if (GML.surfaceMembers.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType()
                        && GML.surfaceMembers.equals(reader.getName())) {
                    // we're done
                    break;
                }
                Polygon p = parsePolygon(reader, geomFac, dimension);
                polygons.add(p);
            }
            nextTag(reader);
        } else if (GML.surfaceMember.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                Polygon p = parsePolygon(reader, geomFac, dimension);
                polygons.add(p);
                nextTag(reader);
                reader.require(END_ELEMENT, GML.GML_NS_URI, GML.surfaceMember.getLocalPart());
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType()
                        && GML.MultiSurface.equals(reader.getName())) {
                    // we're done
                    break;
                }
            }
        }
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.MultiSurface.getLocalPart());

        geom = geomFac.createMultiPolygon(polygons.toArray(new Polygon[polygons.size()]));
        return geom;
    }

    private Geometry parseMultiPolygon(XMLStreamReader reader, GeometryFactory geomFac, int dimension)
            throws XMLStreamException {

        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.MultiPolygon.getLocalPart());
        Geometry geom;
        List<Polygon> polygons = new ArrayList<Polygon>(2);
        nextTag(reader);
        while (true) {
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.polygonMember.getLocalPart());
            nextTag(reader);
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.Polygon.getLocalPart());
            Polygon p = parsePolygon(reader, geomFac, dimension);
            polygons.add(p);
            nextTag(reader);
            reader.require(END_ELEMENT, GML.GML_NS_URI, GML.polygonMember.getLocalPart());
            nextTag(reader);
            if (END_ELEMENT == reader.getEventType() && GML.MultiPolygon.equals(reader.getName())) {
                // we're done
                break;
            }
        }
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.MultiPolygon.getLocalPart());

        geom = geomFac.createMultiPolygon(polygons.toArray(new Polygon[polygons.size()]));
        return geom;
    }

    /**
     * Parses a polygon.
     * <p>
     * Precondition: reader positioned at a {@link GML#Polygon Polygon} start tag
     * </p>
     * <p>
     * Postcondition: reader positioned at the {@link GML#Polygon Polygon} end tag of the starting
     * tag
     * </p>
     * 
     * @param reader
     * 
     * @param dimension
     * @return
     * @throws XMLStreamException
     */
    private Polygon parsePolygon(XMLStreamReader reader, GeometryFactory geomFac, int dimension) throws XMLStreamException {
        Polygon geom;
        LinearRing shell;
        List<LinearRing> holes = null;

        nextTag(reader);
        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.exterior.getLocalPart());
        nextTag(reader);
        shell = parseLinearRing(reader, geomFac, dimension);
        nextTag(reader);
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.exterior.getLocalPart());
        nextTag(reader);

        if (GML.interior.equals(reader.getName())) {
            // parse interior rings
            holes = new ArrayList<LinearRing>(2);
            while (true) {
                nextTag(reader);
                LinearRing hole = parseLinearRing(reader, geomFac, dimension);
                holes.add(hole);
                nextTag(reader);
                reader.require(END_ELEMENT, GML.GML_NS_URI, GML.interior.getLocalPart());
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType()) {
                    // we're done
                    reader.require(END_ELEMENT, GML.GML_NS_URI, GML.Polygon.getLocalPart());
                    break;
                }
            }
        }

        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.Polygon.getLocalPart());

        LinearRing[] holesArray = null;
        if (holes != null) {
            holesArray = holes.toArray(new LinearRing[holes.size()]);
        }
        geom = geomFac.createPolygon(shell, holesArray);

        return geom;
    }

    private LinearRing parseLinearRing(final XMLStreamReader reader, GeometryFactory geomFac, final int dimension)
            throws XMLStreamException {

        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.LinearRing.getLocalPart());
        nextTag(reader);
        QName tagName = reader.getName();
        Coordinate[] shellCoords;
        if (GML.pos.equals(tagName)) {
            Coordinate[] point;
            List<Coordinate> coords = new ArrayList<Coordinate>();
            int eventType;
            do {
                point = parseCoordListContent(reader, dimension);
                coords.add(point[0]);
                nextTag(reader);
                tagName = reader.getName();
                eventType = reader.getEventType();
            } while (eventType == START_ELEMENT && GML.pos.equals(tagName));

            shellCoords = coords.toArray(new Coordinate[coords.size()]);

        } else if (GML.posList.equals(tagName)) {
            // reader.require(START_ELEMENT, GML.NAMESPACE,
            // GML.posList.getLocalPart());
            // crs = crs(reader, crs);
            shellCoords = parseCoordListContent(reader, dimension);
            nextTag(reader);
        } else {
            throw new IllegalStateException("Expected posList or pos inside LinearRing: " + tagName);
        }
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.LinearRing.getLocalPart());
        LinearRing linearRing = geomFac.createLinearRing(shellCoords);
        // linearRing.setUserData(crs);
        return linearRing;
    }

    private LineString parseLineString(XMLStreamReader reader, GeometryFactory geomFac, int dimension)
            throws XMLStreamException {
        LineString geom;
        nextTag(reader);
        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.posList.getLocalPart());
        // crs = crs(reader, crs);
        Coordinate[] coords = parseCoordListContent(reader, dimension);
        geom = geomFac.createLineString(coords);
        // geom.setUserData(crs);
        nextTag(reader);
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.LineString.getLocalPart());
        return geom;
    }

    private Point parsePoint(XMLStreamReader reader, GeometryFactory geomFac, int dimension) throws XMLStreamException {

        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.Point.getLocalPart());

        Point geom;
        nextTag(reader);
        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.pos.getLocalPart());
        // crs = crs(reader, crs);
        Coordinate[] coords = parseCoordListContent(reader, dimension);
        geom = geomFac.createPoint(coords[0]);
        // geom.setUserData(crs);
        consume(reader, GML.Point);

        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.Point.getLocalPart());
        return geom;
    }

    private int crsDimension(final XMLStreamReader reader, final int defaultValue) {
        String srsDimension = reader.getAttributeValue(null, "srsDimension");
        if (srsDimension == null) {
            return defaultValue;
        }
        int dimension = Integer.valueOf(srsDimension);
        return dimension;
    }

    private Coordinate[] parseCoordListContent(final XMLStreamReader reader, int dimension)
            throws XMLStreamException {

        reader.require(START_ELEMENT, null, null);
        final QName tagName = reader.getName();
        // we might be on a posList tag with srsDimension defined
        dimension = crsDimension(reader, dimension);
        String rawTextValue = text(reader);
        Coordinate[] coords = toCoordList(rawTextValue, dimension);
        consume(reader, tagName);
        return coords;
    }

    private Coordinate[] toCoordList(String rawTextValue, final int dimension) {
        rawTextValue = rawTextValue.trim();
        rawTextValue = rawTextValue.replaceAll("\n", " ");
        rawTextValue = rawTextValue.replaceAll("\r", " ");
        String[] split = rawTextValue.trim().split(" +");
        final int ordinatesLength = split.length;
        if (ordinatesLength % dimension != 0) {
            throw new IllegalArgumentException("Number of ordinates (" + ordinatesLength
                    + ") does not match crs dimension: " + dimension);
        }
        final int nCoords = ordinatesLength / dimension;
        Coordinate[] coords = new Coordinate[nCoords];
        Coordinate coord;
        int currCoordIdx = 0;
        double x, y, z;
        for (int i = 0; i < ordinatesLength; i += dimension) {
            x = Double.valueOf(split[i]);
            y = Double.valueOf(split[i + 1]);
            if (dimension > 2) {
                z = Double.valueOf(split[i + 2]);
                coord = new Coordinate(x, y, z);
            } else {
                coord = new Coordinate(x, y);
            }
            coords[currCoordIdx] = coord;
            currCoordIdx++;
        }
        return coords;
    }

    /**
     * Safely advances until the next tag element (either start or end element) and returns its
     * name, or {@code null} in case the end of document is reached before any tag
     * 
     * @param reader
     * @return
     * @throws XMLStreamException
     */
    public static QName nextTag(XMLStreamReader reader) throws XMLStreamException {
    
        while (reader.next() != END_DOCUMENT) {
            if (reader.isStartElement() || reader.isEndElement()) {
                return reader.getName();
            }
        }
    
        return null;
    }

    /**
     * Consumes the current element (given by tagName) until it's end element is fount (assuming
     * there's no nested element called the same)
     * 
     * @param reader
     * @param tagName
     * @throws XMLStreamException
     */
    public static void consume(XMLStreamReader reader, QName tagName) throws XMLStreamException {
    
        if (reader.getEventType() == END_ELEMENT && tagName.equals(reader.getName())) {
            return;// already consumed
        }
    
        while (reader.next() != END_DOCUMENT) {
            if (reader.isEndElement() && tagName.equals(reader.getName())) {
                return;
            }
        }
    }

    /**
     * Being at a start element tag, returns its coalesced text value
     * 
     * @param reader
     * @return
     * @throws XMLStreamException
     */
    public static String text(XMLStreamReader reader) throws XMLStreamException {
        reader.require(START_ELEMENT, null, null);
        StringBuilder sb = new StringBuilder();
    
        while (true) {
            reader.next();
            if (reader.isCharacters() || reader.isWhiteSpace()) {
                sb.append(reader.getText());
            } else if (reader.isEndElement()) {
                break;
            }
        }
        return sb.toString();
    }

}
