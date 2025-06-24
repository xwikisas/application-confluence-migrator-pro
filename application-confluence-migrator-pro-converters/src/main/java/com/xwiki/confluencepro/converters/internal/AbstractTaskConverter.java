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

import java.util.Map;

import javax.inject.Inject;

import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;
import org.xwiki.filter.job.FilterStreamConverterJobRequest;
import org.xwiki.job.Job;
import org.xwiki.job.JobContext;
import org.xwiki.job.Request;
import org.xwiki.job.event.status.JobStatus;

/**
 * Abstract class that offers methods to determine whether to convert action items to tasks or taskboxes.
 *
 * @version $Id$
 * @since 1.34.10
 */
public abstract class AbstractTaskConverter extends AbstractMacroConverter
{
    @Inject
    private JobContext jobContext;

    protected boolean shouldConvertToTaskbox(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        return (content != null && content.trim().isEmpty() && isPropertyEnabled("emptyActionsToCheckboxes"))
            || isPropertyEnabled("actionsToCheckboxes");
    }

    protected boolean isPropertyEnabled(String property)
    {
        Job job = this.jobContext.getCurrentJob();
        if (job == null) {
            return false;
        }
        JobStatus jobStatus = job.getStatus();
        if (jobStatus == null) {
            return false;
        }
        Request jobRequest = jobStatus.getRequest();
        if (!(jobRequest instanceof FilterStreamConverterJobRequest)) {
            return false;
        }

        Map<String, Object> outputProps = ((FilterStreamConverterJobRequest) jobRequest).getOutputProperties();
        if (outputProps == null) {
            return false;
        }
        return Boolean.TRUE.toString().equals(outputProps.getOrDefault(property, Boolean.FALSE.toString()));
    }
}
