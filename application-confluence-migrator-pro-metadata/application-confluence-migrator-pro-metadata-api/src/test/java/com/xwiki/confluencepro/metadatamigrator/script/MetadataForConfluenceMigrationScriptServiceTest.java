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

package com.xwiki.confluencepro.metadatamigrator.script;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageTitleResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.PropertyInterface;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.DateClass;
import com.xpn.xwiki.objects.classes.StaticListClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ComponentTest
@OldcoreTest
@ReferenceComponentList
class MetadataForConfluenceMigrationScriptServiceTest
{
    private static final String INCLUDED_SPACES = "includedSpaces";
    private static final String SPACES = "spaces";
    private static final String REQUIRED_FIELDS = "requiredFields";
    private static final String DEFAULT_VALUES = "defaultValues";
    private static final String WYSIWYG = "wysiwyg";
    private static final String FULLYRENDEREDTEXT = "fullyrenderedtext";
    private static final String XWIKI = "xwiki";
    private static final String CODE = "Code";
    private static final String METADATA_PRO = "MetadataPro";
    private static final List<String> METADATA_PRO_CODE = List.of(METADATA_PRO, CODE);
    private static final String CONFLUENCE_MIGRATOR_PRO = "ConfluenceMigratorPro";
    private static final String DOT_JSON = ".json";
    private static final String PACKAGE = "package";
    private static final String EXECUTED = "executed";
    private static final String TITLE_TEMPLATE = "titleTemplate";
    private static final String REFERENCE_TEMPLATE1 = "referenceTemplate";
    private static final String REFERENCE_TEMPLATE = "MetadataPro.Sets.${spaceKey}_${setName}";
    private static final String SET_TITLE_TEMPLATE = "${setName}";
    private static final String TEST_SPACE = "TestSpace";
    private static final String WEB_HOME = "WebHome";
    private static final String TEST_PAGE = "Test page";
    private static final String PAGE_WITH_NULLS = "Page with nulls";
    private static final List<String> SETS = List.of(METADATA_PRO, "Sets");
    private static final DocumentReference MYSET_REF = new DocumentReference(XWIKI, SETS, "TestSpace_myset");
    private static final DocumentReference MYSET2_REF = new DocumentReference(XWIKI,SETS, "TestSpace_myset2");
    private static final DocumentReference NEWSET_REF = new DocumentReference(XWIKI,SETS, "TestSpace_newset");

    private static final EntityReference TEST_SPACE_REF = new EntityReference(TEST_SPACE,
        EntityType.SPACE, new WikiReference(XWIKI));

    private static final DocumentReference SET_CLASS = new DocumentReference(XWIKI, METADATA_PRO_CODE,
        "MetadataSetClass");

    private static final DocumentReference MIGRATION_CLASS = new DocumentReference(XWIKI, List.of(
        CONFLUENCE_MIGRATOR_PRO,
        "Metadata",
        CODE),
        "MigrationClass"
    );

    private static final DocumentReference PACKAGE_STORE_REF = new DocumentReference(XWIKI, List.of(
        CONFLUENCE_MIGRATOR_PRO,
        "Metadata",
        CODE),
        "PackagesStore"
    );

    private static final String ESCAPED_DESCRIPTION = "~T~h~i~s~ ~i~s~ ~a~ ~*~*~d~e~s~c~r~i~p~t~i~o~n~*~*~.";
    private static final String DESCRIPTION = "description";
    private static final String DEPARTMENT = "department";
    private static final String COLOR = "color";
    private static final String RED = "Red";
    private static final String GREEN = "Green";
    private static final String BLUE = "Blue";
    private static final String MODIFICATIONDATE = "modificationdate";
    private static final String SELECT = "select";
    private static final String CHECKBOX = "CHECKBOX";
    private static final String AIN = "Ain";
    private static final String MOSELLE = "Moselle";
    private static final String ISERE = "Isère";

    @InjectMockComponents
    MetadataForConfluenceMigrationScriptService migrator;

    @MockComponent
    UserReferenceResolver<CurrentUserReference> currentUserReferenceUserReferenceResolver;

    @MockComponent
    ObservationManager observationManager;

    @InjectMockitoOldcore
    private MockitoOldcore oldCore;

