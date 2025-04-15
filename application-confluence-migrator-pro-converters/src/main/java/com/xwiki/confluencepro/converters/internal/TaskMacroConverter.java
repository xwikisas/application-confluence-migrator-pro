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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;

import com.xwiki.task.TaskConfiguration;
import com.xwiki.task.model.Task;

/**
 * Convert task macros.
 *
 * @version $Id$
 * @since 1.20.2
 */
@Component
@Singleton
@Named("task")
public class TaskMacroConverter extends AbstractMacroConverter
{
    private static final String TASK_STATUS_PARAMETER = "status";

    private static final String TASK_ID_PARAMETER = "id";

    private static final String TASK_REFERENCE_PARAMETER = "reference";

    private static final String TASK_REFERENCE_PREFIX = "/Tasks/Task_";

    @Inject
    private TaskConfiguration taskConfiguration;

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
        return newContent.replace("\n \n\n{{task", "\n\n{{task");
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> params = new HashMap<>();
        // TODO: Use a configurable value instead of "Done".
        String confluenceStatus = confluenceParameters.get(TASK_STATUS_PARAMETER);
        String xwikiStatus = confluenceStatus.equals("complete") || confluenceStatus.equals(Task.STATUS_DONE)
            ? Task.STATUS_DONE
            : taskConfiguration.getDefaultInlineStatus();
        String confluenceTaskId = confluenceParameters.get(TASK_ID_PARAMETER);
        String reference = confluenceParameters.get(TASK_REFERENCE_PARAMETER);
        String xwikiIdParam = confluenceTaskId != null
            ? TASK_REFERENCE_PREFIX + confluenceTaskId
            : reference;

        params.put(TASK_STATUS_PARAMETER, xwikiStatus);
        params.put(TASK_REFERENCE_PARAMETER, xwikiIdParam);
        return params;
    }
}
