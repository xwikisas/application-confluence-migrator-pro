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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.event.ConfluenceFilteringEvent;
import org.xwiki.contrib.confluence.filter.input.ConfluenceXMLPackage;
import org.xwiki.job.AbstractJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.event.status.JobStatus;
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
        ConfluenceXMLPackage confluencePackage = (ConfluenceXMLPackage) data;

        Job job = this.jobContext.getCurrentJob();
        JobStatus jobStatusToAsk = getRootJobStatus(job);
        SpaceQuestion question =
            new ConfluenceQuestionManager().createAndAskQuestion((CancelableEvent) event, confluencePackage,
                jobStatusToAsk);

        boolean anySelected = false;
        for (EntitySelection entitySelection : question.getConfluenceSpaces().keySet()) {
            if (entitySelection.isSelected()) {
                anySelected = true;
            } else {
                Long spaceId = confluencePackage.getSpacesByKey().get(entitySelection.getEntityReference().getName());
                ((ConfluenceFilteringEvent) event).disableSpace(spaceId);
            }
        }
        if (!anySelected) {
            ((CancelableEvent) event).cancel();
        }
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
