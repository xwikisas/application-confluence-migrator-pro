package com.xwiki.confluencepro.converters.internal;

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

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.ConfluenceFilterReferenceConverter;
import org.xwiki.contrib.confluence.filter.input.ConfluenceInputContext;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

import com.xwiki.date.DateMacroConfiguration;
import com.xwiki.task.TaskConfiguration;
import com.xwiki.task.model.Task;

/**
 * Convert the legacy tasklist macro into a series of task macro calls.
 *
 * @version $Id$
 * @since 1.34.0
 */
@Component
@Singleton
@Named("tasklist")
public class LegacyTaskListMacroConverter extends AbstractTaskConverter
{
    private static final String PARAM_PRIORITY = "priority";
    private static final String PARAM_LOCKED = "locked";
    private static final String VAL_T = "T";
    private static final String TASK = "task";
    private static final String TASKBOX = "taskbox";

    @Inject
    private TaskConfiguration taskConfiguration;

    @Inject
    private DateMacroConfiguration dateMacroConfiguration;

    @Inject
    private ConfluenceFilterReferenceConverter confluenceConverter;

    @Inject
    @Named("xwiki/2.1")
    private BlockRenderer blockRenderer;

    @Inject
    @Named("plain/1.0")
    private Parser plainParser;

    @Inject
    private ConfluenceInputContext context;

    @Inject
    private Logger logger;

