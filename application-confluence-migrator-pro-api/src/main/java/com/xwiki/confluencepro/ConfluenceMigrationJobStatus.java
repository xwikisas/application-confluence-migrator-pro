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
package com.xwiki.confluencepro;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.job.DefaultJobStatus;
import org.xwiki.job.event.status.CancelableJobStatus;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.logging.LoggerManager;
import org.xwiki.observation.ObservationManager;

/**
 * Custom Job Status that holds the questions asked by sub-jobs.
 *
 * @since 1.0
 * @version $Id$
 */
public class ConfluenceMigrationJobStatus extends DefaultJobStatus<ConfluenceMigrationJobRequest>
{
    private final Map<List<String>, Object> askedQuestions = new HashMap<>();

    private CancelableJobStatus filterJobStatus;

    private Collection<String> spaces = Collections.emptyList();

    /**
     * @param request the request provided when started the job
     * @param parentJobStatus the status of the parent job
     * @param observationManager the observation manager component
     * @param loggerManager the logger manager component
     */
    public ConfluenceMigrationJobStatus(ConfluenceMigrationJobRequest request,
        JobStatus parentJobStatus, ObservationManager observationManager,
        LoggerManager loggerManager)
    {
        super("confluence.migration", request, parentJobStatus, observationManager, loggerManager);
        setCancelable(true);
    }

    /**
     * @param jobId the identifier of the job.
     * @param question the asked question.
     */
    public void addAskedQuestion(List<String> jobId, Object question)
    {
        this.askedQuestions.put(jobId, question);
    }

    /**
     * @return a map of questions each identified by the job identifiers.
     */
    public Map<List<String>, Object> getAskedQuestions()
    {
        return this.askedQuestions;
    }

    /**
     * @param filterJobStatus set the filter job status so it can be canceled.
     * @since 1.14.0
     */
    public void setFilterJobStatus(CancelableJobStatus filterJobStatus)
    {
        this.filterJobStatus = filterJobStatus;
    }

    /**
     * Set the list of spaces that are migrated.
     * @param spaces the Confluence keys of the migrated spaces
     * @since 1.19.0
     */
    public void setSpaces(Collection<String> spaces)
    {
        this.spaces = spaces;
    }

    /**
     * @return the list of spaces that are migrated.
     * @since 1.19.0
     */

    public Collection<String> getSpaces()
    {
        return spaces;
    }

    @Override
    public void cancel()
    {
        super.cancel();
        if (this.filterJobStatus != null) {
            this.filterJobStatus.cancel();
        }
    }
}
