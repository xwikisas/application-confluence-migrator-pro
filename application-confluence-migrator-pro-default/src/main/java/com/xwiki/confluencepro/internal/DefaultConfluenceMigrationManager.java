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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.xpn.xwiki.XWiki;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.PageIdentifier;
import org.xwiki.contrib.confluence.filter.internal.ConfluenceFilter;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.logging.LogLevel;
import org.xwiki.logging.event.LogEvent;
import org.xwiki.logging.tail.LogTail;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.confluencepro.ConfluenceMigrationJobStatus;
import com.xwiki.confluencepro.ConfluenceMigrationManager;

import static com.xwiki.confluencepro.script.ConfluenceMigrationScriptService.PREFILLED_INPUT_PARAMETERS;
import static com.xwiki.confluencepro.script.ConfluenceMigrationScriptService.PREFILLED_OUTPUT_PARAMETERS;

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
    private static final String OCCURRENCES_KEY = "occurrences";

    private static final String PAGES_KEY = "pages";

    private static final List<String> CONFLUENCE_MIGRATOR_SPACE = Arrays.asList("ConfluenceMigratorPro", "Code");

    private static final String OCCURRENCE_KEY_FORMAT = "%s_oc";

    private static final LocalDocumentReference MIGRATION_OBJECT =
        new LocalDocumentReference(CONFLUENCE_MIGRATOR_SPACE, "MigrationClass");

    private static final String LINKS_BROKEN = "Links to this page may be broken";

    private static final String LOGS = "logs";

    private static final String EXECUTED = "executed";

    private static final String AN_EXCEPTION_OCCURRED = "An exception occurred";

    private static final long WAIT_JOB_MILLIS = 30000;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private ConfluenceMigrationPrerequisitesManager prerequisitesManager;

    @Inject
    private Logger logger;

    @Inject
    private JobContext jobContext;

    private final ConcurrentLinkedDeque<Job> waitingJobs = new ConcurrentLinkedDeque<>();

    private final AtomicReference<Job> runningJob = new AtomicReference<>();

    @Override
    public void updateAndSaveMigration(ConfluenceMigrationJobStatus jobStatus)
    {
        XWikiContext context = contextProvider.get();
        XWikiDocument document = null;
        BaseObject object = null;
        XWiki wiki = context.getWiki();
        DocumentReference statusDocumentReference = jobStatus.getRequest().getStatusDocumentReference();
        try {
            document = wiki.getDocument(statusDocumentReference, context).clone();
            object = document.getXObject(MIGRATION_OBJECT);
            object.set(EXECUTED, jobStatus.isCanceled() ? 3 : 1, context);
            object.setStringListValue("spaces", new ArrayList<>(jobStatus.getSpaces()));
            setLogRelatedFields(jobStatus, object, document, context);
            updateMigrationProperties(object);
            wiki.saveDocument(document, "Migration executed!", context);
        } catch (Exception e) {
            if (object != null) {
                List<Map<String, Object>> logList = new ArrayList<>(1);
                addToJsonList(LogLevel.ERROR, Instant.now().toEpochMilli(), AN_EXCEPTION_OCCURRED, e, logList);
                object.set(LOGS, new Gson().toJson(logList), context);
                object.set(EXECUTED, 4, context);
                logger.error(AN_EXCEPTION_OCCURRED, e);
                try {
                    wiki.saveDocument(document, "Migration failed", context);
                } catch (XWikiException err) {
                    logger.error("Could not update the migration document [{}] with an error status",
                        statusDocumentReference, err);
                }
            }
        }
    }

    private void updateMigrationProperties(BaseObject object)
    {
        Gson gson = new Gson();
        removeDefaultProperties(object, "outputProperties", PREFILLED_OUTPUT_PARAMETERS, gson);
        removeDefaultProperties(object, "inputProperties", PREFILLED_INPUT_PARAMETERS, gson);
    }

    private void removeDefaultProperties(BaseObject object, String field, Map<String, String> defaults, Gson gson)
    {
        boolean update = false;
        Map<String, String> props = gson.fromJson(object.getLargeStringValue(field), Map.class);
        for (Map.Entry<String, String> def : defaults.entrySet()) {
            String key = def.getKey();
            String v = props.get(key);
            if (v != null && v.equals(def.getValue())) {
                props.remove(key);
                update = true;
            }
        }
        if (update) {
            object.setLargeStringValue(field, gson.toJson(props));
        }
    }

    private void replaceKey(Map<String, List<String>> m, String oldKey, String newKey)
    {
        if (m.containsKey(oldKey)) {
            m.put(newKey, m.remove(oldKey));
        }
    }

    private void setLogRelatedFields(ConfluenceMigrationJobStatus jobStatus, BaseObject object, XWikiDocument document,
        XWikiContext context) throws IOException
    {
        // Set logs json.
        Gson gson = new Gson();
        // warning: [w1, w2..],
        // error: [e1, e2..]

        Map<String, List<String>> otherIssues = new TreeMap<>();
        Map<String, List<String>> skipped = new TreeMap<>();
        Map<String, List<String>> problematic = new TreeMap<>();
        Map<String, List<String>> brokenLinksPages = new TreeMap<>();
        // fullName or pageId: [log1, log2..]


        Set<List<String>> brokenLinks = new TreeSet<>((t1, t2) -> {
            int spaceCompare = t1.get(0).compareTo(t2.get(0));
            if (spaceCompare == 0) {
                return t1.get(1).compareTo(t2.get(1));
            }
            return spaceCompare;
        });
        // List of (SPACE, Page title) entries

        Map<String, Object> macroPages = new HashMap<>();
        // Filter the logs.

        String currentDocument = null;
        String currentPageId = null;
        long docCount = 0;
        LogTail logTail = jobStatus.getLogTail();
        List<Map<String, Object>> logList = new ArrayList<>(logTail.size());
        for (LogEvent logEvent : logTail) {
            addToJsonList(logEvent, logList);
            Object[] args = logEvent.getArgumentArray();
            Marker marker = logEvent.getMarker();
            if (Objects.equals(marker, ConfluenceFilter.LOG_MACROS_FOUND)) {
                if (currentDocument != null) {
                    macroPages.put(currentDocument, args[0]);
                }
                continue;
            }

            Map<String, List<String>> cat;
            String msg = logEvent.getMessage();
            if (msg == null) {
                msg = "";
            }
            switch (logEvent.getLevel()) {
                case ERROR:
                    cat = skipped;
                    break;
                case WARN:
                    if (msg.contains(LINKS_BROKEN)) {
                        cat = brokenLinksPages;
                        if (args.length > 1 && args[1] instanceof String && args[0] instanceof String) {
                            brokenLinks.add(List.of((String) args[1], (String) args[0]));
                        }
                    } else {
                        cat = otherIssues;
                    }
                    break;
                case INFO:
                    String markerName = marker != null && marker.getName() != null ? marker.getName() : "";
                    if (markerName.equals("filter.instance.log.document.updated")
                        || markerName.equals("filter.instance.log.document.created")
                    ) {
                        if (args.length > 0 && args[0] instanceof DocumentReference) {
                            currentDocument = serializer.serialize((DocumentReference) args[0]);
                            if (currentPageId != null) {
                                replaceKey(otherIssues, currentPageId, currentDocument);
                                replaceKey(skipped, currentPageId, currentDocument);
                                replaceKey(problematic, currentPageId, currentDocument);
                                replaceKey(brokenLinksPages, currentPageId, currentDocument);
                            }
                        }
                    } else if (msg.startsWith("Sending page [{}]")) {
                        docCount++;
                        currentDocument = null;
                        currentPageId = toString((args.length > 1 && args[1] instanceof Long)
                            ? (Long) args[1]
                            : (args.length > 0 && args[0] instanceof PageIdentifier
                                ? ((PageIdentifier) args[0]).getPageId()
                                : null));
                    }
                    /* fall through */
                default:
                    cat = null;
            }
            if (cat != null) {
                String pageIdOrFullName = getPageIdOrFullName(logEvent, currentDocument, currentPageId);
                if (pageIdOrFullName != null) {
                    cat.computeIfAbsent(pageIdOrFullName, k -> new ArrayList<>()).add(logEvent.getFormattedMessage());
                }
            }
        }

        Map<String, Map<String, Object>> macroMap = createSerializableMacroMap(macroPages);
        persistMacroMap(context, macroMap, gson);

        object.setLargeStringValue("macros", gson.toJson(macroMap.keySet()));
        addAttachment("skipped.json", skipped, document, context, gson);
        addAttachment("problems.json", problematic, document, context, gson);
        addAttachment("otherIssues.json", otherIssues, document, context, gson);
        addAttachment("brokenLinksPages.json", brokenLinksPages, document, context, gson);
        addAttachment("brokenLinks.json", brokenLinks, document, context, gson);
        addAttachment("logs.json", logList, document, context, gson);
        object.setLongValue("imported", docCount);
    }

    private void addAttachment(String name, Object obj, XWikiDocument document, XWikiContext context, Gson gson)
        throws IOException
    {
        document.setAttachment(name, new ByteArrayInputStream(gson.toJson(obj).getBytes()), context);
    }

    private static String toString(Long id)
    {
        if (id == null) {
            return null;
        }
        return id.toString();
    }

    private static String getPageIdOrFullName(LogEvent logEvent, String currentFullName, String currentPageId)
    {
        String pageIdOrFullName = currentFullName;
        if (currentFullName == null) {
            Optional<PageIdentifier> pageIdentifier =
                Arrays.stream(logEvent.getArgumentArray()).filter(PageIdentifier.class::isInstance)
                    .map(a -> (PageIdentifier) a).findFirst();
            pageIdOrFullName = pageIdentifier.isPresent()
                ? toString(pageIdentifier.get().getPageId())
                : currentPageId;
        }
        return pageIdOrFullName;
    }

    private void persistMacroMap(XWikiContext context, Map<String, Map<String, Object>> macroMap, Gson gson)
    {
        try {
            XWikiDocument macroCountDoc = context.getWiki().getDocument(
                new DocumentReference(context.getWikiId(), CONFLUENCE_MIGRATOR_SPACE,
                    "MigratedMacrosCountJSON"), context);
            macroCountDoc.setHidden(true);
            Map<String, Map<String, Integer>> occurenceMap = contentToMap(macroCountDoc, gson,
                new TypeToken<Map<String, Map<String, Integer>>>()
                {
                }.getType());
            XWikiDocument macroDocListDoc = context.getWiki().getDocument(
                new DocumentReference(context.getWikiId(), CONFLUENCE_MIGRATOR_SPACE,
                    "MigratedMacrosDocsJSON"), context);
            macroDocListDoc.setHidden(true);
            Map<String, Map<String, Set<String>>> pagesMap = contentToMap(macroDocListDoc, gson,
                new TypeToken<Map<String, Map<String, Set<String>>>>()
                {
                }.getType());

            prepareMacroMap(macroMap, occurenceMap, pagesMap);
            Map<String, Map<String, Integer>> sortedOccurrences = occurenceMap.entrySet()
                .stream()
                .sorted((o1, o2) -> Integer.compare(o2.getValue().getOrDefault(OCCURRENCES_KEY, 0),
                    o1.getValue().getOrDefault(OCCURRENCES_KEY, 0)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (o1, o2) -> o2, LinkedHashMap::new));
            macroCountDoc.setContent(gson.toJson(sortedOccurrences));
            context.getWiki().saveDocument(macroCountDoc, context);
            macroDocListDoc.setContent(gson.toJson(pagesMap));
            context.getWiki().saveDocument(macroDocListDoc, context);
        } catch (XWikiException e) {

        }
    }

    private void prepareMacroMap(Map<String, Map<String, Object>> macroMap,
        Map<String, Map<String, Integer>> occurenceMap, Map<String, Map<String, Set<String>>> pagesMap)
    {
        for (Map.Entry<String, Map<String, Object>> macroEntry : macroMap.entrySet()) {
            String macroId = macroEntry.getKey();
            Map<String, Object> macroSpacesData = macroEntry.getValue();
            for (Map.Entry<String, Object> macroData : macroSpacesData.entrySet()) {
                String spaceKey = macroData.getKey();
                Map<String, Integer> macroOccurenceMap =
                    occurenceMap.computeIfAbsent(macroId, k -> new HashMap<>());

                if (macroData.getValue() instanceof Integer) {

                    int occurrences = macroOccurenceMap.getOrDefault(OCCURRENCES_KEY, 0);
                    int oldSpaceOccurrences = macroOccurenceMap.getOrDefault(spaceKey, 0);
                    int newSpaceOccurrences = (Integer) macroData.getValue();

                    macroOccurenceMap.put(OCCURRENCES_KEY, occurrences - oldSpaceOccurrences + newSpaceOccurrences);
                    macroOccurenceMap.put(spaceKey, newSpaceOccurrences);
                } else if (macroData.getValue() instanceof Set) {
                    Set<String> pages = (Set<String>) macroData.getValue();

                    int pagesCount = macroOccurenceMap.getOrDefault(PAGES_KEY, 0);
                    int oldPagesCount = macroOccurenceMap.getOrDefault(spaceKey, 0);
                    int newPagesCount = pages.size();
                    macroOccurenceMap.put(PAGES_KEY, pagesCount - oldPagesCount + newPagesCount);
                    macroOccurenceMap.put(spaceKey, newPagesCount);

                    pagesMap.computeIfAbsent(macroId, k -> new HashMap<>()).put(spaceKey, pages);
                }
            }
        }
    }

    private Map contentToMap(XWikiDocument doc, Gson gson, Type type)
    {
        Map map =
            gson.fromJson(doc.getContent(), type);
        if (map == null) {
            map = new HashMap<>();
        }
        return map;
    }

    private Map<String, Map<String, Object>> createSerializableMacroMap(Map<String, Object> macroPages)
    {
        Map<String, Map<String, Object>> macroMap = new HashMap<>();

        for (Map.Entry<String, Object> macroPage : macroPages.entrySet()) {

            Map<String, Integer> pageMacroCount = (Map<String, Integer>) macroPage.getValue();
            String page = macroPage.getKey();
            String space = page.substring(0, page.indexOf('.') > 0 ? page.indexOf('.') : page.length() - 1);
            for (Map.Entry<String, Integer> macroEntry : pageMacroCount.entrySet()) {
                Map<String, Object> serializableMacro =
                    macroMap.computeIfAbsent(macroEntry.getKey(), k -> new HashMap<>());
                serializableMacro.put(String.format(OCCURRENCE_KEY_FORMAT, space),
                    (Integer) serializableMacro.getOrDefault(String.format(OCCURRENCE_KEY_FORMAT, space), 0)
                        + macroEntry.getValue());
                ((Set<String>) (serializableMacro.computeIfAbsent(String.format("%s_pg", space),
                    k -> new HashSet<>()))).add(page);
            }
        }
        return macroMap;
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

    @Override
    public void waitForOtherMigrationsToFinish() throws InterruptedException
    {
        Job currentJob = this.jobContext.getCurrentJob();
        synchronized (this.runningJob) {
            Job r = runningJob.get();
            if (r == null) {
                runningJob.set(currentJob);
                return;
            }
            waitingJobs.offer(currentJob);
        }

        logger.info("Waiting for other running migrations to finish…");

        while (true) {
            final Job jobToWait;
            synchronized (this.runningJob) {
                Job r = runningJob.get();
                Job nextJob = waitingJobs.peek();
                if (nextJob == null || nextJob == currentJob) {
                    if (nextJob == null) {
                        logger.warn("While waiting for other migrations to finish, we found a null migration job "
                            + "in the queue. This is unexpected. Going on anyway.");
                    }

                    if (r == null || r.getStatus().getState() == JobStatus.State.FINISHED) {
                        waitingJobs.poll();
                        runningJob.set(currentJob);
                        return;
                    }

                    jobToWait = r;
                } else if (nextJob.getStatus().getState() == JobStatus.State.FINISHED) {
                    // Maybe the job crashed or was killed
                    waitingJobs.poll();
                    continue;
                } else {
                    jobToWait = nextJob;
                }
            }

            jobToWait.join();
        }
    }

    private void addToJsonList(LogEvent e, List<Map<String, Object>> logList)
    {
        addToJsonList(e.getLevel(), e.getTimeStamp(), e.getFormattedMessage(), e.getThrowable(), logList);
    }

    private void addToJsonList(LogLevel l, long timeStamp, String msg, Throwable t, List<Map<String, Object>> logList)
    {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("level", l.toString());
        logMap.put("timeStamp", timeStamp);
        logMap.put("message", msg);
        if (t != null) {
            try {
                logMap.put("throwable", ExceptionUtils.getStackFrames(t));
            } catch (Exception e) {
                try {
                    logMap.put("unableToGetThrowableReason", ExceptionUtils.getStackFrames(e));
                } catch (Exception ee) {
                    logMap.put("failedToGetThrowableReason",
                        "Unable to get both the exception stack and the reason of the error: " + ee.getMessage());
                }
            }
        }
        logList.add(logMap);
    }
}
