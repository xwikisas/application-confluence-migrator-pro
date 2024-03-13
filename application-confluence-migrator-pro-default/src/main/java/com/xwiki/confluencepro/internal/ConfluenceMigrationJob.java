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

import com.xwiki.licensing.LicenseType;
import com.xwiki.licensing.Licensor;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.nestedpagesmigrator.MigrationConfiguration;
import org.xwiki.contrib.nestedpagesmigrator.MigrationPlanTree;
import org.xwiki.contrib.nestedpagesmigrator.internal.DefaultNestedPagesMigrator;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanCreatorJobStatus;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanExecutorRequest;
import org.xwiki.contrib.nestedpagesmigrator.internal.job.MigrationPlanRequest;
import org.xwiki.filter.descriptor.FilterStreamDescriptor;
import org.xwiki.filter.input.InputFilterStreamFactory;
import org.xwiki.filter.job.FilterStreamConverterJobRequest;
import org.xwiki.filter.output.OutputFilterStreamFactory;
import org.xwiki.filter.type.FilterStreamType;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.Job;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.refactoring.job.question.EntitySelection;

import com.xwiki.confluencepro.ConfluenceMigrationJobRequest;
import com.xwiki.confluencepro.ConfluenceMigrationJobStatus;
import com.xwiki.confluencepro.ConfluenceMigrationManager;

