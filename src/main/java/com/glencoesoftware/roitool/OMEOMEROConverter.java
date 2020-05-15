/*
 * Copyright (C) 2019 Glencoe Software, Inc. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.glencoesoftware.roitool;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Glacier2.CannotCreateSessionException;
import Glacier2.PermissionDeniedException;
import com.google.common.collect.ImmutableMap;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.MissingLibraryException;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import ome.specification.XMLWriter;
import ome.system.Login;
import ome.xml.meta.MetadataConverter;
import ome.xml.model.OME;
import omero.ServerError;
import omero.api.IConfigPrx;
import omero.model.Annotation;
import omero.model.IObject;
import omero.model.Roi;
import omero.sys.ParametersI;

public class OMEOMEROConverter {

    private static final Logger LOG =
            LoggerFactory.getLogger(OMEOMEROConverter.class);

    public static final ImmutableMap<String, String> ALL_GROUPS_CONTEXT =
            ImmutableMap.of(Login.OMERO_GROUP, "-1");

    private final long imageId;

    private final ROIMetadataStoreClient target;

    private final OMEXMLService omeXmlService;

    private String lsidFormat;

    /**
     * Construct a new converter for the given OMERO image.
     *
     * @param image OMERO Image ID
     */
    public OMEOMEROConverter(long image)
            throws ServerError, DependencyException
    {
        this.imageId = image;
        this.target = new ROIMetadataStoreClient();
        ServiceFactory factory = new ServiceFactory();
        this.omeXmlService = factory.getInstance(OMEXMLService.class);
    }

    /**
     * Open an OMERO session with the given credentials.
     *
     * @param username user's name
     * @param password user's password
     * @param server server name
     * @param port server port
     * @param group group ID; if null, user's default group is used
     */
    public void initialize(String username, String password, String server,
        int port, Long group)
        throws CannotCreateSessionException, PermissionDeniedException,
               ServerError
    {
        target.initialize(username, password, server, port, group, true);
        IConfigPrx iConfig = this.target.getServiceFactory().getConfigService();
        this.lsidFormat = String.format("urn:lsid:%s:%%s:%s_%%s:%%s",
                iConfig.getConfigValue("omero.db.authority"),
                iConfig.getDatabaseUuid());
    }

    /**
     * Open an OMERO session with the given credentials.
     *
     * @param server server name
     * @param port server port
     * @param sessionKey valid session key
     */
    public void initialize(String server, int port, String sessionKey)
            throws CannotCreateSessionException, PermissionDeniedException,
                   ServerError
    {
        initialize(sessionKey, sessionKey, server, port, null);
    }

    /**
     * Import ROIs from an OME-XML file and link to the current OMERO image.
     *
     * @param input OME-XML file
     * @return list of linked OMERO ROIs
     */
    public List<IObject> importRoisFromFile(File input)
            throws IOException, MissingLibraryException
    {
        LOG.info("ROI import started");
        String xml = new String(
            Files.readAllBytes(input.toPath()), StandardCharsets.UTF_8);
        LOG.debug("Importing OME-XML: {}", xml);

        OMEXMLMetadata xmlMeta;
        try {
            xmlMeta = omeXmlService.createOMEXMLMetadata(xml);
            LOG.info("Converting to OMERO metadata");
            MetadataConverter.convertMetadata(xmlMeta, target);
            LOG.info("ROI count: {}", xmlMeta.getROICount());
            LOG.debug("Containers: {}",
                      target.countCachedContainers(null, null));
            LOG.debug("References: {}",
                      target.countCachedReferences(null, null));
            target.postProcess();
            try {
                List<IObject> rois = target.saveToDB(imageId);
                return rois;
            }
            catch (Exception e) {
                LOG.error("Exception saving to DB", e);
            }
        }
        catch (ServiceException s) {
            LOG.error("Exception creating OME-XML metadata", s);
        }
        return null;
    }

    /**
     * Save ROIs linked to the current OMERO Image to the specified file.
     *
     * @param file output file
     * @return list of OMERO ROIs that were saved
     */
    public List<? extends IObject> exportRoisToFile(File file)
        throws Exception
    {
        LOG.info("ROI export started");
        final OMEXMLMetadata xmlMeta = omeXmlService.createOMEXMLMetadata();
        xmlMeta.createRoot();
        List<Roi> rois = getRois();
        List<Annotation> roiAnnotations = new ArrayList<Annotation>();
        for (final Roi roi : rois) {
            roiAnnotations.addAll(getAnnotations(roi.getId().getValue()));
        }
        LOG.debug("Annotations: {}", roiAnnotations);
        LOG.info("Converting to OME-XML metadata");
        omeXmlService.convertMetadata(
                new ROIMetadata(this::getLsid, rois), xmlMeta);
        omeXmlService.convertMetadata(
                new AnnotationMetadata(this::getLsid, roiAnnotations), xmlMeta);
        LOG.info("ROI count: {}", xmlMeta.getROICount());
        LOG.info("Writing OME-XML to: {}", file.getAbsolutePath());
        XMLWriter xmlWriter = new XMLWriter();
        xmlWriter.writeFile(file, (OME) xmlMeta.getRoot(), false);
        return rois;
    }

    /**
     * Find the LSID of the given OMERO model object.
     * Ported from
     * <code>org.openmicroscopy.client.downloader.XmlGenerator</code>
     * @param object an OMERO model object, hydrated with its update event
     * @return the LSID for that object
     */
    private String getLsid(IObject object) {
        Class<? extends IObject> objectClass = object.getClass();
        if (objectClass == IObject.class) {
            throw new IllegalArgumentException(
                    "must be of a specific model object type");
        }
        while (objectClass.getSuperclass() != IObject.class) {
            objectClass = objectClass.getSuperclass().asSubclass(IObject.class);
        }
        final long objectId = object.getId().getValue();
        final long updateId =
                object.getDetails().getUpdateEvent().getId().getValue();
        return String.format(
                lsidFormat, objectClass.getSimpleName(), objectId, updateId);
    }

    /**
     * Query the server for the given ROIs.
     * Ported from
     * <code>org.openmicroscopy.client.downloader.XmlGenerator</code>
     * @return the ROIs, hydrated sufficiently for conversion to XML
     * @throws ServerError if the ROIs could not be retrieved
     */
    private List<Roi> getRois() throws ServerError {
        final List<Roi> rois = new ArrayList<Roi>();
        for (final IObject result : target.getIQuery().findAllByQuery(
                "FROM Roi r " +
                "JOIN FETCH r.shapes AS s " +
                "WHERE r.image.id = :id",
                new ParametersI().addId(imageId),
                ALL_GROUPS_CONTEXT))
        {
            rois.add((Roi) result);
        }
        return rois;
    }

    private List<Annotation> getAnnotations(long roiId) throws ServerError {
        final List<Annotation> anns = new ArrayList<Annotation>();
        for (final IObject result : target.getIQuery().findAllByQuery(
                "SELECT DISTINCT a " +
                        "FROM RoiAnnotationLink as l " +
                        "JOIN l.child as a " +
                        "WHERE l.parent.id = :id",
                new ParametersI().addId(roiId),
                ALL_GROUPS_CONTEXT))
        {
            anns.add((Annotation) result);
        }
        return anns;
    }

    /**
     * Close the converter and log out of the current OMERO session.
     */
    public void close() {
        if (this.target != null) {
            this.target.logout();
        }
    }

}
