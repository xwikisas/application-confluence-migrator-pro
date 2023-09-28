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

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.event.ConfluenceFilteringEvent;
import org.xwiki.contrib.confluence.filter.input.ConfluenceXMLPackage;
import org.xwiki.job.AbstractJobStatus;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.CancelableEvent;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.job.question.EntitySelection;

import com.xwiki.confluencepro.ConfluenceMigrationJobRequest;
import com.xwiki.licensing.LicenseType;
import com.xwiki.licensing.Licensor;

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

    @Inject
    private Provider<Licensor> licensorProvider;

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

        // If there is no license, the user can still trial the application by importing one space and 30 of its pages.
        if (jobStatusToAsk != null && jobStatusToAsk.getRequest() instanceof ConfluenceMigrationJobRequest) {
            String wiki = ((ConfluenceMigrationJobRequest) jobStatusToAsk.getRequest()).getStatusDocumentReference()
                .getWikiReference().getName();
            Licensor licensor = licensorProvider.get();
            if (licensor == null) {
                return;
            }
            DocumentReference mainRef =
                new DocumentReference(wiki, Arrays.asList("ConfluenceMigratorPro", "Code"), "MigrationClass");
            if (!licensor.hasLicensure(mainRef) || licensor.getLicense(mainRef).getType().equals(LicenseType.TRIAL)) {
                reduceNumberOfPagesToProcess(confluencePackage);
            }
        }
    }

    private void reduceNumberOfPagesToProcess(ConfluenceXMLPackage confluencePackage)
    {
        // We expect only one space, but, if somehow the user selected more, process only the first one.
        boolean moreThanOne = false;
        Iterator<Map.Entry<Long, List<Long>>> iterator = confluencePackage.getPages().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, List<Long>> entry = iterator.next();
            if (!moreThanOne) {
                confluencePackage.getPages()
                    .put(entry.getKey(), entry.getValue().subList(0, Integer.min(30, entry.getValue().size())));
                moreThanOne = true;
            } else {
                iterator.remove();
            }
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
