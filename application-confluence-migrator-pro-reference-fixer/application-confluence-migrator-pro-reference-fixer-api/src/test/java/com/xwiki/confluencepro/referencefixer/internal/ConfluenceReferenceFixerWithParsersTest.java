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
package com.xwiki.confluencepro.referencefixer.internal;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xwiki.confluencepro.referencefixer.BrokenRefType;
import com.xwiki.licensing.internal.upgrades.LicensingSchedulerListener;

import org.junit.jupiter.api.Test;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.confluence.resolvers.internal.DefaultConfluencePageResolver;
import org.xwiki.contrib.confluence.resolvers.internal.DefaultConfluenceSpaceResolver;
import org.xwiki.extension.xar.internal.handler.XarExtensionJobFinishedListener;
import org.xwiki.query.internal.DefaultQueryManager;
import org.xwiki.search.solr.internal.EmbeddedSolr;
import org.xwiki.search.solr.internal.reference.SolrEntityReferenceResolver;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.mockito.ComponentTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ComponentTest
@OldcoreTest
@AllComponents (excludes = {
    DefaultQueryManager.class,
    EmbeddedSolr.class,
    XarExtensionJobFinishedListener.class,
    LicensingSchedulerListener.class,
    DefaultConfluencePageResolver.class,
    DefaultConfluenceSpaceResolver.class,
    SolrEntityReferenceResolver.class
})
class ConfluenceReferenceFixerWithParsersTest extends ConfluenceReferenceFixerTestBase
{
    @Test
    void testMigrationBrokenLinkInAttachment() throws ComponentLookupException, XWikiException, IOException
    {
        List<String> docsWithReferenceIssues = List.of(
            "MySpace.Doc10.WebHome"
        );

        List<String> docsWithoutReferenceIssues = List.of(
            "MySpace.Doc1A.WebHome"
        );

        String content = "[[MySpace.My Answer]] [[confluencePage:id:42]] [[http://base.url/x/2EkGOQ]]";
        String expected = "[[doc:Migrated.MySpace.My Answer.WebHome]] [[doc:xwiki:MyAnswer.WebHome]] "
            + "[[http://base.url/x/2EkGOQ]]";

        for (String testDoc : docsWithReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        XWikiDocument migrationDoc = addDoc("Migrations.Migration10", "", false);
        migrationDoc.setAttachment("brokenLinksPages.json", new ByteArrayInputStream(
            ("{\"xwiki:MySpace.Doc10.WebHome\":{}}").getBytes(StandardCharsets.UTF_8)
        ), context);

        wiki.saveDocument(migrationDoc, context);

        fixer.fixDocuments(
            List.of(migrationDoc.getDocumentReference()),
            null, null, BrokenRefType.BROKEN_LINKS, false, true, false
        );

        for (String testDoc : docsWithReferenceIssues) {
            assertEquals(expected + ' ' + testDoc, getContent(testDoc));
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            assertEquals(content + ' ' + testDoc, getContent(testDoc));
        }
    }
}
