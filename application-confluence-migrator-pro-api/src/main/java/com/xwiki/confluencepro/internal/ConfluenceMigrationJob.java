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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.confluence.filter.internal.input.ConfluenceInputFilterStreamFactory;
import org.xwiki.contrib.nestedpagesmigrator.MigrationConfiguration;
import org.xwiki.contrib.nestedpagesmigrator.MigrationPlanTree;
import org.xwiki.contrib.nestedpagesmigrator.internal.DefaultNestedPagesMigrator;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanCreatorJob;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanCreatorJobStatus;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanExecutorJob;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanExecutorRequest;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanRequest;
import org.xwiki.filter.FilterStreamFactory;
import org.xwiki.filter.descriptor.FilterStreamDescriptor;
import org.xwiki.filter.input.InputFilterStreamFactory;
import org.xwiki.filter.instance.internal.input.InstanceInputFilterStreamFactory;
import org.xwiki.filter.internal.job.FilterStreamConverterJob;
import org.xwiki.filter.job.FilterStreamConverterJobRequest;
import org.xwiki.filter.output.OutputFilterStreamFactory;
import org.xwiki.filter.type.FilterStreamType;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.Job;
import org.xwiki.job.JobGroupPath;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.refactoring.job.question.EntitySelection;

import com.xwiki.confluencepro.ConfluenceMigrationJobRequest;
import com.xwiki.confluencepro.ConfluenceMigratorManager;

