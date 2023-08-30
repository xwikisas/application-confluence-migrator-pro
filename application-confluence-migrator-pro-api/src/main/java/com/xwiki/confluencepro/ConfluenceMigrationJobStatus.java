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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xwiki.job.DefaultJobStatus;
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
}