    private final Map<Long, Integer> legacyMacrosIdCounter = new HashMap<>();

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return null;
    }

    @Override
    public void toXWiki(String confluenceId, Map<String, String> confluenceParameters, boolean inline,
        String confluenceContent, Listener listener)
    {
        List<Map<String, String>> taskList = getConfluenceTasksFromContent(confluenceContent);
        SimpleDateFormat storageDateFormat = new SimpleDateFormat(dateMacroConfiguration.getStorageDateFormat());
        String title = confluenceParameters.remove("title");
        // It is possible that the title parameter has an empty name.
        title = title == null || title.isEmpty() ? confluenceParameters.remove("") : title;
        // Can't handle these parameters.
        confluenceParameters.remove("promptOnDelete");
        confluenceParameters.remove("enableLocking");
        listener.beginGroup(confluenceParameters);
        maybeTraverseTitle(listener, title);
        for (Map<String, String> task : taskList) {
            if (shouldConvertToTaskbox(confluenceId, confluenceParameters, confluenceContent)) {
                toXWikiTaskbox(task);
                listener.onMacro(TASKBOX, task, task.remove(Task.NAME), false);
            } else {
                toXWikiTask(task, storageDateFormat);

                int refSuffix = legacyMacrosIdCounter.getOrDefault(context.getCurrentPage(), 0);
                legacyMacrosIdCounter.put(context.getCurrentPage(), refSuffix + 1);
                task.put(Task.REFERENCE, "/Tasks/Task_Legacy_" + refSuffix);
                listener.onMacro(TASK, task, task.remove(Task.NAME), false);
            }
        }
        listener.endGroup(confluenceParameters);
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        return Collections.emptyMap();
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {
        return InlineSupport.NO;
    }

    private List<Map<String, String>> getConfluenceTasksFromContent(String confluenceContent)
    {
        List<Map<String, String>> taskList = new ArrayList<>();
        for (String line : confluenceContent.split("\n")) {
            if (line.trim().isEmpty() || line.startsWith("||")) {
                continue;
            }
            Map<String, String> task = new HashMap<>();

            if (line.startsWith("|")) {
                String[] properties = line.substring(1).split("\\|");
                maybePutFromArray(0, properties, task, Task.STATUS);
                maybePutFromArray(1, properties, task, PARAM_PRIORITY);
                maybePutFromArray(2, properties, task, PARAM_LOCKED);
                maybePutFromArray(3, properties, task, Task.CREATE_DATE);
                maybePutFromArray(4, properties, task, Task.COMPLETE_DATE);
                maybePutFromArray(5, properties, task, Task.ASSIGNEE);
                maybePutFromArray(6, properties, task, Task.NAME);
            } else {
                task.put(Task.NAME, line);
            }

            if (!task.isEmpty()) {
                taskList.add(task);
            }
        }
        return taskList;
    }

    private void maybeTraverseTitle(Listener listener, String title)
    {
        if (title != null && !title.trim().isEmpty()) {
            try {
                listener.beginParagraph(Collections.emptyMap());
                listener.beginFormat(Format.BOLD, Collections.emptyMap());
                XDOM titleXDOM = plainParser.parse(new StringReader(title));
                List<Block> blockToTraverse = titleXDOM.getChildren();
                if (!blockToTraverse.isEmpty() && blockToTraverse.get(0) instanceof ParagraphBlock) {
                    blockToTraverse = blockToTraverse.get(0).getChildren();
                }
                blockToTraverse.forEach(child -> child.traverse(listener));
                listener.endFormat(Format.BOLD, Collections.emptyMap());
                listener.endParagraph(Collections.emptyMap());
            } catch (ParseException e) {
                logger.warn("Failed to parse the title [{}] of the tasklist confluence macro. Cause: [{}]",
                    title, ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }

    private void maybePutFromArray(int index, String[] properties, Map<String, String> task, String propName)
    {
        try {
            String property = properties[index];
            if (property.trim().isEmpty()) {
                return;
            }
            task.put(propName, property);
        } catch (IndexOutOfBoundsException e) {
            // If the data is consistent with the example, it shouldn't happen.
            logger.warn(
                "There is no property at index [{}] in array [{}] when trying to extract tasks from a tasklist"
                    + " confluence macro.",
                index, Arrays.toString(properties));
        }
    }

    private void toXWikiTask(Map<String, String> task, SimpleDateFormat storageDateFormat)
    {
        // We can't handle these parameters in the Task Manager application.
        task.remove(PARAM_PRIORITY);
        task.remove(PARAM_LOCKED);

        String xwikiStatus = task.getOrDefault(Task.STATUS, "");
        xwikiStatus = xwikiStatus.equals(VAL_T) ? Task.STATUS_DONE : taskConfiguration.getDefaultInlineStatus();
        task.put(Task.STATUS, xwikiStatus);

        maybeUpdateDateProperty(Task.CREATE_DATE, task, storageDateFormat);
        maybeUpdateDateProperty(Task.COMPLETE_DATE, task, storageDateFormat);

        String content = task.getOrDefault(Task.NAME, "");

        String assignee = task.remove(Task.ASSIGNEE);
        String mentionMacro = getMentionMacro(assignee);
        if (mentionMacro != null && !mentionMacro.trim().isEmpty()) {
            content = String.join(" ", content, mentionMacro);
        }
        task.put(Task.NAME, content);
    }

    private String getMentionMacro(String assignee)
    {
        if (assignee != null && !assignee.trim().isEmpty()) {
            String userRef = confluenceConverter.convertUserReference(assignee);
            if (userRef != null) {
                Map<String, String> mentionParams = new HashMap<>();
                mentionParams.put("style", "FULL_NAME");
                mentionParams.put(Task.REFERENCE, userRef);
                mentionParams.put("anchor",
                    String.join("-",
                        userRef.replace('.', '-'),
                        "legacy",
                        legacyMacrosIdCounter.getOrDefault(context.getCurrentPage(), 0).toString()));
                MacroBlock mentionBlock = new MacroBlock("mention", mentionParams, true);
                WikiPrinter printer = new DefaultWikiPrinter(new StringBuffer());
                blockRenderer.render(Collections.singletonList(mentionBlock), printer);
                return printer.toString();
            }
        }
        return null;
    }

    private void maybeUpdateDateProperty(String key, Map<String, String> task, SimpleDateFormat storageDateFormat)
    {
        try {
            String serializedCreateDate = task.getOrDefault(key, "");
            if (!serializedCreateDate.trim().isEmpty()) {
                Date date = new Date(Long.parseLong(serializedCreateDate.trim()));
                task.put(key, storageDateFormat.format(date));
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse the date [{}] for the [{}] property.", task.get(key), key);
        }
    }

    private void toXWikiTaskbox(Map<String, String> task)
    {
        // Unhandled params.
        task.remove(PARAM_PRIORITY);
        task.remove(PARAM_LOCKED);
        task.remove(Task.CREATE_DATE);
        // Taskbox content.
        String content = task.remove(Task.NAME);
        if (content == null) {
            content = "";
        }
        String assignee = task.remove(Task.ASSIGNEE);
        String mentionMacro = getMentionMacro(assignee);
        if (mentionMacro != null) {
            content = String.join(" ", content, mentionMacro);
        }
        task.put(Task.NAME, content);
        // Checked param.
        String status = task.remove(Task.STATUS);
        status = VAL_T.equals(status) ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
        task.put("checked", status);
        // Id param.
        int refSuffix = legacyMacrosIdCounter.getOrDefault(context.getCurrentPage(), 0);
        legacyMacrosIdCounter.put(context.getCurrentPage(), refSuffix + 1);
        task.put("id", "Legacy" + refSuffix);
    }
}
