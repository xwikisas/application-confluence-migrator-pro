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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.xwiki.confluencepro.ConfluenceMigrationJobStatus;
import com.xwiki.pro.internal.resolvers.LinkMappingStore;
import org.hibernate.Session;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.event.ConfluenceFilteringEvent;
import org.xwiki.contrib.confluence.filter.input.ConfluenceXMLPackage;
import org.xwiki.contrib.confluence.filter.input.LinkMapper;
import org.xwiki.contrib.confluence.filter.internal.input.ConfluenceLinkMappingReceiver;
import org.xwiki.job.AbstractJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.Request;
import org.xwiki.job.event.status.JobStatus;
import org.slf4j.Logger;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
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
    static final Marker COLLISION_MARKER = MarkerFactory.getMarker("collidingReferences");

    static final String ONLY_LINK_MAPPING = "onlyLinkMapping";

    @Inject
    private JobContext jobContext;

    @Inject
    private LinkMapper linkMapper;

    @Inject
    private Logger logger;

    @Inject
    private LinkMappingStore linkMappingStore;

    @Inject
    private EntityReferenceSerializer<String> serializer;

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
        ConfluenceMigrationJobStatus status = getConfluenceMigrationJobStatus(job);

        if (status == null) {
            logger.debug("Could not get the job status. Maybe the migration was not run from Confluence Migrator Pro?");
            return;
        }

        ConfluenceXMLPackage confluencePackage = (ConfluenceXMLPackage) data;

        Collection<String> spaces = new HashSet<>(confluencePackage.getSpacesByKey().keySet());
        status.setSpaces(spaces);

        if (shouldAskQuestions(status, job, spaces)) {
            askSpacesQuestion((ConfluenceFilteringEvent) event, confluencePackage, status, spaces);
        }

        if (status.isCanceled()) {
            ((CancelableEvent) event).cancel();
        } else if (isPropertyEnabled(status, ONLY_LINK_MAPPING)) {
            // This is a link mapping only phase, let's store the link mapping and cancel the import
            updateLinkMapping();
            ((CancelableEvent) event).cancel();
        } else if (isInputPropertyEnabled(status, "storeConfluenceDetailsEnabled")) {
            // This is the happy path / normal situation.
            // The data on the imported spaces should be in the wiki. Except if someone has imported a partial space and
            // also disabled storeConfluenceDetailsEnabled on the previous import, which is not supported nor likely.
            // We clean up the link mapping which may have been imported in a link-mapping only phase, we don't want
            // this data hanging around for nothing.
            linkMappingStore.removeSpaces(status.getSpaces());
        } else if (isPropertyEnabled(status, "saveLinkMapping")) {
            // We are asked to save the link mapping and storeConfluenceDetailsEnabled is disabled, let's store the
            // link mapping
            updateLinkMapping();
        }
    }

    private static boolean shouldAskQuestions(ConfluenceMigrationJobStatus status, Job job, Collection<String> spaces)
    {
        if (spaces.size() < 2) {
            return false;
        }

        if (isPropertyEnabled(status, "skipQuestions") || isPropertyEnabled(status, ONLY_LINK_MAPPING))
        {
            return false;
        }

        Request request = job.getRequest();
        if (request == null) {
            return true;
        }
        return request.isInteractive();
    }

    private static boolean isPropertyEnabled(ConfluenceMigrationJobStatus jobStatusToAsk, String propertyName)
    {
        String v = (String) jobStatusToAsk.getRequest().getOutputProperties().get(propertyName);
        return isTrue(v);
    }

    private static boolean isInputPropertyEnabled(ConfluenceMigrationJobStatus jobStatusToAsk, String propertyName)
    {
        String v = (String) jobStatusToAsk.getRequest().getInputProperties().get(propertyName);
        return isTrue(v);
    }

    static boolean isTrue(String v)
    {
        return "true".equals(v) || "1".equals(v);
    }

    private static void askSpacesQuestion(ConfluenceFilteringEvent event, ConfluenceXMLPackage confluencePackage,
        ConfluenceMigrationJobStatus jobStatusToAsk, Collection<String> spaces)
    {
        SpaceQuestion question =
            new ConfluenceQuestionManager().createAndAskQuestion(event, confluencePackage, jobStatusToAsk);

        for (EntitySelection entitySelection : question.getConfluenceSpaces().keySet()) {
            if (!entitySelection.isSelected()) {
                String spaceKey = entitySelection.getEntityReference().getName();
                Long spaceId = confluencePackage.getSpacesByKey().get(spaceKey);
                event.disableSpace(spaceId);
                spaces.remove(spaceKey);
            }
        }
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

    private void updateLinkMapping()
    {
        logger.info("Computing the link mapping…");
        AtomicReference<String> currentSpace = new AtomicReference<>();

        Map<String, List<String>> reverseMapping = new HashMap<>();
        Set<String> collidingReferences = new HashSet<>();
        Map<String, String> spaceByRef = new HashMap<>();

        Session session = linkMappingStore.beginTransaction();
        if (session != null) {
            ConfluenceLinkMappingReceiver mapper = new MyConfluenceLinkMappingReceiver(currentSpace, session,
                reverseMapping, collidingReferences, spaceByRef);
            linkMapper.getLinkMapping(mapper);
            for (String collidingReference : collidingReferences) {
                String spaceKey = spaceByRef.get(collidingReference);
                List<String> pageTitles = reverseMapping.get(collidingReference);
                logger.error(COLLISION_MARKER, "Reference [{}] collides in space [{}] for pages [{}]",
                    collidingReference, spaceKey, pageTitles);
            }
            linkMappingStore.endTransaction(true);
        }
    }

    private final class MyConfluenceLinkMappingReceiver implements ConfluenceLinkMappingReceiver
    {
        private final AtomicReference<String> currentSpace;
        private final Session session;
        private final Map<String, List<String>> reverseMapping;
        private final Set<String> collidingReferences;
        private final Map<String, String> spaceByRef;

        private MyConfluenceLinkMappingReceiver(AtomicReference<String> currentSpace, Session session,
            Map<String, List<String>> reverseMapping, Set<String> collidingReferences,
            Map<String, String> spaceByRef)
        {
            this.currentSpace = currentSpace;
            this.session = session;
            this.reverseMapping = reverseMapping;
            this.collidingReferences = collidingReferences;
            this.spaceByRef = spaceByRef;
        }

        @Override
        public void addPage(String spaceKey, long pageId, EntityReference reference)
        {
            if (!spaceKey.equals(currentSpace.get())) {
                currentSpace.set(spaceKey);
            }
            String serialized = serializer.serialize(reference);
            linkMappingStore.add(session, pageId, serialized);
        }

        @Override
        public void addPage(String spaceKey, String pageTitle, EntityReference reference)
        {
            if (!spaceKey.equals(currentSpace.get())) {
                currentSpace.set(spaceKey);
            }
            String serialized = serializer.serialize(reference);
            linkMappingStore.add(session, spaceKey, pageTitle, serialized);

            // for collision checking
            List<String> list = reverseMapping.get(serialized);
            if (list == null) {
                list = new ArrayList<>(1);
                reverseMapping.put(serialized, list);
            } else {
                collidingReferences.add(serialized);
                spaceByRef.put(serialized, spaceKey);
            }
            list.add(pageTitle);
        }
    }
}
