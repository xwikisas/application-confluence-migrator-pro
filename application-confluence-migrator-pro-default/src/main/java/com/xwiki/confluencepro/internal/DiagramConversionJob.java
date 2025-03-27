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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.DefaultJobStatus;
import org.xwiki.job.GroupedJob;
import org.xwiki.job.JobGroupPath;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentContent;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * The job that will migrate the confluence package into XWiki.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
@Named(DiagramConversionJob.JOBTYPE)
public class DiagramConversionJob
    extends AbstractJob<DiagramConversionJobRequest, DefaultJobStatus<DiagramConversionJobRequest>>
    implements GroupedJob
{
    static final String JOBTYPE = "confluence.diagramconversion";

    private static final JobGroupPath GROUP =
        new JobGroupPath(Arrays.asList("confluencemigratorpro", "diagramconversion"));

    private static final LocalDocumentReference REFERENCE_FIXING_CLASS = new LocalDocumentReference(
        List.of("ConfluenceMigratorPro", "DiagramFixer", "Code"), "DiagramConversionClass");

    private static final String EXECUTED = "executed";

    @Inject
    private DiagramConverter diagramConverter;

    @Inject
    private Provider<XWikiContext> contextProvider;


    @Override
    protected void runInternal() throws Exception
    {
        DocumentReference statusDocumentReference = request.getStatusDocumentReference();
        XWikiContext context = contextProvider.get();
        XWiki wiki = context.getWiki();
        XWikiDocument document = wiki.getDocument(statusDocumentReference, context);
        document.setIntValue(REFERENCE_FIXING_CLASS, EXECUTED, 2);
        wiki.saveDocument(document, "Start session", context);

        logger.info("Starting diagram conversion job");
        MigrationFixingStats s = null;
        try {
            s = diagramConverter.fixDocuments(
                request.getMigrationReferences(),
                request.getSpaceReferences(),
                request.isUpdateInPlace(),
                request.isDryRun()
            );
        } catch (Exception e) {
            document = wiki.getDocument(statusDocumentReference, context);
            document.setIntValue(REFERENCE_FIXING_CLASS, EXECUTED, 3);
            wiki.saveDocument(document, "End failed conversion", context);
            logger.error("Diagram conversion job failed with an exception", e);
        } finally {
            document = wiki.getDocument(statusDocumentReference, context);
            document.setIntValue(REFERENCE_FIXING_CLASS, EXECUTED, 1);
            if (s != null) {
                addAttachment("stats.json", s.toJSON(), document);
            }
            wiki.saveDocument(document, "End session", context);
        }
        logger.info("Finished diagram conversion job");
    }

    private void addAttachment(String name, String content, XWikiDocument document)
    {
        XWikiAttachment a = new XWikiAttachment(document, name);
        XWikiAttachmentContent attachmentContent = new XWikiAttachmentContent(a);
        try {
            attachmentContent.setContent(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            logger.error("Could not save [{}]", name, e);
        }
        a.setAttachment_content(attachmentContent);
        document.setAttachment(a);
    }

    @Override
    public JobGroupPath getGroupPath()
    {
        return GROUP;
    }

    @Override
    public String getType()
    {
        return JOBTYPE;
    }
}
