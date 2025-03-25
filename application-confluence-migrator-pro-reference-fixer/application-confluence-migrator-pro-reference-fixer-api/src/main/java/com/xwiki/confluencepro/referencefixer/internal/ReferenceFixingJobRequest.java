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
package com.xwiki.confluencepro.referencefixer.internal;

import com.xwiki.confluencepro.referencefixer.BrokenRefType;
import org.xwiki.job.AbstractRequest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference Fixing Job Request.
 * @since 1.29.0
 * @version $Id$
 */
public class ReferenceFixingJobRequest extends AbstractRequest
{
    private static final long serialVersionUID = 1L;

    private final List<EntityReference> migrationReferences;
    private final List<EntityReference> spaceReferences;
    private final String[] baseURLs;
    private final BrokenRefType brokenRefType;
    private final boolean updateInPlace;
    private final boolean dryRun;
    private final DocumentReference statusDocumentReference;

    /**
     * @param statusDocumentReference the document "owning" the reference fixing job
     * @param migrationReferences fix the documents migrated during these migrations
     * @param spaceReferences fix the documents in these spaces
     * @param baseURLs the base URLs to fix absolute links to the old Confluence instance
     * @param brokenRefType the type of broken references to fix
     * @param updateInPlace whether to update the document in place instead of creating a new revision
     * @param dryRun whether to simulate the fixing instead of actually updating the documents
     */
    public ReferenceFixingJobRequest(DocumentReference statusDocumentReference,
        List<EntityReference> migrationReferences, List<EntityReference> spaceReferences, String[] baseURLs,
        BrokenRefType brokenRefType, boolean updateInPlace, boolean dryRun)
    {
        this.migrationReferences = migrationReferences;
        this.spaceReferences = spaceReferences;
        this.baseURLs = baseURLs;
        this.brokenRefType = brokenRefType;
        this.updateInPlace = updateInPlace;
        this.dryRun = dryRun;
        this.statusDocumentReference = statusDocumentReference;
        List<String> jobId = getJobId(statusDocumentReference);
        setId(jobId);
    }

    /**
     * @return the job id of a reference fixing status document
     * @param statusDocumentReference the migration document for which to get the job id
     */
    public static List<String> getJobId(EntityReference statusDocumentReference)
    {
        List<String> jobId = new ArrayList<>();
        jobId.add("confluence");
        jobId.add("referencefixing");
        for (EntityReference er : statusDocumentReference.getReversedReferenceChain()) {
            jobId.add(er.getName());
        }
        return jobId;
    }

    BrokenRefType getBrokenRefType()
    {
        return brokenRefType;
    }

    List<EntityReference> getMigrationReferences()
    {
        return migrationReferences;
    }

    List<EntityReference> getSpaceReferences()
    {
        return spaceReferences;
    }

    String[] getBaseURLs()
    {
        return baseURLs;
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
