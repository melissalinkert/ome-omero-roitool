/**
 * -----------------------------------------------------------------------------
 *   Copyright (C) 2020 Glencoe Software, Inc. All rights reserved.
 *
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License along
 *   with this program; if not, write to the Free Software Foundation, Inc.,
 *   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * ------------------------------------------------------------------------------
 *
 * QuPath zarr exporter
 *
 * This script will export ROIs (detections and annotations) as a mask
 * to a zarr pyramid.
 *
 * Instructions:
 *   - Open the QuPath file containing the ROIs to be exported
 *   - Open this file in the QuPath "Script editor"
 *   - Choose "Run" from the Run menu
 *   - When prompted, choose the location and filename for the exported pyramid
 */


import java.awt.BasicStroke
import loci.formats.gui.AWTImageTools
import qupath.lib.common.ColorTools
import qupath.lib.common.GeneralTools
import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathCellObject
import qupath.lib.objects.PathROIObject
import qupath.lib.roi.*

import org.janelia.saalfeldlab.n5.ByteArrayDataBlock
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.Lz4Compression
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter

// first check the version; only 0.2.0-m10 and later are supported
version = GeneralTools.getVersion()
versionTokens = version.split("-")
if (!versionTokens[0].equals("0.2.0") || (versionTokens.length == 2 && Integer.parseInt(versionTokens[1].substring(1)) < 10)) {
    throw new RuntimeException("Unsupported QuPath version: " + version)
}

detections = QPEx.getDetectionObjects()
annotations = QPEx.getAnnotationObjects()
QPEx.getProjectEntryMetadataValue()
rois = detections + annotations

tileSize = 1024

void writeMask(qupath.lib.roi.interfaces.ROI roi, N5ZarrWriter writer, String id) {
            // construct a blank image matching the ROI bounding box
            originX = roi.getBoundsX()
            originY = roi.getBoundsY()
            width = roi.getBoundsWidth()
            height = roi.getBoundsHeight()

            blockSize = [tileSize, tileSize, 1, 1, 1] as int[]
            dimensions = [getCurrentServer().getWidth(), getCurrentServer().getHeight(), 1, 1, 1] as long[]
            pathName = "/labels/" + id

            writer.createDataset(pathName, dimensions, blockSize, DataType.UINT8, new Lz4Compression())

            for (y=originY-(originY % tileSize); y<(originY + height); y+=tileSize) {
                for (x=originX-(originX % tileSize); x<(originX + width); x+=tileSize) {
                    img = new BufferedImage(tileSize as int, tileSize as int, BufferedImage.TYPE_BYTE_GRAY)

                    // draw the shape onto the image
                    graphics = img.createGraphics()
                    graphics.translate(-1 * x, -1 * y)
                    strokeWidth = PathPrefs.annotationStrokeThicknessProperty().get()
                    graphics.setStroke(new BasicStroke(strokeWidth))
                    graphics.setColor(java.awt.Color.WHITE)

                    drawableShape = roi.getShape()
                    graphics.draw(drawableShape)

                    pixels = AWTImageTools.getBytes(img, false)
                    row = y / tileSize
                    col = x / tileSize

                    gridPosition = [col, row, 0, 0, 0] as long[]
                    dataBlock = new ByteArrayDataBlock(blockSize, gridPosition, pixels)

                    writer.writeBlock(pathName, writer.getDatasetAttributes(pathName), dataBlock)
                }
            }
}

file = Dialogs.promptToSaveFile("Choose Zarr export location", null, null, "Zarr", ".zarr")
writer = new N5ZarrWriter(file.getAbsolutePath())

rois.eachWithIndex { PathROIObject path, int i ->
    def roi = path.getROI()
    print(String.format("ROI type: %s", roi.class))

    writeMask(roi, writer, i as String)

    // PathCellObjects are the result of running a cell detection
    // the result of getROI is the cell boundary
    // the nucleus is defined as a separate ROI and should be included
    // in the OME ROI as a separate shape
    if (path instanceof PathCellObject) {
        writeMask(path.getNucleusROI(), writer, String.format("%d-nucleus", i))
    }
}
