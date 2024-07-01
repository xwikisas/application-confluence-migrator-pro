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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.xwiki.contrib.confluence.filter.input.ConfluenceXMLPackage;
import org.xwiki.job.event.status.CancelableJobStatus;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.event.CancelableEvent;
import org.xwiki.refactoring.job.question.EntitySelection;

import com.xwiki.confluencepro.ConfluenceMigrationJobStatus;

/**
 * Handles the creation and sending the question to the job status.
 *
 * @version $Id$
 * @since 1.0
 */
public class ConfluenceQuestionManager
{
    /**
     * @param event the event to cancel if the question is canceled
     * @param confluencePackage the confluence package from where to extract the spaces
     * @param jobStatusToAsk the job status that will be asked the created question
     * @return the created and answered space question
     */
    public SpaceQuestion createAndAskQuestion(CancelableEvent event, ConfluenceXMLPackage confluencePackage,
        JobStatus jobStatusToAsk)
    {
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

            try {
                additionalInfo.put(ConfluenceXMLPackage.KEY_SPACE_NAME,
                    confluencePackage.getSpaceProperties(space.getValue())
                        .getString(ConfluenceXMLPackage.KEY_SPACE_NAME, ""));
            } catch (ConfigurationException ignored) {
                // Shouldn't happen.
            }
            confluenceSpaces.put(new EntitySelection(spaceRef), additionalInfo);
        }
        return createAndAskQuestion(event, confluenceSpaces, jobStatusToAsk);
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
}