    @MockComponent
    private ConfluencePageTitleResolver confluencePageTitleResolver;

    @MockComponent
    private ConfluenceSpaceKeyResolver confluenceSpaceKeyResolver;

    @BeforeEach
    void before() throws XWikiException
    {
        Logger logger = (Logger) LoggerFactory.getLogger(MetadataForConfluenceMigrationScriptService.class);
        logger.setLevel(Level.ERROR);
        prepareRights();
    }

    @Test
    void testMigration1() throws IOException, XWikiException, ConfluenceResolverException, ParseException
    {
        String packageName = "metadata";

        prepareConfluencePages();
        prepareSetDocuments();
        migrate(packageName);

        XWikiContext xcontext = oldCore.getXWikiContext();
        XWiki wiki = xcontext.getWiki();
        checkMigrationObject(packageName, wiki, xcontext);
        checkMigratedObjects(wiki, xcontext);
        checkMigratedSets(wiki, xcontext);
    }

    @Test
    void testMigrationNoValuesDoesntCrash() {
        assertDoesNotThrow(() -> migrate("novalues"));
    }

    private static void checkMigrationObject(String packageName, XWiki wiki, XWikiContext xcontext) throws XWikiException
    {
        DocumentReference migrationDocRef = getMigrationDocRef(packageName);
        XWikiDocument migrationDoc = wiki.getDocument(migrationDocRef, xcontext).clone();
        BaseObject migrationObject = migrationDoc.getXObject(MIGRATION_CLASS);
        assertTrue(wiki.exists(migrationDocRef, xcontext));
        assertNotNull(migrationObject);
        assertEquals(1, migrationObject.getIntValue(EXECUTED));
    }

    private static void checkMigratedSets(XWiki wiki, XWikiContext xcontext)
        throws XWikiException, JsonProcessingException
    {
        checkMyset(wiki, xcontext);
        checkMyset2(wiki, xcontext);
        checkNewset(wiki, xcontext);
    }

    private static void checkMyset(XWiki wiki, XWikiContext xcontext) throws XWikiException, JsonProcessingException
    {
        XWikiDocument mysetDoc = wiki.getDocument(MYSET_REF, xcontext);
        BaseObject setObj = mysetDoc.getXObject(SET_CLASS);
        assertNotNull(setObj);
        assertEquals(List.of(DEPARTMENT), setObj.getListValue(REQUIRED_FIELDS));
        assertEquals(
            Map.of(COLOR, List.of(GREEN)),
            new ObjectMapper()
                .readValue(setObj.getLargeStringValue(DEFAULT_VALUES), Map.class)
        );

        BaseClass setClass = mysetDoc.getXClass();

        PropertyInterface department = setClass.getField(DEPARTMENT);
        assertInstanceOf(StaticListClass.class, department);
        assertEquals(List.of(AIN, MOSELLE, ISERE), ((StaticListClass) department).getList(xcontext));
        assertEquals(SELECT, ((StaticListClass) department).getDisplayType());
        assertFalse(((StaticListClass) department).isMultiSelect());
        assertEquals(1, ((StaticListClass) department).getSize());

        PropertyInterface color = setClass.getField(COLOR);
        assertInstanceOf(StaticListClass.class, color);
        assertEquals(List.of(RED, GREEN, BLUE), ((StaticListClass) color).getList(xcontext));
        assertEquals(CHECKBOX, ((StaticListClass) color).getDisplayType());
        assertTrue(((StaticListClass) color).isMultiSelect());

        PropertyInterface description = setClass.getField(DESCRIPTION);
        assertInstanceOf(TextAreaClass.class, description);
        assertEquals(WYSIWYG, ((TextAreaClass) description).getEditor());
        assertEquals(FULLYRENDEREDTEXT, ((TextAreaClass) description).getContentType());
    }

