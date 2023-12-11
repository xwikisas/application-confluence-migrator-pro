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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.PageIdentifier;
import org.xwiki.logging.LogLevel;
import org.xwiki.logging.event.LogEvent;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.refactoring.job.question.EntitySelection;

import com.google.gson.Gson;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.confluencepro.ConfluenceMigrationJobStatus;
import com.xwiki.confluencepro.ConfluenceMigrationManager;

/**
 * The default implementation of {@link ConfluenceMigrationManager}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultConfluenceMigrationManager implements ConfluenceMigrationManager
{
    private static final LocalDocumentReference MIGRATION_OBJECT =
        new LocalDocumentReference(Arrays.asList("ConfluenceMigratorPro", "Code"), "MigrationClass");

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private QueryManager queryManager;

    @Inject
    private ConfluenceMigrationPrerequisitesManager prerequisitesManager;

    @Override
    public void updateAndSaveMigration(ConfluenceMigrationJobStatus jobStatus)
    {
        XWikiContext context = contextProvider.get();
        try {

            XWikiDocument document =
                context.getWiki().getDocument(jobStatus.getRequest().getStatusDocumentReference(), context).clone();
            // Set executed to true.
            BaseObject object = document.getXObject(MIGRATION_OBJECT);
            object.set("executed", 1, context);
            SpaceQuestion spaceQuestion = (SpaceQuestion) jobStatus.getQuestion();
            // Set imported spaces.
            Set<String> spaces = new HashSet<>();
            if (spaceQuestion != null) {
                extractSpaces(spaceQuestion, spaces);
            }
            for (Object question : jobStatus.getAskedQuestions().values()) {
                if (question instanceof SpaceQuestion) {
                    extractSpaces((SpaceQuestion) question, spaces);
                }
            }
            object.set("spaces", new ArrayList<>(spaces), context);

            // Set logs json.
            Gson gson = new Gson();

            extractDocsFromLogs(jobStatus, spaces, object, gson, context);

            List<Map<String, Object>> logList = new ArrayList<>();

            for (LogEvent logEvent : jobStatus.getLogTail().getLogEvents(0, -1)) {
                addToJsonList(logEvent, logList);
            }
            object.set("logs", gson.toJson(logList), context);

            context.getWiki().saveDocument(document, "Migration executed!", context);
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void disablePrerequisites()
    {
        prerequisitesManager.disablePrerequisites();
    }

    @Override
    public void enablePrerequisites()
    {
        prerequisitesManager.enablePrerequisites();
    }

    private void extractDocsFromLogs(ConfluenceMigrationJobStatus jobStatus, Set<String> spaces, BaseObject object,
        Gson gson, XWikiContext context)
    {
        Map<String, List<String>> otherIssues = new TreeMap<>();
        Map<String, ArrayList<LogEvent>> pageToLog = new HashMap<>();
        jobStatus.getLogTail().getLogEvents(0, -1).stream().forEach(
            logEvent -> getPageIdentifierArgument(logEvent, otherIssues).ifPresent(
                pageId -> pageToLog.computeIfAbsent(pageId.getPageTitle(), k -> new ArrayList<>()).add(logEvent)));

        try {
            // We need to retrieve all the confluence pages that had problems when imported. We get the document
            // reference using the title (it is unique per space) and the space.
            List<String[]> documentIds = queryManager.createQuery(
                    "select doc.fullName, doc.title " + "from XWikiDocument as doc "
                        + "where SUBSTRING(doc.fullName, 1, locate('.', doc.fullName) - 1) in (:importedSpaces) "
                        + "and doc.title in (:pages)", Query.XWQL).bindValue("importedSpaces", spaces)
                .bindValue("pages", pageToLog.keySet()).execute();

            Map<String, List<String>> skipped = new TreeMap<>();
            Map<String, List<String>> problematic = new TreeMap<>();

            for (Object[] documentId : documentIds) {
                String serializedDocRef = (String) documentId[0];
                String title = (String) documentId[1];

                for (LogEvent logEvent : pageToLog.get(title)) {
                    if (logEvent.getLevel().equals(LogLevel.ERROR)) {
                        skipped.computeIfAbsent(serializedDocRef, k -> new ArrayList<>())
                            .add(logEvent.getFormattedMessage());
                    } else {
                        problematic.computeIfAbsent(serializedDocRef, k -> new ArrayList<>())
                            .add(logEvent.getFormattedMessage());
                    }
                }
            }
            object.set("skipped", gson.toJson(skipped), context);
            object.set("problems", gson.toJson(problematic), context);
            object.set("otherIssues", gson.toJson(otherIssues), context);
        } catch (QueryException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<PageIdentifier> getPageIdentifierArgument(LogEvent logEvent, Map<String, List<String>> otherIssues)
    {
        if (!logEvent.getLevel().equals(LogLevel.ERROR) && !logEvent.getLevel().equals(LogLevel.WARN)) {
            return Optional.empty();
        }
        if (logEvent.getArgumentArray() == null) {
            otherIssues.computeIfAbsent(logEvent.getLevel().toString(), k -> new ArrayList<>())
                .add(logEvent.getFormattedMessage());
            return Optional.empty();
        }
        Optional<PageIdentifier> pageIdentifier =
            Arrays.stream(logEvent.getArgumentArray()).filter(a -> a instanceof PageIdentifier)
                .map(a -> (PageIdentifier) a).findFirst();
        // Do this here so we don't have to look through the arguments for each log again.
        if (!pageIdentifier.isPresent() || pageIdentifier.get().getPageTitle().isEmpty()) {
            otherIssues.computeIfAbsent(logEvent.getLevel().toString(), k -> new ArrayList<>())
                .add(logEvent.getFormattedMessage());
            return Optional.empty();
        }
        return pageIdentifier;
    }

    private void addToJsonList(LogEvent logEvent, List<Map<String, Object>> logList)
    {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("level", logEvent.getLevel().toString());
        logMap.put("timeStamp", logEvent.getTimeStamp());
        logMap.put("message", logEvent.getFormattedMessage());
        if (logEvent.getThrowable() != null) {
            logMap.put("throwable", ExceptionUtils.getStackFrames(logEvent.getThrowable()));
        }
        logList.add(logMap);
    }

    private void extractSpaces(SpaceQuestion spaceQuestion, Set<String> spaces)
    {
        for (EntitySelection entitySelection : spaceQuestion.getConfluenceSpaces().keySet()) {
            if (entitySelection.isSelected()) {
                spaces.add(serializer.serialize(entitySelection.getEntityReference()));
            }
        }
    }
}
