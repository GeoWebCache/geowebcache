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
package org.geowebcache.georss;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.geowebcache.georss.GeoRSSParsingUtils.consume;
import static org.geowebcache.georss.GeoRSSParsingUtils.nextTag;
import static org.geowebcache.georss.GeoRSSParsingUtils.text;

import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Utility class to parse GML 3.1 geometries out of an {@link XMLStreamReader}
 *
 * <p>Dislaimer: the code on this class was adapted from LGPL licensed GeoTools WFS module's
 * {@code XmlSimpleFeatureParser} class available <a href=
 * "http://svn.osgeo.org/geotools/trunk/modules/unsupported/wfs/src/main/java/org/geotools/data/wfs/v1_1_0/parsers/XmlSimpleFeatureParser.java"
 * >here</a>
 *
 * @author Gabriel Roldan (TOPP)
 * @version $Id$
 */
class GML31ParsingUtils {

    private final GeometryFactory geomFac;

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

    public GML31ParsingUtils() {
        this(new GeometryFactory());
    }

    public GML31ParsingUtils(GeometryFactory gFac) {
        this.geomFac = gFac;
    }

    /**
     * Precondition: reader cursor positioned on a geometry property (ej, {@code gml:Point}, etc)
     *
     * <p>Postcondition: reader gets positioned at the end tag of the element it started parsing the geometry at
     */
    public Geometry parseGeometry(final XMLStreamReader reader) throws XMLStreamException {

        reader.require(START_ELEMENT, GML.GML_NS_URI, null);

        final QName startingGeometryTagName = reader.getName();
        int dimension = crsDimension(reader, 2);

        Geometry geom;
        if (GML.Point.equals(startingGeometryTagName)) {
            geom = parsePoint(reader, dimension);
        } else if (GML.LineString.equals(startingGeometryTagName)) {
            geom = parseLineString(reader, dimension);
        } else if (GML.Polygon.equals(startingGeometryTagName)) {
            geom = parsePolygon(reader, dimension);
        } else if (GML.MultiPoint.equals(startingGeometryTagName)) {
            geom = parseMultiPoint(reader, dimension);
        } else if (GML.MultiLineString.equals(startingGeometryTagName)) {
            geom = parseMultiLineString(reader, dimension);
        } else if (GML.MultiSurface.equals(startingGeometryTagName)) {
            geom = parseMultiSurface(reader, dimension);
        } else if (GML.MultiPolygon.equals(startingGeometryTagName)) {
            geom = parseMultiPolygon(reader, dimension);
        } else {
            throw new IllegalStateException("Unrecognized geometry element " + startingGeometryTagName);
        }

        reader.require(END_ELEMENT, startingGeometryTagName.getNamespaceURI(), startingGeometryTagName.getLocalPart());

        return geom;
    }

