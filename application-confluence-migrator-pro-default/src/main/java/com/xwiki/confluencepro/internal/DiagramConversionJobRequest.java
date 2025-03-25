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
package com.xwiki.confluencepro.internal;

import java.util.ArrayList;
import java.util.List;

import org.xwiki.job.AbstractRequest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;

/**
 * Diagram Conversion Job Request.
 * @since 1.33.0
 * @version $Id$
 */
public class DiagramConversionJobRequest extends AbstractRequest
{
    private static final long serialVersionUID = 1L;

    private final List<EntityReference> migrationReferences;
    private final List<EntityReference> spaceReferences;
    private final boolean updateInPlace;
    private final boolean dryRun;
    private final DocumentReference statusDocumentReference;

    /**
     * @param statusDocumentReference the document "owning" the diagram conversion job
     * @param migrationReferences fix the documents migrated during these migrations
     * @param spaceReferences fix the documents in these spaces
     * @param updateInPlace whether to update the document in place instead of creating a new revision
     * @param dryRun whether to simulate the fixing instead of actually updating the documents
     */
    public DiagramConversionJobRequest(DocumentReference statusDocumentReference,
        List<EntityReference> migrationReferences, List<EntityReference> spaceReferences, boolean updateInPlace,
        boolean dryRun)
    {
        this.migrationReferences = migrationReferences;
        this.spaceReferences = spaceReferences;
        this.updateInPlace = updateInPlace;
        this.dryRun = dryRun;
        this.statusDocumentReference = statusDocumentReference;
        List<String> jobId = getJobId(statusDocumentReference);
        setId(jobId);
    }

    /**
     * @return the job id of a diagram conversion status document
     * @param statusDocumentReference the migration document for which to get the job id
     */
    public static List<String> getJobId(EntityReference statusDocumentReference)
    {
        List<String> jobId = new ArrayList<>();
        jobId.add("confluence");
        jobId.add("diagramconversion");
        for (EntityReference er : statusDocumentReference.getReversedReferenceChain()) {
            jobId.add(er.getName());
        }
        return jobId;
    }

    List<EntityReference> getMigrationReferences()
    {
        return migrationReferences;
    }

    List<EntityReference> getSpaceReferences()
    {
        return spaceReferences;
    }

    boolean isUpdateInPlace()
    {
        return updateInPlace;
    }

    boolean isDryRun()
    {
        return dryRun;
    }

    DocumentReference getStatusDocumentReference()
    {
        return statusDocumentReference;
    }
}
