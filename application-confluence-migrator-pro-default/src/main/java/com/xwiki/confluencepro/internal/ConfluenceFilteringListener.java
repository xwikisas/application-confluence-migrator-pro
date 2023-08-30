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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.event.ConfluenceFilteringEvent;
import org.xwiki.contrib.confluence.filter.input.ConfluenceXMLPackage;
import org.xwiki.contrib.confluence.filter.internal.input.ConfluenceInputFilterStream;
import org.xwiki.job.AbstractJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.event.status.CancelableJobStatus;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.CancelableEvent;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.job.question.EntitySelection;

import com.xwiki.confluencepro.ConfluenceMigrationJobStatus;

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
    @Inject
    private JobContext jobContext;

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
        ConfluenceInputFilterStream filterStream = (ConfluenceInputFilterStream) source;
        ConfluenceXMLPackage confluencePackage = (ConfluenceXMLPackage) data;

        Job job = this.jobContext.getCurrentJob();
        JobStatus jobStatusToAsk = getRootJobStatus(job);
        Map<EntitySelection, Map<String, String>> confluenceSpaces = new HashMap<>();
        for (Map.Entry<String, Long> space : confluencePackage.getSpacesByKey().entrySet()) {
            EntityReference spaceRef = new EntityReference(space.getKey(), EntityType.DOCUMENT);
            Map<String, String> additionalInfo = new HashMap<>();
            additionalInfo.put("documentsCount",
                Integer.toString(confluencePackage.getPages().get(space.getValue()).size()));

            int attachmentsNumber = confluencePackage.getPages().get(space.getValue())
                .stream()
                .map(p -> confluencePackage.getAttachments(p).size())
                .reduce(Integer::sum).orElse(0);

            additionalInfo.put("attachmentsCount", Integer.toString(attachmentsNumber));
            confluenceSpaces.put(new EntitySelection(spaceRef), additionalInfo);
        }
        SpaceQuestion question =
            createAndAskQuestion((CancelableEvent) event, confluenceSpaces, jobStatusToAsk);

        boolean anySelected = false;
        for (EntitySelection entitySelection : question.getConfluenceSpaces().keySet()) {
            if (!entitySelection.isSelected()) {
                confluencePackage.getPages()
                    .remove(confluencePackage.getSpacesByKey().get(entitySelection.getEntityReference().getName()));
            } else {
                anySelected = true;
            }
        }
        if (!anySelected) {
            ((CancelableEvent) event).cancel();
        }
    }

    private SpaceQuestion createAndAskQuestion(CancelableEvent event,
        Map<EntitySelection, Map<String, String>> confluenceSpaces, JobStatus jobStatusToAsk)
    {
        SpaceQuestion question = new SpaceQuestion(confluenceSpaces);
        if (jobStatusToAsk instanceof ConfluenceMigrationJobStatus) {
            ((ConfluenceMigrationJobStatus) jobStatusToAsk).addAskedQuestion(jobStatusToAsk.getRequest().getId(),
                question);
        }
        question.unselectAll();
        if (
            jobStatusToAsk != null
                && jobStatusToAsk.getRequest() != null
                && jobStatusToAsk.getRequest().isInteractive()
        )
        {
            try {
                boolean ack = jobStatusToAsk.ask(question, 1, TimeUnit.DAYS);
                if (((CancelableJobStatus) jobStatusToAsk).isCanceled()) {
                    event.cancel();
                }
            } catch (InterruptedException e) {
                // What now?
            }
        } else {
            question.selectAll();
        }
        return question;
    }

    private JobStatus getRootJobStatus(Job job)
    {
        JobStatus jobStatus = job.getStatus();
        if (jobStatus == null) {
            return null;
        }
        JobStatus jobParentStatus = ((AbstractJobStatus<?>) jobStatus).getParentJobStatus();
        while (true) {
            if (jobParentStatus == null) {
                return jobStatus;
            }
            jobStatus = jobParentStatus;
            jobParentStatus = ((AbstractJobStatus<?>) jobParentStatus).getParentJobStatus();
        }
    }
}