/**
 * The job that will migrate the confluence package into XWiki and also run the nested pages migration.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
@Named(ConfluenceMigrationJob.JOBTYPE)
public class ConfluenceMigrationJob
    extends AbstractJob<ConfluenceMigrationJobRequest, ConfluenceMigrationJobStatus>
{
    /**
     * The identifier for the job.
     */
    public static final String JOBTYPE = "confluence.migrator";

    /**
     * The root group of the job.
     */
    public static final JobGroupPath ROOT_GROUP =
        new JobGroupPath(Arrays.asList("confluence", "migrator"));

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    @Named(FilterStreamConverterJob.JOBTYPE)
    private Provider<Job> filterJobProvider;

    @Inject
    @Named(MigrationPlanCreatorJob.JOB_TYPE)
    private Provider<Job> migrationPlanCreatorProvider;

    @Inject
    @Named(MigrationPlanExecutorJob.JOB_TYPE)
    private Provider<Job> migrationPlanExecutorProvider;

    @Inject
    private ConfluenceMigratorManager migratorManager;

    /**
     * @return the job type.
     */
    @Override
    public String getType()
    {
        return JOBTYPE;
    }

    @Override
    protected ConfluenceMigrationJobStatus createNewStatus(ConfluenceMigrationJobRequest request)
    {
        Job currentJob = this.jobContext.getCurrentJob();
        JobStatus currentJobStatus = currentJob != null ? currentJob.getStatus() : null;
        return new ConfluenceMigrationJobStatus(request, currentJobStatus, observationManager, loggerManager);
    }

    /**
     * Run the job.
     *
     * @throws Exception if something goes wrong with the job.
     */
    @Override
    protected void runInternal() throws Exception
    {
        String input = ConfluenceInputFilterStreamFactory.ROLEHINT;
        String output = InstanceInputFilterStreamFactory.ROLEHINT;

        FilterStreamDescriptor inputDescriptor = this.componentManagerProvider.get()
            .<FilterStreamFactory>getInstance(InputFilterStreamFactory.class, input).getDescriptor();
        FilterStreamDescriptor outputDescriptor = this.componentManagerProvider.get()
            .<FilterStreamFactory>getInstance(OutputFilterStreamFactory.class, output).getDescriptor();

        Map<String, Object> inputProperties = inputDescriptor
            .getProperties()
            .stream()
            .collect(HashMap::new, (m, v) -> m.put(v.getId(), v.getDefaultValue()), HashMap::putAll);

        Map<String, Object> outputProperties = outputDescriptor
            .getProperties()
            .stream()
            .collect(HashMap::new, (m, v) -> m.put(v.getId(), v.getDefaultValue()), HashMap::putAll);

        inputProperties.put("source", getRequest().getConfluencePackage());
        for (Map.Entry<String, Object> entry : request.getInputProperties().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                inputProperties.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Object> entry : request.getOutputProperties().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                outputProperties.put(entry.getKey(), entry.getValue());
            }
        }
        FilterStreamConverterJobRequest filterJobRequest =
            new FilterStreamConverterJobRequest(FilterStreamType.unserialize(input), inputProperties,
                FilterStreamType.unserialize(output), outputProperties);
        filterJobRequest.setInteractive(true);
        logger.info("Starting Filter Job");
        progressManager.pushLevelProgress(3, this);
        progressManager.startStep(this);
        Job filterJob = this.filterJobProvider.get();
        filterJob.initialize(filterJobRequest);
        filterJob.run();

        SpaceQuestion spaceQuestion =
            (SpaceQuestion) getStatus().getAskedQuestions().values()
                .stream()
                .filter(q -> q instanceof SpaceQuestion).findFirst()
                .orElse(null);
        WikiReference wikiReference = this.request.getDocumentReference().getWikiReference();
        if (wikiReference != null) {
            runNestedPagesMigrator(spaceQuestion, wikiReference);
        } else {
            logger.error("Could not start the nested migration job because the the wiki couldn't be determined.");
        }

        progressManager.popLevelProgress(this);

        migratorManager.updateAndSaveMigration(getStatus());
    }

    private void runNestedPagesMigrator(SpaceQuestion spaceQuestion, WikiReference wikiReference)
    {
        MigrationConfiguration configuration = new MigrationConfiguration(wikiReference);
        configuration.setAddAutoRedirect(false);

        if (spaceQuestion != null) {
            for (EntitySelection entitySelection : spaceQuestion.getConfluenceSpaces().keySet()) {
                configuration.addIncludedSpace(
                    new SpaceReference(entitySelection.getEntityReference().getName(), wikiReference));
            }
            if (configuration.hasIncludedSpaces()) {
                progressManager.startStep(this);
                // Execute plan creation job.
                Job planCreationJob = executePlanCreationJob(configuration);
                progressManager.startStep(this);
                // Execute migration migration job.
                executePlanExecutionJob(configuration, planCreationJob);
            } else {
                logger.info("No spaces migrated. Skipping Nested Paged Migration.");
            }
        }
    }

    private void executePlanExecutionJob(MigrationConfiguration configuration, Job planCreationJob)
    {
        MigrationPlanTree planTree = ((MigrationPlanCreatorJobStatus) planCreationJob.getStatus()).getPlan();
        MigrationPlanExecutorRequest migrationRequest = new MigrationPlanExecutorRequest();
        migrationRequest.setId(
            Arrays.asList(MigrationPlanCreatorJob.JOB_TYPE, DefaultNestedPagesMigrator.EXECUTE_PLAN,
                configuration.getWikiReference().getName()));
        migrationRequest.setConfiguration(configuration);
        migrationRequest.setPlan(planTree);

        Job migrationJob = migrationPlanExecutorProvider.get();
        migrationJob.initialize(migrationRequest);
        migrationJob.run();
    }

    private Job executePlanCreationJob(MigrationConfiguration configuration)
    {
        MigrationPlanRequest migrationPlanRequest = new MigrationPlanRequest();
        migrationPlanRequest.setId(
            Arrays.asList(MigrationPlanCreatorJob.JOB_TYPE, DefaultNestedPagesMigrator.CREATE_PLAN,
                configuration.getWikiReference().getName()));
        migrationPlanRequest.setConfiguration(configuration);

        Job planCreationJob = migrationPlanCreatorProvider.get();
        planCreationJob.initialize(migrationPlanRequest);
        planCreationJob.run();
        return planCreationJob;
    }
}