    private static void checkMyset2(XWiki wiki, XWikiContext xcontext) throws XWikiException, JsonProcessingException
    {
        XWikiDocument myset2Doc = wiki.getDocument(MYSET2_REF, xcontext);
        BaseObject setObj = myset2Doc.getXObject(SET_CLASS);
        assertNotNull(setObj);
        assertEquals(List.of(DESCRIPTION), setObj.getListValue(REQUIRED_FIELDS));
        assertEquals(
            Map.of(),
            new ObjectMapper()
                .readValue(setObj.getLargeStringValue(DEFAULT_VALUES), Map.class)
        );

        BaseClass setClass = myset2Doc.getXClass();
        PropertyInterface description = setClass.getField(DESCRIPTION);
        assertInstanceOf(TextAreaClass.class, description);

        // The already existing description field was changed to WYSIWYG
        assertEquals(WYSIWYG, ((TextAreaClass) description).getEditor());
        assertEquals(FULLYRENDEREDTEXT, ((TextAreaClass) description).getContentType());

        PropertyInterface modificationdate = setClass.getField(MODIFICATIONDATE);
        assertInstanceOf(DateClass.class, modificationdate);
    }

    private static void checkNewset(XWiki wiki, XWikiContext xcontext) throws XWikiException
    {
        XWikiDocument newsetDoc = wiki.getDocument(NEWSET_REF, xcontext);
        BaseObject setObj = newsetDoc.getXObject(SET_CLASS);
        assertNotNull(setObj);
        // we can't check fields of setObj here

        BaseClass setClass = newsetDoc.getXClass();
        PropertyInterface description = setClass.getField(DESCRIPTION);
        assertNotNull(description);
    }

    private void prepareSetDocuments() throws XWikiException
    {
        // We don't have the Metadata instance set class, so we need to populate metadata set instances so XWiki know
        // how to fill the fields during the migration.
        // Additionally, we will add two fields to myset2 to check that they don't disappear, and that the required flag
        // is correctly set anyway.
        // We will test that non-existing set are indeed created, but we won't be able to check whether they are
        // fully migrated correctly

        prepareSetPage(MYSET_REF);
        XWikiDocument myset2Doc = prepareSetPage(MYSET2_REF);

        // We will check that existing fields are not affected but still allow the required field to be set
        myset2Doc.getXClass().addTextAreaField(DESCRIPTION, DESCRIPTION, 20, 10, TextAreaClass.EditorType.PURE_TEXT,
            TextAreaClass.ContentType.PURE_TEXT);
        XWikiContext xcontext = oldCore.getXWikiContext();
        XWiki wiki = xcontext.getWiki();
        wiki.saveDocument(myset2Doc, xcontext);
    }

    private XWikiDocument prepareSetPage(DocumentReference setRef) throws XWikiException
    {
        XWikiContext xcontext = oldCore.getXWikiContext();
        XWiki wiki = xcontext.getWiki();
        XWikiDocument setDoc = wiki.getDocument(setRef, xcontext);
        BaseObject setObject = setDoc.getXObject(SET_CLASS, true, xcontext);
        setObject.setLargeStringValue(DEFAULT_VALUES, "");
        setObject.setStringListValue(REQUIRED_FIELDS, List.of());
        setObject.setStringListValue(INCLUDED_SPACES, List.of());
        setObject.setStringListValue(SPACES, List.of());
        wiki.saveDocument(setDoc, xcontext);
        return setDoc;
    }

    private static void checkMigratedObjects(XWiki wiki, XWikiContext xcontext) throws XWikiException, ParseException
    {
        checkPageWithNulls(wiki, xcontext);
        checkTestPage(wiki, xcontext);
    }

    private static void checkPageWithNulls(XWiki wiki, XWikiContext xcontext) throws XWikiException
    {
        XWikiDocument pageWithNulls = wiki.getDocument(getConfluencePage(PAGE_WITH_NULLS), xcontext);
        assertNotNull(pageWithNulls.getXObject(MYSET_REF));
    }

    private static void checkTestPage(XWiki wiki, XWikiContext xcontext) throws XWikiException, ParseException
    {
        XWikiDocument testPageDoc = wiki.getDocument(getConfluencePage(TEST_PAGE), xcontext);
        BaseObject mysetObj = testPageDoc.getXObject(MYSET_REF);
        assertNotNull(mysetObj);
        List<?> color = mysetObj.getListValue(COLOR);
        assertTrue(color.contains(GREEN));
        assertTrue(color.contains(BLUE));
        assertFalse(color.contains(RED));
        assertEquals(MOSELLE, mysetObj.getStringValue(DEPARTMENT));
        assertEquals(ESCAPED_DESCRIPTION, mysetObj.getLargeStringValue(DESCRIPTION));
        BaseObject myset2Obj = testPageDoc.getXObject(MYSET2_REF);
        assertNotNull(myset2Obj);
        assertEquals(ESCAPED_DESCRIPTION, myset2Obj.getLargeStringValue(DESCRIPTION));
        assertEquals(new SimpleDateFormat("yyyy-MM-dd").parse("2025-10-08"),
            myset2Obj.getDateValue(MODIFICATIONDATE));
    }

