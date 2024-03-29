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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.confluencepro.ConfluenceMigrationJobStatus;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.event.ConfluenceFilteringEvent;
import org.xwiki.contrib.confluence.filter.input.ConfluenceXMLPackage;
import org.xwiki.contrib.confluence.filter.input.LinkMapper;
import org.xwiki.job.AbstractJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.event.status.JobStatus;
import org.slf4j.Logger;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.CancelableEvent;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.job.question.EntitySelection;

/**
 * Listener that will ask different questions when the Confluence migration job runs.
 *
 * @version $Id$
 * @since 3.0
 */
@Component
@Named("com.xwiki.confluencepro.internal.ConfluenceFilteringListener")
@Singleton
public class ConfluenceFilteringListener extends AbstractEventListener
{
    private static final String MAPPING_OBJECT_KEY = "mapping";

    private static final String MAPPING_SPACEKEY_KEY = "spaceKey";

    private static final List<String> CODE_REF = List.of("ConfluenceMigratorPro", "Code");

    private static final List<String> LINK_MAPPING_STATE_REF =
        Stream.concat(CODE_REF.stream(), Stream.of("LinkMappingState")).collect(Collectors.toList());

    private static final LocalDocumentReference LINK_MAPPING_SPACE_STATE_CLASS_REF = new LocalDocumentReference(
        CODE_REF, "LinkMappingStateSpaceClass");

    @Inject
    private JobContext jobContext;

    @Inject
    private LinkMapper linkMapper;

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private LinkMappingConverter linkMappingConverter;

    /**
     * Default constructor.
     */
    public ConfluenceFilteringListener()
    {
        super(ConfluenceFilteringListener.class.getName(),
            Collections.singletonList(new ConfluenceFilteringEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        Job job = this.jobContext.getCurrentJob();
        ConfluenceMigrationJobStatus jobStatusToAsk = getConfluenceMigrationJobStatus(job);

        if (jobStatusToAsk == null) {
            logger.debug("Could not get the job status. Maybe the migration was not run from Confluence Migrator Pro?");
            return;
        }

        boolean anySelected = !isPropertyEnabled(jobStatusToAsk, "onlyLinkMapping")
            && handleSpaceSelection((ConfluenceFilteringEvent) event, (ConfluenceXMLPackage) data, jobStatusToAsk);

        if (!jobStatusToAsk.isCanceled() && isPropertyEnabled(jobStatusToAsk, "saveLinkMapping")) {
            updateLinkMapping(jobStatusToAsk);
        }

        if (!anySelected || jobStatusToAsk.isCanceled()) {
            ((CancelableEvent) event).cancel();
        }
    }

    private static boolean isPropertyEnabled(ConfluenceMigrationJobStatus jobStatusToAsk, String propertyName)
    {
        String useLinkMapping = (String) jobStatusToAsk.getRequest().getOutputProperties().get(propertyName);
        return "true".equals(useLinkMapping) || "1".equals(useLinkMapping);
    }

    private static boolean handleSpaceSelection(ConfluenceFilteringEvent event, ConfluenceXMLPackage confluencePackage,
        ConfluenceMigrationJobStatus jobStatusToAsk)
    {
        SpaceQuestion question =
            new ConfluenceQuestionManager().createAndAskQuestion(event, confluencePackage, jobStatusToAsk);

        boolean anySelected = false;
        for (EntitySelection entitySelection : question.getConfluenceSpaces().keySet()) {
            if (entitySelection.isSelected()) {
                anySelected = true;
            } else {
                Long spaceId = confluencePackage.getSpacesByKey().get(entitySelection.getEntityReference().getName());
                event.disableSpace(spaceId);
            }
        }
        return anySelected;
    }

    private ConfluenceMigrationJobStatus getConfluenceMigrationJobStatus(Job job)
    {
        JobStatus jobStatus = job.getStatus();
        while (jobStatus != null) {
            if (jobStatus instanceof ConfluenceMigrationJobStatus) {
                return (ConfluenceMigrationJobStatus) jobStatus;
            }
            jobStatus = ((AbstractJobStatus<?>) jobStatus).getParentJobStatus();
        }
        return null;
    }

    private void updateLinkMapping(ConfluenceMigrationJobStatus jobStatusToAsk)
    {
        logger.info("Computing the link mapping…");
        Map<String, Map<String, EntityReference>> linkMapping = linkMapper.getLinkMapping();
        XWikiContext context = contextProvider.get();
        XWiki wiki = context.getWiki();
        SpaceReference stateRef = new SpaceReference(wiki.getDatabase(), LINK_MAPPING_STATE_REF);
        for (Map.Entry<String, Map<String, EntityReference>> mappingEntry : linkMapping.entrySet()) {
            String key = mappingEntry.getKey();
            boolean pageIds = key.endsWith(":ids");
            String spaceKey = pageIds ? key.substring(0, key.indexOf(":")) : key;
            logger.info("Updating the link mapping for space [{}]" + (pageIds ? " (page IDs)" : ""), spaceKey);
            try {
                XWikiDocument spaceStateDoc = wiki.getDocument(
                    new DocumentReference(key, stateRef), context);
                Map<String, EntityReference> newSpaceMapping = mappingEntry.getValue();
                BaseObject mappingObj = spaceStateDoc.isNew()
                    ? spaceStateDoc.newXObject(LINK_MAPPING_SPACE_STATE_CLASS_REF, context)
                    : spaceStateDoc.getXObject(LINK_MAPPING_SPACE_STATE_CLASS_REF, true, context);
                String updatedSpaceMappingStr = mappingObj.getLargeStringValue(MAPPING_OBJECT_KEY);
                Map<String, EntityReference> updatedSpaceMapping =
                    linkMappingConverter.convertSpaceLinkMapping(updatedSpaceMappingStr, key);
                boolean updated = false;

                if (updatedSpaceMapping == null) {
                    updated = true;
                    updatedSpaceMapping = newSpaceMapping;
                } else {
                    for (Map.Entry<String, EntityReference> newEntry : newSpaceMapping.entrySet()) {
                        String docTitle = newEntry.getKey();
                        EntityReference docRef = newEntry.getValue();
                        if (!Objects.equals(docRef, updatedSpaceMapping.get(docTitle))) {
                            // FIXME we are assuming that the documents are created in the current wiki.
                            updatedSpaceMapping.put(docTitle, docRef);
                            updated = true;
                        }
                    }
                }

                if (updated) {
                    mappingObj.setLargeStringValue(MAPPING_OBJECT_KEY,
                        linkMappingConverter.convertSpaceLinkMapping(updatedSpaceMapping, context.getWikiReference()));
                    mappingObj.setStringValue(MAPPING_SPACEKEY_KEY, key);
                    if (spaceStateDoc.isNew()) {
                        spaceStateDoc.setHidden(true);
                    }
                    String migrationName = jobStatusToAsk.getRequest().getStatusDocumentReference().getName();
                    wiki.saveDocument(spaceStateDoc, "Updated from migration " + migrationName, context);
                }
            } catch (XWikiException | JsonProcessingException e) {
                logger.warn("Could not update link mapping for space [{}]", key, e);
            }
        }
        logger.info("Done computing the link mapping.");
    }
}
