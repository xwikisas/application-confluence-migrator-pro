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
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.job.question.EntitySelection;

/**
 * Listener that will ask different questions when the Confluence migration job runs.
 *
 * @version $Id$
 * @since 3.0
 */
@Component
@Named("com.xwiki.confluencemigrator.internal.DocumentsGeneratingListener")
@Singleton
public class ConfluencePackageReadListener extends AbstractEventListener
{
    @Inject
    private JobContext jobContext;

    /**
     * Default constructor.
     */
    public ConfluencePackageReadListener()
    {
        super(ConfluencePackageReadListener.class.getName(),
            Collections.singletonList(new ConfluenceFilteringEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        ConfluenceInputFilterStream filterStream = (ConfluenceInputFilterStream) source;
        ConfluenceXMLPackage confluencePackage = (ConfluenceXMLPackage) data;

        Job job = this.jobContext.getCurrentJob();
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
        SpaceQuestion question = new SpaceQuestion(confluenceSpaces);
        question.unselectAll();
        if (
            job != null
                && job.getStatus() != null
                && job.getStatus().getRequest() != null
                && job.getStatus().getRequest().isInteractive()
        )
        {
            try {
                boolean ack = job.getStatus().ask(question, 1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                // What now?
            }
        } else {
            question.selectAll();
        }

        for (EntitySelection entitySelection : question.getConfluenceSpaces().keySet()) {
            if (!entitySelection.isSelected()) {
                confluencePackage.getPages()
                    .remove(confluencePackage.getSpacesByKey().get(entitySelection.getEntityReference().getName()));
            }
        }
    }
}
