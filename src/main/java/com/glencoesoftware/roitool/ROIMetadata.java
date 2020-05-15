/*
 * Copyright (C) 2009-2016 Glencoe Software, Inc., University of Dundee
 * and Open Microscopy Environment. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.glencoesoftware.roitool;

import java.util.List;
import java.util.function.Function;

import ome.formats.model.UnitsFactory;
import ome.units.quantity.Length;
import ome.xml.model.AffineTransform;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.FillRule;
import ome.xml.model.enums.FontFamily;
import ome.xml.model.enums.FontStyle;
import ome.xml.model.enums.Marker;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.NonNegativeInteger;

import omero.RString;
import omero.model.Annotation;
import omero.model.Ellipse;
import omero.model.IObject;
import omero.model.Label;
import omero.model.Line;
import omero.model.Point;
import omero.model.Polygon;
import omero.model.Polyline;
import omero.model.Rectangle;
import omero.model.Roi;
import omero.model.Shape;

/**
 * An instance of {@link loci.formats.meta.MetadataRetrieve} that provides
 * metadata about OMERO ROIs.
 * Ported from <code>org.openmicroscopy.client.downloader.metadata</code>
 * @author m.t.b.carroll@dundee.ac.uk
 * @author Josh Moore josh at glencoesoftware.com
 * @author Chris Allan callan at blackcat.ca
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class ROIMetadata extends MetadataBase {

    private final List<Roi> roiList;

    /**
     * Construct a new ROIMetadata that provides access to the given list
     * of OMERO ROIs using the MetadataRetrieve API.
     * This is used to convert OMERO ROIs to OME-XML.
     *
     * @param lsids function used to get an object's LSID
     * @param rois list of OMERO ROIs
     */
    public ROIMetadata(Function<IObject, String> lsids, List<Roi> rois) {
        super(lsids);
        this.roiList = rois;
    }

    private static AffineTransform toTransform(
        omero.model.AffineTransform omeroTransform)
    {
        if (omeroTransform == null ||
            omeroTransform.getA00() == null ||
            omeroTransform.getA01() == null ||
            omeroTransform.getA02() == null ||
            omeroTransform.getA10() == null ||
            omeroTransform.getA11() == null ||
            omeroTransform.getA12() == null)
        {
            return null;
        }
        final AffineTransform schemaTransform = new AffineTransform();
        schemaTransform.setA00(omeroTransform.getA00().getValue());
        schemaTransform.setA01(omeroTransform.getA01().getValue());
        schemaTransform.setA02(omeroTransform.getA02().getValue());
        schemaTransform.setA10(omeroTransform.getA10().getValue());
        schemaTransform.setA11(omeroTransform.getA11().getValue());
        schemaTransform.setA12(omeroTransform.getA12().getValue());
        return schemaTransform;
    }

    private <X extends Shape> X getShape(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        if (roiIndex < 0 || shapeIndex < 0 || roiIndex >= roiList.size()) {
            return null;
        }
        final Roi roi = roiList.get(roiIndex);
        final List<Shape> shapes = roi.copyShapes();
        if (shapeIndex >= shapes.size()) {
            return null;
        }
        final Shape shape = shapes.get(shapeIndex);
        if (!expectedSubclass.isAssignableFrom(shape.getClass())) {
            return null;
        }
        return expectedSubclass.cast(shape);
    }

    @Override
    public int getROICount() {
        return roiList.size();
    }

    @Override
    public String getROIAnnotationRef(int roiIndex, int annotationRefIndex) {
        if (roiIndex < 0 || annotationRefIndex < 0 ||
            roiIndex >= roiList.size())
        {
            return null;
        }
        final Roi roi = roiList.get(roiIndex);
        final List<Annotation> annotations = roi.linkedAnnotationList();
        if (annotationRefIndex >= annotations.size()) {
            return null;
        }
        final Annotation annotation = annotations.get(annotationRefIndex);
        return getLsid(annotation);
    }

    @Override
    public int getROIAnnotationRefCount(int roiIndex) {
        if (roiIndex < 0 || roiIndex >= roiList.size()) {
            return -1;
        }
        final Roi roi = roiList.get(roiIndex);
        return roi.sizeOfAnnotationLinks();
    }

    @Override
    public String getROIDescription(int roiIndex) {
        if (roiIndex < 0 || roiIndex >= roiList.size()) {
            return null;
        }
        final Roi roi = roiList.get(roiIndex);
        return fromRType(roi.getDescription());
    }

    @Override
    public String getROIID(int roiIndex) {
        if (roiIndex < 0 || roiIndex >= roiList.size()) {
            return null;
        }
        final Roi roi = roiList.get(roiIndex);
        return getLsid(roi);
    }

    @Override
    public String getROIName(int roiIndex) {
        if (roiIndex < 0 || roiIndex >= roiList.size()) {
            return null;
        }
        final Roi roi = roiList.get(roiIndex);
        return fromRType(roi.getName());
    }

    @Override
    public int getShapeCount(int roiIndex) {
        if (roiIndex < 0 || roiIndex >= roiList.size()) {
            return -1;
        }
        final Roi roi = roiList.get(roiIndex);
        return roi.sizeOfShapes();
    }

    @Override
    public String getShapeType(int roiIndex, int shapeIndex) {
        final Shape shape = getShape(roiIndex, shapeIndex, Shape.class);
        if (shape == null) {
            return null;
        }
        Class<? extends Shape> shapeClass = null;
        Class<? extends Shape> currentClass = shape.getClass();
        while (currentClass != Shape.class) {
            shapeClass = currentClass;
            currentClass = currentClass.getSuperclass().asSubclass(Shape.class);
        }
        if (shapeClass == Rectangle.class) {
            return "Rectangle";
        }
        else {
            return shapeClass.getSimpleName();
        }
    }

    @Override
    public int getShapeAnnotationRefCount(int roiIndex, int shapeIndex) {
        final Shape shape = getShape(roiIndex, shapeIndex, Shape.class);
        if (shape == null) {
            return -1;
        }
        return shape.sizeOfAnnotationLinks();
    }

    private <X extends Shape> String getShapeAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex,
        Class<X> expectedSubclass)
    {
        if (annotationRefIndex < 0) {
            return null;
        }
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        final List<Annotation> annotations = shape.linkedAnnotationList();
        if (annotationRefIndex >= annotations.size()) {
            return null;
        }
        final Annotation annotation = annotations.get(annotationRefIndex);
        return getLsid(annotation);
    }

    private <X extends Shape> Color getShapeFillColor(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        final Integer color = fromRType(shape.getFillColor());
        if (color == null) {
            return null;
        }
        return new Color(color);
    }

    private <X extends Shape> FillRule getShapeFillRule(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        final String fillRuleName = fromRType(shape.getFillRule());
        if (fillRuleName == null) {
            return null;
        }
        final FillRule fillRule;
        try {
            fillRule = FillRule.fromString(fillRuleName);
        }
        catch (EnumerationException e) {
            return null;
        }
        return fillRule;
    }

    private <X extends Shape> FontFamily getShapeFontFamily(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        final String fontFamilyName = fromRType(shape.getFontFamily());
        if (fontFamilyName == null) {
            return null;
        }
        final FontFamily fontFamily;
        try {
            fontFamily = FontFamily.fromString(fontFamilyName);
        }
        catch (EnumerationException e) {
            return null;
        }
        return fontFamily;
    }

    private <X extends Shape> Length getShapeFontSize(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return UnitsFactory.convertLength(shape.getFontSize());
    }

    private <X extends Shape> FontStyle getShapeFontStyle(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        final String fontStyleName = fromRType(shape.getFontStyle());
        if (fontStyleName == null) {
            return null;
        }
        final FontStyle fontStyle;
        try {
            fontStyle = FontStyle.fromString(fontStyleName);
        }
        catch (EnumerationException e) {
            return null;
        }
        return fontStyle;
    }

    private <X extends Shape> String getShapeID(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return getLsid(shape);
    }

    private <X extends Shape> Boolean getShapeLocked(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return fromRType(shape.getLocked());
    }

    private <X extends Shape> Color getShapeStrokeColor(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        final Integer color = fromRType(shape.getStrokeColor());
        if (color == null) {
            return null;
        }
        return new Color(color);
    }

    private <X extends Shape> String getShapeStrokeDashArray(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return fromRType(shape.getStrokeDashArray());
    }

    private <X extends Shape> Length getShapeStrokeWidth(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return UnitsFactory.convertLength(shape.getStrokeWidth());
    }

    private <X extends Shape> NonNegativeInteger getShapeTheC(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return toNonNegativeInteger(shape.getTheC());
    }

    private <X extends Shape> NonNegativeInteger getShapeTheT(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return toNonNegativeInteger(shape.getTheT());
    }

    private <X extends Shape> NonNegativeInteger getShapeTheZ(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return toNonNegativeInteger(shape.getTheZ());
    }

    private <X extends Shape> AffineTransform getShapeTransform(
        int roiIndex, int shapeIndex, Class<X> expectedSubclass)
    {
        final X shape = getShape(roiIndex, shapeIndex, expectedSubclass);
        if (shape == null) {
            return null;
        }
        return toTransform(shape.getTransform());
    }

    @Override
    public String getEllipseAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex)
    {
        return getShapeAnnotationRef(roiIndex, shapeIndex,
            annotationRefIndex, Ellipse.class);
    }

    @Override
    public Color getEllipseFillColor(int roiIndex, int shapeIndex) {
        return getShapeFillColor(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public FillRule getEllipseFillRule(int roiIndex, int shapeIndex) {
        return getShapeFillRule(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public FontFamily getEllipseFontFamily(int roiIndex, int shapeIndex) {
        return getShapeFontFamily(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public Length getEllipseFontSize(int roiIndex, int shapeIndex) {
        return getShapeFontSize(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public FontStyle getEllipseFontStyle(int roiIndex, int shapeIndex) {
        return getShapeFontStyle(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public String getEllipseID(int roiIndex, int shapeIndex) {
        return getShapeID(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public Boolean getEllipseLocked(int roiIndex, int shapeIndex) {
        return getShapeLocked(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public Color getEllipseStrokeColor(int roiIndex, int shapeIndex) {
        return getShapeStrokeColor(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public String getEllipseStrokeDashArray(int roiIndex, int shapeIndex) {
        return getShapeStrokeDashArray(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public Length getEllipseStrokeWidth(int roiIndex, int shapeIndex) {
        return getShapeStrokeWidth(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public NonNegativeInteger getEllipseTheC(int roiIndex, int shapeIndex) {
        return getShapeTheC(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public NonNegativeInteger getEllipseTheT(int roiIndex, int shapeIndex) {
        return getShapeTheT(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public NonNegativeInteger getEllipseTheZ(int roiIndex, int shapeIndex) {
        return getShapeTheZ(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public AffineTransform getEllipseTransform(int roiIndex, int shapeIndex) {
        return getShapeTransform(roiIndex, shapeIndex, Ellipse.class);
    }

    @Override
    public Double getEllipseRadiusX(int roiIndex, int shapeIndex) {
        final Ellipse ellipse = getShape(roiIndex, shapeIndex, Ellipse.class);
        if (ellipse == null) {
            return null;
        }
        return fromRType(ellipse.getRadiusX());
    }

    @Override
    public Double getEllipseRadiusY(int roiIndex, int shapeIndex) {
        final Ellipse ellipse = getShape(roiIndex, shapeIndex, Ellipse.class);
        if (ellipse == null) {
            return null;
        }
        return fromRType(ellipse.getRadiusY());
    }

    @Override
    public String getEllipseText(int roiIndex, int shapeIndex) {
        final Ellipse ellipse = getShape(roiIndex, shapeIndex, Ellipse.class);
        if (ellipse == null) {
            return null;
        }
        return fromRType(ellipse.getTextValue());
    }

    @Override
    public Double getEllipseX(int roiIndex, int shapeIndex) {
        final Ellipse ellipse = getShape(roiIndex, shapeIndex, Ellipse.class);
        if (ellipse == null) {
            return null;
        }
        return fromRType(ellipse.getX());
    }

    @Override
    public Double getEllipseY(int roiIndex, int shapeIndex) {
        final Ellipse ellipse = getShape(roiIndex, shapeIndex, Ellipse.class);
        if (ellipse == null) {
            return null;
        }
        return fromRType(ellipse.getY());
    }

    @Override
    public String getLabelAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex)
    {
        return getShapeAnnotationRef(roiIndex, shapeIndex,
            annotationRefIndex, Label.class);
    }

    @Override
    public Color getLabelFillColor(int roiIndex, int shapeIndex) {
        return getShapeFillColor(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public FillRule getLabelFillRule(int roiIndex, int shapeIndex) {
        return getShapeFillRule(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public FontFamily getLabelFontFamily(int roiIndex, int shapeIndex) {
        return getShapeFontFamily(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public Length getLabelFontSize(int roiIndex, int shapeIndex) {
        return getShapeFontSize(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public FontStyle getLabelFontStyle(int roiIndex, int shapeIndex) {
        return getShapeFontStyle(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public String getLabelID(int roiIndex, int shapeIndex) {
        return getShapeID(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public Boolean getLabelLocked(int roiIndex, int shapeIndex) {
        return getShapeLocked(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public Color getLabelStrokeColor(int roiIndex, int shapeIndex) {
        return getShapeStrokeColor(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public String getLabelStrokeDashArray(int roiIndex, int shapeIndex) {
        return getShapeStrokeDashArray(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public Length getLabelStrokeWidth(int roiIndex, int shapeIndex) {
        return getShapeStrokeWidth(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public NonNegativeInteger getLabelTheC(int roiIndex, int shapeIndex) {
        return getShapeTheC(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public NonNegativeInteger getLabelTheT(int roiIndex, int shapeIndex) {
        return getShapeTheT(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public NonNegativeInteger getLabelTheZ(int roiIndex, int shapeIndex) {
        return getShapeTheZ(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public AffineTransform getLabelTransform(int roiIndex, int shapeIndex) {
        return getShapeTransform(roiIndex, shapeIndex, Label.class);
    }

    @Override
    public String getLabelText(int roiIndex, int shapeIndex) {
        final Label label = getShape(roiIndex, shapeIndex, Label.class);
        if (label == null) {
            return null;
        }
        return fromRType(label.getTextValue());
    }

    @Override
    public Double getLabelX(int roiIndex, int shapeIndex) {
        final Label label = getShape(roiIndex, shapeIndex, Label.class);
        if (label == null) {
            return null;
        }
        return fromRType(label.getX());
    }

    @Override
    public Double getLabelY(int roiIndex, int shapeIndex) {
        final Label label = getShape(roiIndex, shapeIndex, Label.class);
        if (label == null) {
            return null;
        }
        return fromRType(label.getY());
    }

    @Override
    public String getLineAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex)
    {
        return getShapeAnnotationRef(roiIndex, shapeIndex,
            annotationRefIndex, Line.class);
    }

    @Override
    public Color getLineFillColor(int roiIndex, int shapeIndex) {
        return getShapeFillColor(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public FillRule getLineFillRule(int roiIndex, int shapeIndex) {
        return getShapeFillRule(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public FontFamily getLineFontFamily(int roiIndex, int shapeIndex) {
        return getShapeFontFamily(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public Length getLineFontSize(int roiIndex, int shapeIndex) {
        return getShapeFontSize(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public FontStyle getLineFontStyle(int roiIndex, int shapeIndex) {
        return getShapeFontStyle(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public String getLineID(int roiIndex, int shapeIndex) {
        return getShapeID(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public Boolean getLineLocked(int roiIndex, int shapeIndex) {
        return getShapeLocked(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public Color getLineStrokeColor(int roiIndex, int shapeIndex) {
        return getShapeStrokeColor(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public String getLineStrokeDashArray(int roiIndex, int shapeIndex) {
        return getShapeStrokeDashArray(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public Length getLineStrokeWidth(int roiIndex, int shapeIndex) {
        return getShapeStrokeWidth(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public NonNegativeInteger getLineTheC(int roiIndex, int shapeIndex) {
        return getShapeTheC(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public NonNegativeInteger getLineTheT(int roiIndex, int shapeIndex) {
        return getShapeTheT(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public NonNegativeInteger getLineTheZ(int roiIndex, int shapeIndex) {
        return getShapeTheZ(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public AffineTransform getLineTransform(int roiIndex, int shapeIndex) {
        return getShapeTransform(roiIndex, shapeIndex, Line.class);
    }

    @Override
    public Marker getLineMarkerStart(int roiIndex, int shapeIndex) {
        final Line line = getShape(roiIndex, shapeIndex, Line.class);
        if (line == null) {
            return null;
        }
        final RString markerStart = line.getMarkerStart();
        if (markerStart == null) {
            return null;
        }
        try {
            return Marker.fromString(markerStart.getValue());
        }
        catch (EnumerationException ex) {
            return null;
        }
    }

    @Override
    public Marker getLineMarkerEnd(int roiIndex, int shapeIndex) {
        final Line line = getShape(roiIndex, shapeIndex, Line.class);
        if (line == null) {
            return null;
        }
        final RString markerEnd = line.getMarkerEnd();
        if (markerEnd == null) {
            return null;
        }
        try {
            return Marker.fromString(markerEnd.getValue());
        }
        catch (EnumerationException ex) {
            return null;
        }
    }

    @Override
    public String getLineText(int roiIndex, int shapeIndex) {
        final Line line = getShape(roiIndex, shapeIndex, Line.class);
        if (line == null) {
            return null;
        }
        return fromRType(line.getTextValue());
    }

    @Override
    public Double getLineX1(int roiIndex, int shapeIndex) {
        final Line line = getShape(roiIndex, shapeIndex, Line.class);
        if (line == null) {
            return null;
        }
        return fromRType(line.getX1());
    }

    @Override
    public Double getLineX2(int roiIndex, int shapeIndex) {
        final Line line = getShape(roiIndex, shapeIndex, Line.class);
        if (line == null) {
            return null;
        }
        return fromRType(line.getX2());
    }

    @Override
    public Double getLineY1(int roiIndex, int shapeIndex) {
        final Line line = getShape(roiIndex, shapeIndex, Line.class);
        if (line == null) {
            return null;
        }
        return fromRType(line.getY1());
    }

    @Override
    public Double getLineY2(int roiIndex, int shapeIndex) {
        final Line line = getShape(roiIndex, shapeIndex, Line.class);
        if (line == null) {
            return null;
        }
        return fromRType(line.getY2());
    }

    @Override
    public String getPointAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex)
    {
        return getShapeAnnotationRef(roiIndex, shapeIndex,
            annotationRefIndex, Point.class);
    }

    @Override
    public Color getPointFillColor(int roiIndex, int shapeIndex) {
        return getShapeFillColor(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public FillRule getPointFillRule(int roiIndex, int shapeIndex) {
        return getShapeFillRule(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public FontFamily getPointFontFamily(int roiIndex, int shapeIndex) {
        return getShapeFontFamily(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public Length getPointFontSize(int roiIndex, int shapeIndex) {
        return getShapeFontSize(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public FontStyle getPointFontStyle(int roiIndex, int shapeIndex) {
        return getShapeFontStyle(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public String getPointID(int roiIndex, int shapeIndex) {
        return getShapeID(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public Boolean getPointLocked(int roiIndex, int shapeIndex) {
        return getShapeLocked(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public Color getPointStrokeColor(int roiIndex, int shapeIndex) {
        return getShapeStrokeColor(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public String getPointStrokeDashArray(int roiIndex, int shapeIndex) {
        return getShapeStrokeDashArray(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public Length getPointStrokeWidth(int roiIndex, int shapeIndex) {
        return getShapeStrokeWidth(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public NonNegativeInteger getPointTheC(int roiIndex, int shapeIndex) {
        return getShapeTheC(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public NonNegativeInteger getPointTheT(int roiIndex, int shapeIndex) {
        return getShapeTheT(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public NonNegativeInteger getPointTheZ(int roiIndex, int shapeIndex) {
        return getShapeTheZ(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public AffineTransform getPointTransform(int roiIndex, int shapeIndex) {
        return getShapeTransform(roiIndex, shapeIndex, Point.class);
    }

    @Override
    public String getPointText(int roiIndex, int shapeIndex) {
        final Point point = getShape(roiIndex, shapeIndex, Point.class);
        if (point == null) {
            return null;
        }
        return fromRType(point.getTextValue());
    }

    @Override
    public Double getPointX(int roiIndex, int shapeIndex) {
        final Point point = getShape(roiIndex, shapeIndex, Point.class);
        if (point == null) {
            return null;
        }
        return fromRType(point.getX());
    }

    @Override
    public Double getPointY(int roiIndex, int shapeIndex) {
        final Point point = getShape(roiIndex, shapeIndex, Point.class);
        if (point == null) {
            return null;
        }
        return fromRType(point.getY());
    }

    @Override
    public String getPolygonAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex)
    {
        return getShapeAnnotationRef(roiIndex, shapeIndex,
            annotationRefIndex, Polygon.class);
    }

    @Override
    public Color getPolygonFillColor(int roiIndex, int shapeIndex) {
        return getShapeFillColor(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public FillRule getPolygonFillRule(int roiIndex, int shapeIndex) {
        return getShapeFillRule(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public FontFamily getPolygonFontFamily(int roiIndex, int shapeIndex) {
        return getShapeFontFamily(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public Length getPolygonFontSize(int roiIndex, int shapeIndex) {
        return getShapeFontSize(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public FontStyle getPolygonFontStyle(int roiIndex, int shapeIndex) {
        return getShapeFontStyle(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public String getPolygonID(int roiIndex, int shapeIndex) {
        return getShapeID(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public Boolean getPolygonLocked(int roiIndex, int shapeIndex) {
        return getShapeLocked(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public Color getPolygonStrokeColor(int roiIndex, int shapeIndex) {
        return getShapeStrokeColor(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public String getPolygonStrokeDashArray(int roiIndex, int shapeIndex) {
        return getShapeStrokeDashArray(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public Length getPolygonStrokeWidth(int roiIndex, int shapeIndex) {
        return getShapeStrokeWidth(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public NonNegativeInteger getPolygonTheC(int roiIndex, int shapeIndex) {
        return getShapeTheC(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public NonNegativeInteger getPolygonTheT(int roiIndex, int shapeIndex) {
        return getShapeTheT(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public NonNegativeInteger getPolygonTheZ(int roiIndex, int shapeIndex) {
        return getShapeTheZ(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public AffineTransform getPolygonTransform(int roiIndex, int shapeIndex) {
        return getShapeTransform(roiIndex, shapeIndex, Polygon.class);
    }

    @Override
    public String getPolygonPoints(int roiIndex, int shapeIndex) {
        final Polygon polygon = getShape(roiIndex, shapeIndex, Polygon.class);
        if (polygon == null) {
            return null;
        }
        return fromRType(polygon.getPoints());
    }

    @Override
    public String getPolygonText(int roiIndex, int shapeIndex) {
        final Polygon polygon = getShape(roiIndex, shapeIndex, Polygon.class);
        if (polygon == null) {
            return null;
        }
        return fromRType(polygon.getTextValue());
    }

    @Override
    public String getPolylineAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex)
    {
        return getShapeAnnotationRef(roiIndex, shapeIndex,
            annotationRefIndex, Polyline.class);
    }

    @Override
    public Color getPolylineFillColor(int roiIndex, int shapeIndex) {
        return getShapeFillColor(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public FillRule getPolylineFillRule(int roiIndex, int shapeIndex) {
        return getShapeFillRule(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public FontFamily getPolylineFontFamily(int roiIndex, int shapeIndex) {
        return getShapeFontFamily(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public Length getPolylineFontSize(int roiIndex, int shapeIndex) {
        return getShapeFontSize(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public FontStyle getPolylineFontStyle(int roiIndex, int shapeIndex) {
        return getShapeFontStyle(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public String getPolylineID(int roiIndex, int shapeIndex) {
        return getShapeID(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public Boolean getPolylineLocked(int roiIndex, int shapeIndex) {
        return getShapeLocked(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public Color getPolylineStrokeColor(int roiIndex, int shapeIndex) {
        return getShapeStrokeColor(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public String getPolylineStrokeDashArray(int roiIndex, int shapeIndex) {
        return getShapeStrokeDashArray(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public Length getPolylineStrokeWidth(int roiIndex, int shapeIndex) {
        return getShapeStrokeWidth(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public NonNegativeInteger getPolylineTheC(int roiIndex, int shapeIndex) {
        return getShapeTheC(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public NonNegativeInteger getPolylineTheT(int roiIndex, int shapeIndex) {
        return getShapeTheT(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public NonNegativeInteger getPolylineTheZ(int roiIndex, int shapeIndex) {
        return getShapeTheZ(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public AffineTransform getPolylineTransform(int roiIndex, int shapeIndex) {
        return getShapeTransform(roiIndex, shapeIndex, Polyline.class);
    }

    @Override
    public Marker getPolylineMarkerStart(int roiIndex, int shapeIndex) {
        final Polyline polyline =
            getShape(roiIndex, shapeIndex, Polyline.class);
        if (polyline == null) {
            return null;
        }
        final RString markerStart = polyline.getMarkerStart();
        if (markerStart == null) {
            return null;
        }
        try {
            return Marker.fromString(markerStart.getValue());
        }
        catch (EnumerationException ex) {
            return null;
        }
    }

    @Override
    public Marker getPolylineMarkerEnd(int roiIndex, int shapeIndex) {
        final Polyline polyline =
            getShape(roiIndex, shapeIndex, Polyline.class);
        if (polyline == null) {
            return null;
        }
        final RString markerEnd = polyline.getMarkerEnd();
        if (markerEnd == null) {
            return null;
        }
        try {
            return Marker.fromString(markerEnd.getValue());
        }
        catch (EnumerationException ex) {
            return null;
        }
    }

    @Override
    public String getPolylinePoints(int roiIndex, int shapeIndex) {
        final Polyline polyline =
            getShape(roiIndex, shapeIndex, Polyline.class);
        if (polyline == null) {
            return null;
        }
        return fromRType(polyline.getPoints());
    }

    @Override
    public String getPolylineText(int roiIndex, int shapeIndex) {
        final Polyline polyline =
            getShape(roiIndex, shapeIndex, Polyline.class);
        if (polyline == null) {
            return null;
        }
        return fromRType(polyline.getTextValue());
    }

    @Override
    public String getRectangleAnnotationRef(
        int roiIndex, int shapeIndex, int annotationRefIndex)
    {
        return getShapeAnnotationRef(roiIndex, shapeIndex,
            annotationRefIndex, Rectangle.class);
    }

    @Override
    public Color getRectangleFillColor(int roiIndex, int shapeIndex) {
        return getShapeFillColor(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public FillRule getRectangleFillRule(int roiIndex, int shapeIndex) {
        return getShapeFillRule(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public FontFamily getRectangleFontFamily(int roiIndex, int shapeIndex) {
        return getShapeFontFamily(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public Length getRectangleFontSize(int roiIndex, int shapeIndex) {
        return getShapeFontSize(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public FontStyle getRectangleFontStyle(int roiIndex, int shapeIndex) {
        return getShapeFontStyle(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public String getRectangleID(int roiIndex, int shapeIndex) {
        return getShapeID(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public Boolean getRectangleLocked(int roiIndex, int shapeIndex) {
        return getShapeLocked(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public Color getRectangleStrokeColor(int roiIndex, int shapeIndex) {
        return getShapeStrokeColor(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public String getRectangleStrokeDashArray(int roiIndex, int shapeIndex) {
        return getShapeStrokeDashArray(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public Length getRectangleStrokeWidth(int roiIndex, int shapeIndex) {
        return getShapeStrokeWidth(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public NonNegativeInteger getRectangleTheC(int roiIndex, int shapeIndex) {
        return getShapeTheC(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public NonNegativeInteger getRectangleTheT(int roiIndex, int shapeIndex) {
        return getShapeTheT(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public NonNegativeInteger getRectangleTheZ(int roiIndex, int shapeIndex) {
        return getShapeTheZ(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public AffineTransform getRectangleTransform(int roiIndex, int shapeIndex) {
        return getShapeTransform(roiIndex, shapeIndex, Rectangle.class);
    }

    @Override
    public String getRectangleText(int roiIndex, int shapeIndex) {
        final Rectangle rectangle =
            getShape(roiIndex, shapeIndex, Rectangle.class);
        if (rectangle == null) {
            return null;
        }
        return fromRType(rectangle.getTextValue());
    }

    @Override
    public Double getRectangleHeight(int roiIndex, int shapeIndex) {
        final Rectangle rectangle =
            getShape(roiIndex, shapeIndex, Rectangle.class);
        if (rectangle == null) {
            return null;
        }
        return fromRType(rectangle.getHeight());
    }

    @Override
    public Double getRectangleWidth(int roiIndex, int shapeIndex) {
        final Rectangle rectangle =
            getShape(roiIndex, shapeIndex, Rectangle.class);
        if (rectangle == null) {
            return null;
        }
        return fromRType(rectangle.getWidth());
    }

    @Override
    public Double getRectangleX(int roiIndex, int shapeIndex) {
        final Rectangle rectangle =
            getShape(roiIndex, shapeIndex, Rectangle.class);
        if (rectangle == null) {
            return null;
        }
        return fromRType(rectangle.getX());
    }

    @Override
    public Double getRectangleY(int roiIndex, int shapeIndex) {
        final Rectangle rectangle =
            getShape(roiIndex, shapeIndex, Rectangle.class);
        if (rectangle == null) {
            return null;
        }
        return fromRType(rectangle.getY());
    }
}
