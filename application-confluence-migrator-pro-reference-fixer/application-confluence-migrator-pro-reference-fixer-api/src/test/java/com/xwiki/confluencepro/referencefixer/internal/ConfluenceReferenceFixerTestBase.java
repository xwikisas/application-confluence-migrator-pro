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

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageIdResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageTitleResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.search.solr.Solr;
import org.xwiki.test.annotation.AfterComponent;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import javax.inject.Named;
import javax.inject.Provider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfluenceReferenceFixerTestBase
{
    static final String CONTENT = "[[confluencePage:id:42]]";
    static final String CONVERTED = "[[doc:xwiki:MyAnswer.WebHome]]";
    static final WikiReference WIKI_REFERENCE = new WikiReference("xwiki");
    static final String MY_SPACE_STR = "MySpace";
    static final EntityReference MIGRATION_CLASS = new EntityReference("MigrationClass", EntityType.DOCUMENT,
        new EntityReference("Code", EntityType.SPACE,
            new EntityReference("ConfluenceMigratorPro", EntityType.SPACE, WIKI_REFERENCE)));
    static final EntityReference MY_SPACE = new EntityReference(MY_SPACE_STR, EntityType.SPACE, WIKI_REFERENCE);
    static final String WEB_HOME = "WebHome";
    static final String FULLNAME = "fullname";
    static final String MIGRATED = "Migrated";
    static final String CONFLUENCE_REF_WARNINGS_JSON = "confluenceRefWarnings.json";

    protected XWiki wiki;

    protected XWikiContext context;

    protected List<XWikiDocument> docs = new ArrayList<>();

    @InjectMockComponents
    protected ConfluenceReferenceFixer fixer;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private ConfluencePageIdResolver confluencePageIdResolver;

    @MockComponent
    private ConfluencePageTitleResolver confluencePageTitleResolver;

    @MockComponent
    private ConfluenceSpaceKeyResolver confluenceSpaceKeyResolver;

    @MockComponent
    @Named("embedded")
    private Solr solr;

    @MockComponent
    private EntityReferenceResolver<SolrDocument> solrEntityReferenceResolver;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @AfterComponent
    void setup() throws ConfluenceResolverException
    {
        when(confluencePageIdResolver.getDocumentById(anyLong())).thenAnswer(i -> {
            long id = i.getArgument(0);
            if (id == 42) {
                return new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                    new EntityReference("MyAnswer", EntityType.SPACE, WIKI_REFERENCE));
            }
            if (id == 956713432) {
                return new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                    new EntityReference("Ok2EkGOQ", EntityType.SPACE, WIKI_REFERENCE));
            }
            return null;
        });
        when(confluencePageTitleResolver.getDocumentByTitle("Sandbox", WEB_HOME)).thenReturn(
            new EntityReference("ShouldNotHaveTriedResolvingSandbox", EntityType.DOCUMENT, WIKI_REFERENCE)
        );
        when(confluencePageTitleResolver.getDocumentByTitle(MY_SPACE_STR, "Resolved Title")).thenReturn(
            new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                new EntityReference("ResolvedTitle", EntityType.SPACE, MY_SPACE))
        );
        when(confluenceSpaceKeyResolver.getSpaceByKey(MY_SPACE_STR)).thenReturn(MY_SPACE);
        when(solrEntityReferenceResolver.resolve(any(), eq(EntityType.DOCUMENT))).thenAnswer(i ->
        {
            EntityReferenceResolver<String> resolver = componentManager.getInstance(
                new DefaultParameterizedType(null, EntityReferenceResolver.class, String.class));
            SolrDocument doc = i.getArgument(0);
            return resolver.resolve((String) doc.get(FULLNAME), EntityType.DOCUMENT);
        });
    }
    @BeforeEach
    void beforeEach() throws ComponentLookupException, XWikiException, QueryException
    {
        Provider<XWikiContext> contextProvider = componentManager.getInstance(XWikiContext.TYPE_PROVIDER);
        context = contextProvider.get();
        wiki = context.getWiki();

        docs = new ArrayList<>();

        AtomicReference<List<Object>> docRefsStr = new AtomicReference<>();
        AtomicReference<SolrDocumentList> solrDocs = new AtomicReference<>();

        QueryResponse solrQueryResponse = mock(QueryResponse.class);
        when(solrQueryResponse.getResults()).thenAnswer(i -> solrDocs.get());

        Query hqlQuery = mock(Query.class);
        when(queryManager.createQuery(anyString(), eq("hql"))).thenReturn(hqlQuery);
        when(hqlQuery.setWiki(anyString())).thenReturn(hqlQuery);
        when(hqlQuery.bindValue(anyString(), anyString())).thenReturn(hqlQuery);
        when(hqlQuery.bindValue(eq("space"), anyString())).thenAnswer(i -> {
            EntityReferenceSerializer<String> serializer = componentManager.getInstance(
                new DefaultParameterizedType(null, EntityReferenceSerializer.class, String.class));

            docRefsStr.set(docs.stream()
                .map(d -> serializer.serialize(d.getDocumentReference()))
                .filter(d -> d.startsWith("xwiki:" + i.getArgument(1)))
                .collect(Collectors.toList()));
            return hqlQuery;
        });
        when(hqlQuery.execute()).thenAnswer(i -> docRefsStr.get());

        Query solrQuery = mock(Query.class);
        when(queryManager.createQuery(anyString(), eq("solr"))).thenAnswer(i -> {
            SolrDocumentList newValue = new SolrDocumentList();
            if (i.getArgument(0).equals(
                "(name:My\\ Answer or title:My\\ Answer) and (fullname:MySpace.* or fullname:*.MySpace.*))")
            ) {
                newValue.add(new SolrDocument(Map.of(FULLNAME, "Migrated.MySpace.My Answer.WebHome")));
            } else if (i.getArgument(0).equals(
                "(name:My\\ Other\\ Answer or title:My\\ Other\\ Answer) "
                    + "and (fullname:MySpace.* or fullname:*.MySpace.*))")
            ) {
                newValue.add(new SolrDocument(Map.of(FULLNAME, "Migrated.MySpace.My Other Answer.WebHome")));
            } else if (i.getArgument(0).equals("fullname:SpaceA.WebHome or fullname:*.SpaceA.WebHome")) {
                newValue.add(new SolrDocument(Map.of(FULLNAME, "SpaceA.WebHome")));
            } else if (i.getArgument(0).equals("fullname:SpaceB.WebHome or fullname:*.SpaceB.WebHome")) {
                newValue.add(new SolrDocument(Map.of(FULLNAME, "SpaceB.WebHome")));
            }

            solrDocs.set(newValue);
            return solrQuery;
        });
        when(solrQuery.bindValue(anyString(), anyString())).thenReturn(solrQuery);
        when(solrQuery.setLimit(anyInt())).thenReturn(solrQuery);
        when(solrQuery.execute()).thenReturn(Collections.singletonList(solrQueryResponse));
        wiki.saveDocument(newExistingDoc("MyBlogSpace.Blog.Hello world"), context);
        wiki.saveDocument(newExistingDoc("MyBlogSpace.Blog.Actually a regular doc.WebHome"), context);
    }

    XWikiDocument newExistingDoc(String fullName) throws ComponentLookupException
    {
        EntityReferenceResolver<String> resolver = componentManager.getInstance(
            new DefaultParameterizedType(null, EntityReferenceResolver.class, String.class));
        XWikiDocument d = new XWikiDocument(new DocumentReference(resolver.resolve(fullName, EntityType.DOCUMENT)));
        d.setSyntax(Syntax.XWIKI_2_1);
        d.setNew(false);
        return d;
    }

    String getExpected(EntityReference doc) throws ComponentLookupException, IOException
    {
        EntityReferenceSerializer<String> compactWiki = componentManager.getInstance(
            new DefaultParameterizedType(null, EntityReferenceSerializer.class, String.class),
            "compactwiki");
        String fullName = compactWiki.serialize(doc, WIKI_REFERENCE);
        File contentFile = new File("src/test/resources/documents/" + fullName + "/expected.txt");
        Path contentFilePath = contentFile.toPath();
        return Files.readString(contentFilePath, StandardCharsets.UTF_8);
    }

    String getContent(EntityReference documentReference) throws XWikiException
    {
        return wiki.getDocument(documentReference, context).getContent().trim();
    }

    String getContent(String documentReference) throws XWikiException, ComponentLookupException
    {
        EntityReference resolved = getEntityReference(documentReference);
        return getContent(resolved);
    }

    EntityReference getEntityReference(String documentReference) throws ComponentLookupException
    {
        EntityReferenceResolver<String> resolver = componentManager.getInstance(
            new DefaultParameterizedType(null, EntityReferenceResolver.class, String.class));
        return resolver.resolve(documentReference, EntityType.DOCUMENT);
    }

    XWikiDocument addDoc(String fullName, String content) throws ComponentLookupException, XWikiException
    {
        XWikiDocument doc = newExistingDoc(fullName);
        doc.setContent(content);
        wiki.saveDocument(doc, context);
        docs.add(doc);
        return doc;
    }
}
