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
package com.xwiki.confluencepro.converters.internal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.xwiki.date.DateMacroConfiguration;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

import com.xwiki.task.TaskConfiguration;
import com.xwiki.task.model.Task;
import org.xwiki.contrib.confluence.filter.ConfluenceFilterReferenceConverter;
import org.xwiki.contrib.confluence.filter.input.ConfluenceInputContext;
import org.xwiki.contrib.confluence.filter.input.ConfluenceXMLPackage;
import org.xwiki.contrib.confluence.filter.task.ConfluenceTask;
import org.xwiki.filter.FilterException;

/**
 * Convert task macros.
 *
 * @version $Id$
 * @since 1.20.2
 */
@Component
@Singleton
@Named("task")
public class TaskMacroConverter extends AbstractTaskConverter
{
    private static final String TASK_STATUS_PARAMETER = "status";

    private static final String TASK_STATUS_COMPLETE = "complete";

    private static final String TASK_ID_PARAMETER = "id";

    private static final String TASK_REFERENCE_PARAMETER = "reference";

    private static final String TASK_REFERENCE_PREFIX = "/Tasks/Task_";

    private static final String TASKBOX_CHECKED_PARAMETER = "checked";

    @Inject
    private DateMacroConfiguration dateMacroConfiguration;

    @Inject
    private TaskConfiguration taskConfiguration;

    @Inject
    private ConfluenceInputContext context;

    @Inject
    private ConfluenceFilterReferenceConverter converter;

    @Inject
    private Logger log;

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return shouldConvertToTaskbox(confluenceId, confluenceParameters, confluenceContent)
            ? "checkbox"
            : "task";
    }

    @Override
    protected String toXWikiContent(String confluenceId, Map<String, String> parameters, String confluenceContent)
    {
        if (confluenceContent == null) {
            return "";
        }
        // A workaround for issue XWIKI-20805 that causes the wysiwyg editor to delete the macros inside the content of
        // another macro when saving the page.
        String newContent = confluenceContent.replace("(% class=\"placeholder-inline-tasks\" %)", "");

        // On certain confluence versions, some tasks may contain subtasks. If the subtasks were introduced inline,
        // since the confluence tasks are not supported inline, they also inserted a <br/> element which gets converted
        // to a "\n " in XWiki. This newline does not appear in confluence.
        return newContent.replaceAll("\\s*\n\\s*\n\\s*\\{\\{task", "\n\n{{task").trim();
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {
        return InlineSupport.YES;
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> params = new HashMap<>();
        if (shouldConvertToTaskbox(confluenceId, confluenceParameters, content)) {
            if (confluenceParameters.get(TASK_STATUS_PARAMETER).equals(TASK_STATUS_COMPLETE)) {
                params.put(TASKBOX_CHECKED_PARAMETER, Boolean.TRUE.toString());
            } else {
                params.put(TASKBOX_CHECKED_PARAMETER, Boolean.FALSE.toString());
            }
            params.put(TASK_ID_PARAMETER, confluenceParameters.get(TASK_ID_PARAMETER));
            return params;
        }
        // TODO: Use a configurable value instead of "Done".
        String confluenceStatus = confluenceParameters.get(TASK_STATUS_PARAMETER);
        String xwikiStatus = confluenceStatus.equals(TASK_STATUS_COMPLETE) || confluenceStatus.equals(Task.STATUS_DONE)
            ? Task.STATUS_DONE
            : taskConfiguration.getDefaultInlineStatus();
        String confluenceTaskId = confluenceParameters.get(TASK_ID_PARAMETER);
        String reference = confluenceParameters.get(TASK_REFERENCE_PARAMETER);
        String xwikiIdParam = confluenceTaskId != null
            ? TASK_REFERENCE_PREFIX + confluenceTaskId
            : reference;

        params.put(TASK_STATUS_PARAMETER, xwikiStatus);
        params.put(TASK_REFERENCE_PARAMETER, xwikiIdParam);
        maybeImportDetailsFromCSV(confluenceTaskId, params);
        return params;
    }

    private void maybeImportDetailsFromCSV(String confluenceTaskId, Map<String, String> params)
    {
        ConfluenceXMLPackage confluencePackage = context.getConfluencePackage();
        Long pageId = context.getCurrentPage();
        if (confluencePackage != null && confluenceTaskId != null && pageId != null) {
            int taskId = Integer.parseInt(confluenceTaskId);
            try {
                ConfluenceTask task = confluencePackage.getTask(pageId, taskId);
                if (task != null) {
                    String creatorUserKey = task.getCreatorUserKey();
                    String creator = converter.convertUserReference(creatorUserKey);
                    params.put("reporter", creator);
                    params.put("completeDate", formatDate(task.getCompleteDate()));
                    params.put("createDate", formatDate(task.getCreateDate()));
                }
            } catch (FilterException e) {
                log.warn("Failed to get task [{}] in page [{}], details might be missing", taskId, pageId, e);
            }
        }
    }

    private String formatDate(Date date)
    {
        if (date == null) {
            return null;
        }

        return new SimpleDateFormat(dateMacroConfiguration.getStorageDateFormat()).format(date);
    }
}
