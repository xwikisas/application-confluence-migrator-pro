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
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.confluence.internal.parser.reference.type.ConfluenceAttachResourceReferenceTypeParser;
import org.xwiki.contrib.confluence.internal.parser.reference.type.ConfluencePageResourceReferenceTypeParser;
import org.xwiki.contrib.confluence.internal.parser.reference.type.ConfluenceSpaceResourceReferenceTypeParser;
import org.xwiki.contrib.confluence.resolvers.internal.DefaultConfluencePageResolver;
import org.xwiki.contrib.confluence.resolvers.internal.DefaultConfluenceSpaceResolver;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.query.internal.DefaultQueryManager;
import org.xwiki.search.solr.internal.EmbeddedSolr;
import org.xwiki.search.solr.internal.reference.SolrEntityReferenceResolver;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.mockito.ComponentTest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ComponentTest
@OldcoreTest
@AllComponents(excludes = {
    DefaultQueryManager.class,
    EmbeddedSolr.class,
    DefaultConfluencePageResolver.class,
    DefaultConfluenceSpaceResolver.class,
    SolrEntityReferenceResolver.class,
    ConfluencePageResourceReferenceTypeParser.class,
    ConfluenceSpaceResourceReferenceTypeParser.class,
    ConfluenceAttachResourceReferenceTypeParser.class
})
class ConfluenceReferenceFixerWithoutParsersTest extends ConfluenceReferenceFixerTestBase
{

    private static final String INPUT_PROPERTIES = "inputProperties";
    private static final String SPACE_A = "SpaceA";
    private static final String SPACE_B = "SpaceB";
    private static final String SPACES = "spaces";
    private static final String COMMENT = "comment";

    @Test
    void testMostFixing() throws ComponentLookupException, IOException, XWikiException
    {
        docs = new ArrayList<>();
        File docsDir = new File("src/test/resources/documents");
        for (String fullName : Objects.requireNonNull(docsDir.list())) {
            File docDir = new File(docsDir, fullName);
            File contentFile = new File(docDir, "content.txt");
            Path contentFilePath = contentFile.toPath();
            String content = Files.readString(contentFilePath, StandardCharsets.UTF_8);
            addDoc(fullName, content, false);
        }

        fixer.fixDocuments(
            null,
            List.of(MY_SPACE),
            new String[] {"http://base.url/", "http://base2.url/"}, null, true, false
        );
        for (XWikiDocument doc : docs) {
            assertEquals(
                getExpected(doc.getDocumentReference()).trim(),
                getContent(doc.getDocumentReference())
            );
        }
    }