    /**
     * Parses a MultiPoint.
     *
     * <p>Precondition: reader positioned at a {@link GML#MultiPoint MultiPoint} start tag
     *
     * <p>Postcondition: reader positioned at the {@link GML#MultiPoint MultiPoint} end tag of the starting tag
     */
    private Geometry parseMultiPoint(XMLStreamReader reader, int dimension) throws XMLStreamException {
        Geometry geom;
        nextTag(reader);
        final QName memberTag = reader.getName();
        List<Point> points = new ArrayList<>(4);
        if (GML.pointMembers.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType() && GML.pointMembers.equals(reader.getName())) {
                    // we're done
                    break;
                }
                Point p = parsePoint(reader, dimension);
                points.add(p);
            }
            nextTag(reader);
        } else if (GML.pointMember.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                reader.require(START_ELEMENT, GML.GML_NS_URI, GML.Point.getLocalPart());

                Point p = parsePoint(reader, dimension);
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
     *
     * <p>Precondition: reader positioned at a {@link GML#MultiLineString MultiLineString} start tag
     *
     * <p>Postcondition: reader positioned at the {@link GML#MultiLineString MultiLineString} end tag of the starting
     * tag
     */
    private MultiLineString parseMultiLineString(XMLStreamReader reader, int dimension) throws XMLStreamException {
        MultiLineString geom;

        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.MultiLineString.getLocalPart());

        List<LineString> lines = new ArrayList<>(2);

        while (true) {
            nextTag(reader);
            if (END_ELEMENT == reader.getEventType() && GML.MultiLineString.equals(reader.getName())) {
                // we're done
                break;
            }
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.lineStringMember.getLocalPart());
            nextTag(reader);
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.LineString.getLocalPart());

            LineString line = parseLineString(reader, dimension);
            lines.add(line);
            nextTag(reader);
            reader.require(END_ELEMENT, GML.GML_NS_URI, GML.lineStringMember.getLocalPart());
        }

        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.MultiLineString.getLocalPart());

        geom = geomFac.createMultiLineString(lines.toArray(new LineString[lines.size()]));
        return geom;
    }

    /**
     * Parses a MultiPolygon out of a MultiSurface element (because our geometry model only supports MultiPolygon).
     *
     * <p>Precondition: reader positioned at a {@link GML#MultiSurface MultiSurface} start tag
     *
     * <p>Postcondition: reader positioned at the {@link GML#MultiSurface MultiSurface} end tag of the starting tag
     */
    private Geometry parseMultiSurface(XMLStreamReader reader, int dimension) throws XMLStreamException {
        Geometry geom;
        nextTag(reader);
        final QName memberTag = reader.getName();
        List<Polygon> polygons = new ArrayList<>(2);
        if (GML.surfaceMembers.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType() && GML.surfaceMembers.equals(reader.getName())) {
                    // we're done
                    break;
                }
                Polygon p = parsePolygon(reader, dimension);
                polygons.add(p);
            }
            nextTag(reader);
        } else if (GML.surfaceMember.equals(memberTag)) {
            while (true) {
                nextTag(reader);
                Polygon p = parsePolygon(reader, dimension);
                polygons.add(p);
                nextTag(reader);
                reader.require(END_ELEMENT, GML.GML_NS_URI, GML.surfaceMember.getLocalPart());
                nextTag(reader);
                if (END_ELEMENT == reader.getEventType() && GML.MultiSurface.equals(reader.getName())) {
                    // we're done
                    break;
                }
            }
        }
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.MultiSurface.getLocalPart());

        geom = geomFac.createMultiPolygon(polygons.toArray(new Polygon[polygons.size()]));
        return geom;
    }

    private Geometry parseMultiPolygon(XMLStreamReader reader, int dimension) throws XMLStreamException {

        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.MultiPolygon.getLocalPart());
        Geometry geom;
        List<Polygon> polygons = new ArrayList<>(2);
        nextTag(reader);
        while (true) {
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.polygonMember.getLocalPart());
            nextTag(reader);
            reader.require(START_ELEMENT, GML.GML_NS_URI, GML.Polygon.getLocalPart());
            Polygon p = parsePolygon(reader, dimension);
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
     *
     * <p>Precondition: reader positioned at a {@link GML#Polygon Polygon} start tag
     *
     * <p>Postcondition: reader positioned at the {@link GML#Polygon Polygon} end tag of the starting tag
     */
    private Polygon parsePolygon(XMLStreamReader reader, int dimension) throws XMLStreamException {
        Polygon geom;
        LinearRing shell;
        List<LinearRing> holes = null;

        nextTag(reader);
        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.exterior.getLocalPart());
        nextTag(reader);
        shell = parseLinearRing(reader, dimension);
        nextTag(reader);
        reader.require(END_ELEMENT, GML.GML_NS_URI, GML.exterior.getLocalPart());
        nextTag(reader);

        if (GML.interior.equals(reader.getName())) {
            // parse interior rings
            holes = new ArrayList<>(2);
            while (true) {
                nextTag(reader);
                LinearRing hole = parseLinearRing(reader, dimension);
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

    private LinearRing parseLinearRing(final XMLStreamReader reader, final int dimension) throws XMLStreamException {

        reader.require(START_ELEMENT, GML.GML_NS_URI, GML.LinearRing.getLocalPart());
        nextTag(reader);
        QName tagName = reader.getName();
        Coordinate[] shellCoords;
        if (GML.pos.equals(tagName)) {
            Coordinate[] point;
            List<Coordinate> coords = new ArrayList<>();
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

    private LineString parseLineString(XMLStreamReader reader, int dimension) throws XMLStreamException {
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

    private Point parsePoint(XMLStreamReader reader, int dimension) throws XMLStreamException {

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

    private Coordinate[] parseCoordListContent(final XMLStreamReader reader, int dimension) throws XMLStreamException {

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
            throw new IllegalArgumentException(
                    "Number of ordinates (" + ordinatesLength + ") does not match crs dimension: " + dimension);
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
}
