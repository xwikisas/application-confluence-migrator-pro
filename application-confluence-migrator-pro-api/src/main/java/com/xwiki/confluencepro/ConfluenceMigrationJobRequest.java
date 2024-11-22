/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.confluencepro;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.job.AbstractRequest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;

/**
 * The request used to configure the confluence migration job.
 *
 * @version $Id$
 * @since 1.0
 */
public class ConfluenceMigrationJobRequest extends AbstractRequest
{
    private static final long serialVersionUID = 1L;

    private final InputStream confluencePackage;

    private final DocumentReference statusDocumentReference;

    private final Map<String, Object> inputProperties;

    private final Map<String, Object> outputProperties;

    /**
     * @param confluencePackage the input stream of the confluence zip package.
     * @param statusDocumentReference see {@link #getStatusDocumentReference()}.
     * @param inputProperties see {@link #getInputProperties()}.
     * @param outputProperties see {@link #getOutputProperties()}.
     */
    public ConfluenceMigrationJobRequest(InputStream confluencePackage, DocumentReference statusDocumentReference,
        Map<String, Object> inputProperties, Map<String, Object> outputProperties)
    {
        this.statusDocumentReference = statusDocumentReference;
        this.confluencePackage = confluencePackage;
        List<String> jobId = getJobId(statusDocumentReference);
        setId(jobId);
        if (inputProperties == null) {
            this.inputProperties = new HashMap<>();
        } else {
            this.inputProperties = inputProperties;
        }
        if (outputProperties == null) {
            this.outputProperties = new HashMap<>();
        } else {
            this.outputProperties = outputProperties;
        }
    }

    /**
     * @return the job id of a migration document
     * @param statusDocumentReference the migration document for which to get the job id
     */
    public static List<String> getJobId(DocumentReference statusDocumentReference)
    {
        List<String> jobId = new ArrayList<>();
        jobId.add("confluence");
        jobId.add("migration");
        for (EntityReference er : statusDocumentReference.getReversedReferenceChain()) {
            jobId.add(er.getName());
        }
        return jobId;
    }

    /**
     * @return the InputStream of the confluence package that will be used for the migration.
     */
    public InputStream getConfluencePackage()
    {
        return confluencePackage;
    }

    /**
     * @return the reference to the document where this job was started.
     */
    public DocumentReference getStatusDocumentReference()
    {
        return statusDocumentReference;
    }

    /**
     * @return the input properties for the confluence filter stream.
     */
    public Map<String, Object> getInputProperties()
    {
        return inputProperties;
    }

    /**
     * @return the output properties for the instance filter stream.
     */
    public Map<String, Object> getOutputProperties()
    {
        return outputProperties;
    }
}