/**
 * The job that will migrate the confluence package into XWiki and also run the nested pages migration.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
@Named(ConfluenceMigrationJob.JOBTYPE)
public class ConfluenceMigrationJob extends AbstractJob<ConfluenceMigrationJobRequest, ConfluenceMigrationJobStatus>
{
    /**
     * The identifier for the job.
     */
    public static final String JOBTYPE = "confluence.migration";

    private static final String NPMIG_ROLEHINT = "npmig";

    private static final String CONFLUENCE_XML_ROLEHINT = "confluence+xml";

    private static final String XWIKI_INSTANCE_ROLEHINT = "xwiki+instance";

    private static final String FILTER_CONVERTER_ROLEHINT = "filter.converter";

    private static final String NPMIG_EXECUTOR_ROLEHINT = "npmig.executor";

    private static final String FALSE = "false";

    private static final int TRIAL_PAGE_COUNT = 30;

    private static final String MAX_PAGE_COUNT = "maxPageCount";

    @Inject
    @Named(CONFLUENCE_XML_ROLEHINT)
    private InputFilterStreamFactory inputFilterStreamFactory;

    @Inject
    @Named(XWIKI_INSTANCE_ROLEHINT)
    private OutputFilterStreamFactory outputFilterStreamFactory;

    @Inject
    @Named(FILTER_CONVERTER_ROLEHINT)
    private Provider<Job> filterJobProvider;

    @Inject
    @Named(NPMIG_ROLEHINT)
    private Provider<Job> migrationPlanCreatorProvider;

    @Inject
    @Named(NPMIG_EXECUTOR_ROLEHINT)
    private Provider<Job> migrationPlanExecutorProvider;

    @Inject
    private ConfluenceMigrationManager migrationManager;

    @Inject
    private Provider<Licensor> licensorProvider;

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
        boolean rightOnly = isGeneralParameterEnabled("rightOnly");
        boolean shouldRunNestedPagesMigrator = !rightOnly
            && FALSE.equals(request.getInputProperties().getOrDefault("nestedSpacesEnabled", FALSE));
        migrationManager.disablePrerequisites(shouldRunNestedPagesMigrator);
        Map<String, Object> inputProperties = getFilterInputProperties();

        Map<String, Object> outputProperties = getFilterOutputProperties();

        String outputStreamRoleHint = rightOnly
            ? ConfluenceObjectsOnlyInstanceOutputFilterStream.ROLEHINT
            : XWIKI_INSTANCE_ROLEHINT;

        maybeReducePageCount(request);

        FilterStreamConverterJobRequest filterJobRequest = new FilterStreamConverterJobRequest(
            FilterStreamType.unserialize(CONFLUENCE_XML_ROLEHINT), inputProperties,
            FilterStreamType.unserialize(outputStreamRoleHint), outputProperties);
        boolean interactive = !isGeneralParameterEnabled("skipQuestions");
        filterJobRequest.setInteractive(interactive);
        request.setInteractive(interactive);
        progressManager.pushLevelProgress(shouldRunNestedPagesMigrator ? 3 : 1, this);
        logger.info("Starting Filter Job");
        progressManager.startStep(this);
        Job filterJob = this.filterJobProvider.get();
        filterJob.initialize(filterJobRequest);
        filterJob.run();

        if (shouldRunNestedPagesMigrator) {
            SpaceQuestion spaceQuestion =
                (SpaceQuestion) getStatus().getAskedQuestions().values()
                    .stream()
                    .filter(SpaceQuestion.class::isInstance).findFirst()
                    .orElse(null);
            WikiReference wikiReference = this.request.getStatusDocumentReference().getWikiReference();
            if (wikiReference != null) {
                runNestedPagesMigrator(spaceQuestion, wikiReference);
            } else {
                logger.error("Could not start the nested migration job because the wiki couldn't be determined.");
            }
        }

        progressManager.popLevelProgress(this);

        migrationManager.updateAndSaveMigration(getStatus());
        migrationManager.enablePrerequisites();
    }

    private void maybeReducePageCount(ConfluenceMigrationJobRequest request)
    {
        int maxPageCount = -1;
        Object maxPageCountObject = request.getInputProperties().get(MAX_PAGE_COUNT);
        if (maxPageCountObject instanceof String) {
            try {
                maxPageCount = Integer.parseInt((String) maxPageCountObject);
            } catch (NumberFormatException e) {
                // ignore, assume the worst (-1).
            }
        } else if (maxPageCountObject instanceof Integer) {
            maxPageCount = (Integer) maxPageCountObject;
        }

        if (maxPageCount > -1 && maxPageCount <= TRIAL_PAGE_COUNT) {
            return;
        }

        // If there is no license, the user can still trial the application by importing one space and 30 of its pages.
        String wiki = request.getStatusDocumentReference()
            .getWikiReference().getName();
        Licensor licensor = licensorProvider.get();
        if (licensor == null) {
            return;
        }
        DocumentReference mainRef =
            new DocumentReference(wiki, Arrays.asList("ConfluenceMigratorPro", "Code"), "MigrationClass");
        if (!licensor.hasLicensure(mainRef) || licensor.getLicense(mainRef).getType().equals(LicenseType.TRIAL)) {
            request.getInputProperties().put(MAX_PAGE_COUNT, TRIAL_PAGE_COUNT);
        }
    }

    private boolean isGeneralParameterEnabled(String parameterName)
    {
        return "true".equals(request.getOutputProperties().getOrDefault(parameterName, FALSE));
    }

    private Map<String, Object> getFilterOutputProperties()
    {
        FilterStreamDescriptor outputDescriptor = outputFilterStreamFactory.getDescriptor();
        Map<String, Object> outputProperties = outputDescriptor
            .getProperties()
            .stream()
            .collect(HashMap::new, (m, v) -> m.put(v.getId(), v.getDefaultValue()), HashMap::putAll);
        for (Map.Entry<String, Object> entry : request.getOutputProperties().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                outputProperties.put(entry.getKey(), entry.getValue());
            }
        }
        return outputProperties;
    }

    private Map<String, Object> getFilterInputProperties()
    {
        FilterStreamDescriptor inputDescriptor = inputFilterStreamFactory.getDescriptor();

        // Not using Collectors.toMap() because of https://bugs.openjdk.org/browse/JDK-8148463
        Map<String, Object> inputProperties = inputDescriptor
            .getProperties()
            .stream()
            .collect(HashMap::new, (m, v) -> m.put(v.getId(), v.getDefaultValue()), HashMap::putAll);

        if (getRequest().getConfluencePackage() != null) {
            inputProperties.put("source", getRequest().getConfluencePackage());
        }
        for (Map.Entry<String, Object> entry : request.getInputProperties().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                inputProperties.put(entry.getKey(), entry.getValue());
            }
        }
        return inputProperties;
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
            Arrays.asList(NPMIG_ROLEHINT, DefaultNestedPagesMigrator.EXECUTE_PLAN,
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
            Arrays.asList(NPMIG_ROLEHINT, DefaultNestedPagesMigrator.CREATE_PLAN,
                configuration.getWikiReference().getName()));
        migrationPlanRequest.setConfiguration(configuration);

        Job planCreationJob = migrationPlanCreatorProvider.get();
        planCreationJob.initialize(migrationPlanRequest);
        planCreationJob.run();
        return planCreationJob;
    }
}
