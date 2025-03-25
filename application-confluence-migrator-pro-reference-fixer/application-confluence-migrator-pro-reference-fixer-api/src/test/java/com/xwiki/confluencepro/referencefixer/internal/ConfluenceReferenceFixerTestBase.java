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
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.XWikiDefaultURLFactory;
import com.xpn.xwiki.web.XWikiURLFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrDocument;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfluenceReferenceFixerTestBase
{
    static final String CONTENT = "[[confluencePage:id:42]]";
    static final String CONVERTED = "[[doc:MyAnswer.WebHome]]";
    static final WikiReference WIKI_REFERENCE = new WikiReference("xwiki");
    static final String MY_SPACE_STR = "MySpace";
    static final EntityReference MIGRATION_CLASS = new EntityReference("MigrationClass", EntityType.DOCUMENT,
        new EntityReference("Code", EntityType.SPACE,
            new EntityReference("ConfluenceMigratorPro", EntityType.SPACE, WIKI_REFERENCE)));
    static final EntityReference MY_SPACE = new EntityReference(MY_SPACE_STR, EntityType.SPACE, WIKI_REFERENCE);
    static final String WEB_HOME = "WebHome";
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
            if (id == 43 || id == 522012960621L) {
                // That big number is "/x/badlink" converted
                return null;
            }
            if (id == 42) {
                return new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                    new EntityReference("MyAnswer", EntityType.SPACE, WIKI_REFERENCE));
            }
            if (id == 956713432) {
                return new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                    new EntityReference("Ok2EkGOQ", EntityType.SPACE, WIKI_REFERENCE));
            }

            return new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                new EntityReference("ID" + id, EntityType.SPACE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE)));
        });
        when(confluencePageTitleResolver.getDocumentByTitle("Sandbox", WEB_HOME)).thenReturn(
            new EntityReference("ShouldNotHaveTriedResolvingSandbox", EntityType.DOCUMENT, WIKI_REFERENCE)
        );
        when(confluencePageTitleResolver.getDocumentByTitle(MY_SPACE_STR, "Resolved Title")).thenReturn(
            new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                new EntityReference("ResolvedTitle", EntityType.SPACE, MY_SPACE))
        );
        when(confluencePageTitleResolver.getDocumentByTitle(MY_SPACE_STR, "My Answer")).thenReturn(
            new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                new EntityReference("My Answer", EntityType.SPACE, MY_SPACE.replaceParent(WIKI_REFERENCE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE))))
        );
        when(confluencePageTitleResolver.getDocumentByTitle(MY_SPACE_STR, "My Other Answer")).thenReturn(
            new EntityReference(WEB_HOME, EntityType.DOCUMENT,
                new EntityReference("My Other Answer", EntityType.SPACE, MY_SPACE.replaceParent(WIKI_REFERENCE,
                    new EntityReference(MIGRATED, EntityType.SPACE, WIKI_REFERENCE))))
        );
        when(confluenceSpaceKeyResolver.getSpaceByKey(MY_SPACE_STR)).thenReturn(MY_SPACE);
        when(confluenceSpaceKeyResolver.getSpaceByKey("SpaceA")).thenReturn(new EntityReference("SpaceA",
            EntityType.SPACE, WIKI_REFERENCE));
        when(confluenceSpaceKeyResolver.getSpaceByKey("SpaceB")).thenReturn(new EntityReference("SpaceB",
            EntityType.SPACE, WIKI_REFERENCE));
    }

    private String maybeQueryString(String q)
    {
        if (StringUtils.isEmpty(q)) {
            return "";
        }

        if (q.startsWith("?")) {
            return q;
        }

        return "?" + q;
    }

    private String maybeAnchor(String anchor)
    {
        if (StringUtils.isEmpty(anchor)) {
            return "";
        }

        if (anchor.startsWith("#")) {
            return anchor;
        }

        return "#" + anchor;
    }

    @BeforeEach
    void beforeEach() throws ComponentLookupException, QueryException
    {
        Provider<XWikiContext> contextProvider = componentManager.getInstance(XWikiContext.TYPE_PROVIDER);
        context = contextProvider.get();
        wiki = context.getWiki();
        XWikiURLFactory urlFactory = mock(XWikiURLFactory.class);
        when(urlFactory.createURL(any(), any(), any())).thenAnswer(i ->
            urlFactory.createURL(i.getArgument(0), i.getArgument(1), "view", i.getArgument(2)));
        when(urlFactory.createURL(any(), any(), any(), any())).thenAnswer(i ->
            urlFactory.createURL(i.getArgument(0), i.getArgument(1), i.getArgument(2), false, i.getArgument(3)));
        when(urlFactory.createURL(any(), any(), any(), any(), any(), any())).thenAnswer(i ->
            urlFactory.createURL(i.getArgument(0), i.getArgument(1), i.getArgument(2), "", "",
                "xwiki", i.getArgument(3)));
        when(urlFactory.createURL(any(), any(), any(), anyBoolean(), any())).thenAnswer(i ->
            urlFactory.createURL(i.getArgument(0), i.getArgument(1), i.getArgument(2), "", "xwiki", i.getArgument(3)));
        when(urlFactory.createURL(any(), any(), any(), any(), any(), any(), any())).thenAnswer(
            i ->  new URL("http://localhost:8080/xwiki/wiki/" + i.getArgument(5) + "/" + i.getArgument(2) + "/" + i.getArgument(0) +
                "/" + i.getArgument(1) +
                maybeQueryString(i.getArgument(3)) + maybeAnchor(i.getArgument(4))));
        when(urlFactory.getURL(any(), any())).thenAnswer(i -> i.getArgument(0).toString());

        context.setURLFactory(urlFactory);

        docs = new ArrayList<>();

        AtomicReference<List<Object>> docRefsStr = new AtomicReference<>();
        Query hqlQuery = mock(Query.class);
        when(queryManager.createQuery(anyString(), eq("hql"))).thenReturn(hqlQuery);
        when(queryManager.createQuery(anyString(), eq("xwql"))).thenReturn(hqlQuery);
        when(hqlQuery.setWiki(anyString())).thenReturn(hqlQuery);
        when(hqlQuery.bindValue(anyString(), anyString())).thenReturn(hqlQuery);
        when(hqlQuery.bindValue(eq("space"), anyString())).thenAnswer(i -> {
            EntityReferenceSerializer<String> serializer = componentManager.getInstance(
                new DefaultParameterizedType(null, EntityReferenceSerializer.class, String.class));

            docRefsStr.set(docs.stream()
                .map(d -> serializer.serialize(d.getDocumentReference()))
                .filter(d -> d.startsWith("xwiki:" + i.getArgument(1)))
                .map(d -> StringUtils.removeStart(d, "xwiki:"))
                .collect(Collectors.toList()));
            return hqlQuery;
        });
        when(hqlQuery.execute()).thenAnswer(i -> docRefsStr.get());
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

    XWikiDocument addDoc(String fullName, String content, boolean addAComment)
        throws ComponentLookupException, XWikiException
    {
        XWikiDocument doc = newExistingDoc(fullName);
        doc.setContent(content);
        if (addAComment) {
            BaseObject comment = doc.newXObject(new EntityReference("XWikiComments", EntityType.DOCUMENT,
                new EntityReference("XWiki", EntityType.SPACE)), context);
            comment.setLargeStringValue("comment", content);
        }
        wiki.saveDocument(doc, context);
        docs.add(doc);
        return doc;
    }
}