    @Test
    void testBrowseSpaces() throws ComponentLookupException, XWikiException
    {
        List<String> convertedTestDocs = List.of(
            "Migrated.SpaceA.Doc1.WebHome",
            "Migrated.SpaceA.Doc2.WebHome",
            "Migrated.SpaceA.Docs.SubDoc1.WebHome",
            "Migrated.SpaceA.Docs.SubDoc2.WebHome",
            "Migrated.SpaceB.Docs.SubDoc2.WebHome"
        );

        List<String> unconvertedTestDocs = List.of(
            "Migrated.SpaceC.DocNotConverted.WebHome"
        );

        for (String testDoc : convertedTestDocs) {
            addDoc(testDoc, CONTENT, false);
        }

        for (String testDoc : unconvertedTestDocs) {
            addDoc(testDoc, CONTENT, false);
        }

        fixer.fixDocuments(
            null,
            List.of(
                new EntityReference(SPACE_A, EntityType.SPACE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE)),
                new EntityReference(SPACE_B, EntityType.SPACE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE))
            ),
            null, null, true, false
        );

        for (String testDoc : convertedTestDocs) {
            assertEquals(CONVERTED, getContent(testDoc));
        }

        for (String testDoc : unconvertedTestDocs) {
            assertEquals(CONTENT, getContent(testDoc));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testDryRun(boolean dryRun) throws ComponentLookupException, XWikiException
    {
        List<String> convertedTestDocs = List.of(
            "Migrated.SpaceA.Dry.WebHome"
        );

        for (String testDoc : convertedTestDocs) {
            addDoc(testDoc, CONTENT, false);
        }

        fixer.fixDocuments(
            null,
            List.of(
                new EntityReference(SPACE_A, EntityType.SPACE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE))
            ),
            null, null, true, dryRun
        );

        for (String testDoc : convertedTestDocs) {
            assertEquals(dryRun ? CONTENT : CONVERTED, getContent(testDoc));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nothing to convert", ""})
    void testNothingToConvert(String content) throws ComponentLookupException, XWikiException
    {
        List<String> convertedTestDocs = List.of(
            "Migrated.Empty.WebHome"
        );
        for (String testDoc : convertedTestDocs) {
            addDoc(testDoc, content, false);
        }

        fixer.fixDocuments(
            null,
            List.of(
                new EntityReference(SPACE_A, EntityType.SPACE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE))
            ),
            null, null, true, true
        );

        for (String testDoc : convertedTestDocs) {
            assertEquals(content, getContent(testDoc));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUpdateInPlaceAndComment(boolean updateInPlace) throws ComponentLookupException, XWikiException
    {
        List<String> convertedTestDocs = List.of(
            "Migrated.SpaceA.Update.WebHome"
        );

        for (String testDoc : convertedTestDocs) {
            addDoc(testDoc, CONTENT, true);
        }

        fixer.fixDocuments(
            null,
            List.of(
                new EntityReference(SPACE_A, EntityType.SPACE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE))
            ),
            null, null, updateInPlace, false
        );

        for (String testDoc : convertedTestDocs) {
            assertEquals(CONVERTED, getContent(testDoc));
            Vector<BaseObject> comments = wiki.getDocument(getEntityReference(testDoc), context).getComments();
            assertEquals(1, comments.size());
            assertEquals(CONVERTED, comments.get(0).getLargeStringValue(COMMENT));
            assertEquals(updateInPlace ? "1.1" : "2.1",
                wiki.getDocument(getEntityReference(testDoc), context).getRCSVersion().toString());
        }
    }

    @Test
    void testBrowseMigrationsConfluenceRefs() throws ComponentLookupException, XWikiException, IOException
    {
        List<String> docsWithReferenceIssues = List.of(
            "MySpace.Doc1.WebHome",
            "MySpace.Doc2.WebHome",
            "MySpace.Doc3.WebHome",
            "MySpace.Doc4.WebHome"
        );

        List<String> docsWithoutReferenceIssues = List.of(
            "MySpace.DocA.WebHome",
            "MySpace.DocB.WebHome"
        );

        for (String testDoc : docsWithReferenceIssues) {
            addDoc(testDoc, CONTENT + ' ' + testDoc, false);
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            addDoc(testDoc, CONTENT + ' ' + testDoc, false);
        }

        XWikiDocument migration1Doc = addDoc("Migrations.Migration1", "", false);
        migration1Doc.setAttachment(CONFLUENCE_REF_WARNINGS_JSON, new ByteArrayInputStream((
            "{ \"xwiki:MySpace.DocA.WebHome\":["
                + "   {\"pageId\":2,\"originalVersion\":1}],"
                + "\"xwiki:MySpace.Doc1.WebHome\":["
                + "   {\"pageId\":3,\"originalVersion\":3}],"
                + "\"xwiki:MySpace.Doc2.WebHome\":["
                + "   {\"pageId\":5,\"originalVersion\":4},"
                + "   {\"pageId\":5,\"originalVersion\":5}],"
                + "\"xwiki:MySpace.Doc3.WebHome\":["
                + "   {\"pageId\":6}]}").getBytes(StandardCharsets.UTF_8)
        ), context);

        wiki.saveDocument(migration1Doc, context);

        XWikiDocument migration2Doc = addDoc("Migrations.Migration2", "", false);
        migration2Doc.setAttachment(CONFLUENCE_REF_WARNINGS_JSON, new ByteArrayInputStream(
            ("{\"xwiki:MySpace.Doc4.WebHome\":[{\"pageId\":7}]}").getBytes(StandardCharsets.UTF_8)
        ), context);

        wiki.saveDocument(migration2Doc, context);

        fixer.fixDocuments(
            List.of(migration1Doc.getDocumentReference(), migration2Doc.getDocumentReference()),
            null, new String[] {"http://b.url/"}, null, true, false
        );

        for (String testDoc : docsWithReferenceIssues) {
            assertEquals(CONVERTED + ' ' + testDoc, getContent(testDoc));
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            assertEquals(CONTENT + ' ' + testDoc, getContent(testDoc));
        }
    }

    @Test
    void testMigrationBrokenLinkInAttachment() throws ComponentLookupException, XWikiException, IOException
    {
        wiki.saveDocument(newExistingDoc("MyBlogSpace.Blog.Hello world"), context);

        List<String> docsWithReferenceIssues = List.of(
            "MySpace.Doc10.WebHome"
        );

        List<String> docsWithoutReferenceIssues = List.of(
            "MySpace.Doc1A.WebHome"
        );

        String content = "[[url:http://BASE.URL/x/2EkGOQ]][[MySpace.My Answer]][[confluencePage:id:42]]"
            + "{{children reference=\"document:MySpace.My Answer\"/}}";
        String expected = "[[doc:Ok2EkGOQ.WebHome]][[doc:Migrated.MySpace.My Answer.WebHome]][[confluencePage:id:42]]"
            + "{{children reference=\"document:Migrated.MySpace.My Answer.WebHome\"/}}";

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
            null, new String[] {"http://BASE.URL/"}, null, true, false
        );

        for (String testDoc : docsWithReferenceIssues) {
            assertEquals(expected + ' ' + testDoc, getContent(testDoc));
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            assertEquals(content + ' ' + testDoc, getContent(testDoc));
        }
    }

    @Test
    void testMigrationBrokenLinkInProperty() throws ComponentLookupException, XWikiException
    {
        List<String> docsWithReferenceIssues = List.of(
            "MySpace.Doc20.WebHome"
        );

        List<String> docsWithoutReferenceIssues = List.of(
            "MySpace.Doc2A.WebHome"
        );

        String content = "[[MySpace.My Answer]][[confluencePage:id:42]][[http://base.url/x/2EkGOQ]]";
        String expected = "[[doc:Migrated.MySpace.My Answer.WebHome]][[confluencePage:id:42]][[doc:Ok2EkGOQ.WebHome]]";

        for (String testDoc : docsWithReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        XWikiDocument migrationDoc = addDoc("Migrations.Migration20", "", false);
        migrationDoc.setStringValue(MIGRATION_CLASS, "brokenLinksPages",
            "{\"xwiki:MySpace.Doc20.WebHome\":{}}");
        migrationDoc.setStringValue(MIGRATION_CLASS, INPUT_PROPERTIES,
            "{\"baseURLs\":\"http://base.url/\"}");

        wiki.saveDocument(migrationDoc, context);

        fixer.fixDocuments(
            List.of(migrationDoc.getDocumentReference()),
            null, null, null, true, false
        );

        for (String testDoc : docsWithReferenceIssues) {
            assertEquals(expected + ' ' + testDoc, getContent(testDoc));
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            assertEquals(content + ' ' + testDoc, getContent(testDoc));
        }
    }

    @Test
    void testMigrationMissingAttachments() throws ComponentLookupException, XWikiException
    {
        List<String> docsWithReferenceIssues = List.of(
            "Migrated.SpaceA.Doc11.WebHome",
            "Migrated.SpaceA.Doc12.WebHome",
            "Migrated.SpaceB.Doc11.WebHome",
            "Migrated.SpaceB.Doc12.WebHome"
        );

        List<String> docsWithoutReferenceIssues = List.of(
            "Migrated.SpaceC.Doc11.WebHome"
        );

        String content = "[[doc:MySpace.Resolved Title]] [[confluencePage:id:42]] [[http://base.url/x/2EkGOQ]]";
        String expected = "[[doc:MySpace.ResolvedTitle.WebHome]] [[doc:MyAnswer.WebHome]] "
            + "[[doc:Ok2EkGOQ.WebHome]]";

        for (String testDoc : docsWithReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        XWikiDocument migrationDoc = addDoc("Migrations.Migration30", "", false);
        migrationDoc.setStringValue(MIGRATION_CLASS, INPUT_PROPERTIES,
            "{\"baseURLs\":\"http://base.url/\",\"root\":\"space:xwiki:Migrated\"}");
        migrationDoc.setStringListValue(MIGRATION_CLASS, SPACES, List.of(SPACE_A, SPACE_B));

        wiki.saveDocument(migrationDoc, context);

        fixer.fixDocuments(
            List.of(migrationDoc.getDocumentReference()),
            null, null, null, true, false
        );

        for (String testDoc : docsWithReferenceIssues) {
            assertEquals(expected + ' ' + testDoc, getContent(testDoc));
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            assertEquals(content + ' ' + testDoc, getContent(testDoc));
        }
    }

    @Test
    void testMigrationMissingInputProperties() throws ComponentLookupException, XWikiException
    {
        List<String> docsWithReferenceIssues = List.of(
            "SpaceA.Doc11.WebHome",
            "SpaceA.Doc12.WebHome",
            "SpaceB.Doc11.WebHome",
            "SpaceB.Doc12.WebHome"
        );

        List<String> docsWithoutReferenceIssues = List.of(
            "SpaceC.Doc11.WebHome"
        );

        addDoc("SpaceA.WebHome", "", false);
        addDoc("SpaceB.WebHome", "", false);
        addDoc("SpaceC.WebHome", "", false);

        String content = "[[MySpace.My Answer]][[confluencePage:id:42]][[MySpace.@home]][[NotFound.@home]]";
        String expected = "[[doc:Migrated.MySpace.My Answer.WebHome]][[doc:MyAnswer.WebHome]]"
            + "[[doc:MySpace.WebHome]][[NotFound.@home]]";

        for (String testDoc : docsWithReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            addDoc(testDoc, content + ' ' + testDoc, false);
        }

        XWikiDocument migrationDoc = addDoc("Migrations.Migration40", "", false);
        migrationDoc.setStringListValue(MIGRATION_CLASS, SPACES, List.of(SPACE_A, SPACE_B));

        wiki.saveDocument(migrationDoc, context);

        fixer.fixDocuments(
            List.of(migrationDoc.getDocumentReference()),
            null, null, null, true, false
        );

        for (String testDoc : docsWithReferenceIssues) {
            assertEquals(expected + ' ' + testDoc, getContent(testDoc));
        }

        for (String testDoc : docsWithoutReferenceIssues) {
            assertEquals(content + ' ' + testDoc, getContent(testDoc));
        }
    }
}
