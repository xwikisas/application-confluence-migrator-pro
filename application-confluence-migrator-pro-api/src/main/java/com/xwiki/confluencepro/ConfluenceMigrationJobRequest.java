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
import java.util.List;

import org.xwiki.job.AbstractRequest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

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

    private final DocumentReference documentReference;

    /**
     * @param confluencePackage the input stream of the confluence zip package.
     * @param documentReference see {@link #getDocumentReference()}.
     */
    public ConfluenceMigrationJobRequest(InputStream confluencePackage, DocumentReference documentReference)
    {
        this.documentReference = documentReference;
        this.confluencePackage = confluencePackage;
        List<String> jobId = new ArrayList<>();
        jobId.add("confluence");
        jobId.add("migrator");
        for (SpaceReference spaceReference : documentReference.getSpaceReferences()) {
            jobId.add(spaceReference.getName());
        }
        jobId.add(documentReference.getName());
        setId(jobId);
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
    public DocumentReference getDocumentReference()
    {
        return documentReference;
    }
}