    private void prepareConfluencePages() throws ConfluenceResolverException, XWikiException
    {
        when(confluenceSpaceKeyResolver.getSpaceByKey(TEST_SPACE)).thenReturn(TEST_SPACE_REF);
        prepareConfluencePage(TEST_PAGE);
        prepareConfluencePage(PAGE_WITH_NULLS);
    }

    private void prepareConfluencePage(String title) throws ConfluenceResolverException, XWikiException
    {
        EntityReference confluencePage = getConfluencePage(title);
        XWikiContext xcontext = oldCore.getXWikiContext();
        XWiki wiki = xcontext.getWiki();
        wiki.saveDocument(wiki.getDocument(confluencePage, xcontext), xcontext);
        when(confluencePageTitleResolver.getDocumentByTitle(TEST_SPACE, title)).thenReturn(confluencePage);
    }

    private static EntityReference getConfluencePage(String title)
    {
        return new EntityReference(
            WEB_HOME,
            EntityType.DOCUMENT,
            new EntityReference(title, EntityType.SPACE, TEST_SPACE_REF)
        );
    }

    private void migrate(String packageName) throws IOException, XWikiException
    {
        XWikiContext xcontext = oldCore.getXWikiContext();
        XWikiDocument migrationDoc = prepareMigrationDoc(packageName);
        migrator.run(new Document(migrationDoc, xcontext), REFERENCE_TEMPLATE,
            SET_TITLE_TEMPLATE);
    }

    private void prepareRights() throws XWikiException
    {
        // We take programming rights to manipulate document classes and save stuff
        when(oldCore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);
        when(oldCore.getMockRightService().hasProgrammingRights(any(), any())).thenReturn(true);
        when(oldCore.getMockRightService().hasAccessLevel(any(), any(), any(), any())).thenReturn(true);
        when(oldCore.getMockRightService().checkAccess(any(), any(), any())).thenReturn(true);
        when(currentUserReferenceUserReferenceResolver.resolve(any(), any())).thenReturn((UserReference) () -> true);
    }

    private XWikiDocument prepareMigrationDoc(String packageName) throws IOException, XWikiException
    {
        initPackageStore(packageName);
        XWikiContext xcontext = oldCore.getXWikiContext();
        XWiki wiki = xcontext.getWiki();
        XWikiDocument migrationDoc = wiki.getDocument(getMigrationDocRef(packageName), xcontext);
        BaseObject migrationObject = migrationDoc.getXObject(MIGRATION_CLASS, true, xcontext);
        migrationObject.setStringValue(PACKAGE, packageName + DOT_JSON);
        migrationObject.setIntValue(EXECUTED, 0);
        migrationObject.setStringValue(TITLE_TEMPLATE, SET_TITLE_TEMPLATE);
        migrationObject.setStringValue(REFERENCE_TEMPLATE1, REFERENCE_TEMPLATE);
        migrationObject.setStringListValue(SPACES, List.of());
        // we don't save the document here. It's not needed and we want to test that the migration does it.
        return migrationDoc;
    }

    private static DocumentReference getMigrationDocRef(String packageName)
    {
        return new DocumentReference(XWIKI, "Migrations", packageName);
    }

    private void initPackageStore(String packageName) throws XWikiException, IOException
    {
        XWikiContext xcontext = oldCore.getXWikiContext();
        XWiki wiki = xcontext.getWiki();
        XWikiDocument packageStore = wiki.getDocument(PACKAGE_STORE_REF, xcontext);
        String json = packageName + DOT_JSON;
        packageStore.setAttachment(json, getClass().getClassLoader().getResourceAsStream(json), xcontext);
        wiki.saveDocument(packageStore, xcontext);
    }
}