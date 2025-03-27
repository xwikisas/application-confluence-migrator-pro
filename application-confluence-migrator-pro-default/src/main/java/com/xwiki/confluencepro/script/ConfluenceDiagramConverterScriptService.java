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
package com.xwiki.confluencepro.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.job.Job;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.api.Document;
import com.xwiki.confluencepro.internal.DiagramConversionJobRequest;

/**
 * Confluence Migrator Pro Diagram Converter.
 * @since 1.33.0
 * @version $Id$
 */
@Component
@Named("confluencepro.diagramconversion")
@Singleton
public class ConfluenceDiagramConverterScriptService implements ScriptService
{
    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private Logger logger;

    @Inject
    private EntityReferenceResolver<String> resolver;

    /**
     * Fix links in the migrated documents specified by the parameters.
     * @param statusDocument the confluence diagram conversion status document
     * @return the Job in which diagrams are converted
     */
    public Job createAndRunDiagramConversionJob(Document statusDocument)
    {
        if (!authorization.hasAccess(Right.ADMIN)) {
            return null;
        }

        List<EntityReference> migrationReferences = getMigrations(statusDocument);
        List<EntityReference> spaceReferences = getSpaces(statusDocument);
        boolean updateInPlace = ((Integer) statusDocument.getValue("updateInPlace")) == 1;
        boolean dryRun = ((Integer) statusDocument.getValue("dryRun")) == 1;

        DiagramConversionJobRequest jobRequest = new DiagramConversionJobRequest(statusDocument.getDocumentReference(),
            migrationReferences, spaceReferences, updateInPlace, dryRun);

        try {
            return jobExecutor.execute("confluence.diagramconversion", jobRequest);
        } catch (JobException e) {
            logger.error("Failed to execute the migration job for [{}].", statusDocument, e);
        }
        return null;
    }

    private List<EntityReference> getSpaces(Document statusDocument)
    {
        List<EntityReference> spaceReferences = Collections.emptyList();
        List<String> spaces = (List<String>) statusDocument.getValue("spaces");
        if (CollectionUtils.isNotEmpty(spaces)) {
            spaceReferences = new ArrayList<>(spaces.size());
            for (String space : spaces) {
                if (StringUtils.isNotEmpty(space)) {
                    EntityType spaceType = space.endsWith(".WebHome") ? EntityType.DOCUMENT : EntityType.SPACE;
                    EntityReference spaceReference = resolver.resolve(space, spaceType);
                    if (spaceReference.getType() == EntityType.DOCUMENT) {
                        spaceReference = spaceReference.getParent();
                    }
                    spaceReferences.add(spaceReference);
                }
            }
        }
        return spaceReferences;
    }

    private List<EntityReference> getMigrations(Document statusDocument)
    {
        List<EntityReference> migrationReferences = Collections.emptyList();
        List<String> migrations = (List<String>) statusDocument.getValue("migrations");
        if (CollectionUtils.isNotEmpty(migrations)) {
            for (String migration : migrations) {
                migrationReferences = new ArrayList<>(migrations.size());
                if (StringUtils.isNotEmpty(migration)) {
                    migrationReferences.add(resolver.resolve(migration, EntityType.DOCUMENT));
                }
            }
        }
        return migrationReferences;
    }

    /**
     * @param statusDocument the diagram conversion status document
     * @return the job id corresponding to the diagram conversion status document
     */
    public List<String> getDiagramConversionJobId(EntityReference statusDocument)
    {
        return DiagramConversionJobRequest.getJobId(statusDocument);
    }
}


