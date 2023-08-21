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
package com.xwiki.confluencepro.internal.listener;

import java.util.Collections;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.job.event.JobFinishedEvent;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xwiki.confluencepro.internal.ConfluenceMigrationJob;

/**
 * Listener that will do things.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("com.xwiki.confluencepro.internal.ConfluenceJobDoneListener")
@Singleton
public class ConfluenceJobDoneListener extends AbstractEventListener
{
//    private static final LocalDocumentReference MIGRATION_OBJECT = new LocalDocumentReference(Arrays.asList(
//        "ConfluenceMigratorPro", "Code"), "MigrationClass");
//
//    @Inject
//    private Provider<XWikiContext> contextProvider;
//
//    @Inject
//    private JobExecutor jobExecutor;

    /**
     * Default constructor.
     */
    public ConfluenceJobDoneListener()
    {
        super(ConfluenceJobDoneListener.class.toString(),
            Collections.singletonList(new JobFinishedEvent(ConfluenceMigrationJob.JOBTYPE)));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
//        XWikiContext context = contextProvider.get();
//        ConfluenceMigrationJob job = (ConfluenceMigrationJob) source;
//        try {
//            XWikiDocument document =
//                context.getWiki().getDocument(job.getStatus().getRequest().getDocumentReference(), context).clone();
//            // Set executed to true.
//            BaseObject object = document.getXObject(MIGRATION_OBJECT);
//            object.set("executed", 1, context);
//            // Set imported spaces.
//            jobExecutor.getCurrentJob(FilterStreamConverterJob.ROOT_GROUP);
//            jobExecutor.getJob(Arrays.asList("filter", "converter",  "confluence+xml", "xwiki+instance"));
//            List<EntityReference> spaces = new ArrayList<>();
//            for (EntitySelection entitySelection : ((SpaceQuestion) job.getStatus().getQuestion())
//            .getConfluenceSpaces()
//                .keySet()) {
//                spaces.add(entitySelection.getEntityReference());
//            }
//            object.set("spaces", spaces, context);
//
//            // Set logs json.
//            Gson gson = new Gson();
//            List<Map<String, Object>> logList = new ArrayList<>();
//            for (LogEvent logEvent : job.getStatus().getLogTail().getLogEvents(0, -1)) {
//                Map<String, Object> logMap = new HashMap<>();
//                logMap.put("level", logEvent.getLevel().toString());
//                logMap.put("timeStamp", logEvent.getTimeStamp());
//                logMap.put("message", logEvent.getFormattedMessage());
//                if (logEvent.getThrowable() != null) {
//                    logMap.put("throwable", ExceptionUtils.getStackFrames(logEvent.getThrowable()));
//                }
//                logList.add(logMap);
//            }
//            object.set("logs", gson.toJson(logList), context);
//            context.getWiki().saveDocument(document, "Migration executed!", context);
//        } catch (XWikiException e) {
//            throw new RuntimeException(e);
//        }
    }
}
