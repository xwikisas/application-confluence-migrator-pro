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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentContent;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.confluencepro.referencefixer.BrokenRefType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageTitleResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceResolver;
import org.xwiki.contrib.confluence.resolvers.resource.ConfluenceResourceReferenceResolver;
import org.xwiki.contrib.confluence.resolvers.resource.ConfluenceResourceReferenceType;
import org.xwiki.contrib.confluence.urlmapping.ConfluenceURLMapper;
import org.xwiki.contrib.urlmapping.DefaultURLMappingMatch;
import org.xwiki.contrib.urlmapping.URLMappingResult;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.model.validation.EntityNameValidationManager;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.block.match.OrBlockMatcher;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.parser.ResourceReferenceTypeParser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.resource.entity.EntityResourceReference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Confluence Reference Fixer.
 * @since 1.29.0
 * @version $Id$
 */
@Component (roles = ConfluenceReferenceFixer.class)
@Singleton
public class ConfluenceReferenceFixer
{

    private static final String DOCUMENT = "document";
    private static final Pattern BROKEN_LINK_PATTERN = Pattern.compile(
        "(?<space>[a-zA-Z0-9_~-]+)"
            + "\\."
            + "(?<nameValidatedTitle>[\\s\\S]+?)"
            + "(?:(?<!\\\\)@"
            + "(?<attachment>[\\s\\S]+)"
            + ")?"
    );

    private static final TypeReference<Map<String, List<Map<String, Object>>>> CONFLUENCE_REF_WARNINGS_TYPE_REF =
        new TypeReference<Map<String, List<Map<String, Object>>>>() { };
    private static final TypeReference<Map<String, Object>> CONFLUENCE_BROKEN_LINK_PAGES_REF =
        new TypeReference<Map<String, Object>>() { };
    private static final TypeReference<Map<String, Object>> INPUT_PROPERTIES_TYPE_REF =
        new TypeReference<Map<String, Object>>() { };
    private static final String WEB_HOME = "WebHome";

    private static final Marker UPDATED_MARKER = MarkerFactory.getMarker("confluencereferencefixer.updated");
    private static final Marker UNCHANGED_MARKER = MarkerFactory.getMarker("confluencereferencefixer.unchanged");
    private static final Marker FAILED_REFERENCE_CONVERSION_MARKER =
        MarkerFactory.getMarker("confluencereferencefixer.failedrefconversion");
    private static final Marker SUCCESSFUL_REFERENCE_CONVERSION_MARKER =
        MarkerFactory.getMarker("confluencereferencefixer.successfulrefconversion");
    private static final Marker CHANGED_AT_PARSE_TIME_MARKER
        = MarkerFactory.getMarker("confluencereferencefixer.changedatparsetime");
    private static final String SPACE = "space";
    private static final String UPDATE_PARAM_LOG = "Document [{}]: Updating macro [{}]'s parameter [{}]: [{}] -> [{}]";
    private static final String CONFLUENCE = "confluence";
    private static final String EXCEPTION_WHILE_RESOLVING
        = "Document [{}]: Failed to resolve [{}] because of an exception";
    private static final String DOT_WEB_HOME = ".WebHome";
    private static final List<String> ALLOWED_BROKEN_LINK_MACROS = List.of("include",
        "display",
        "locationsearch",
        "children",
        "confluence_children");
    private static final String SELF = "@self";

    private static final String ATTACH = "attach:";

    private static final String DOCUMENT_COL = "document:";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private QueryManager queryManager;

    @Inject
    private ConfluencePageTitleResolver pageTitleResolver;

    @Inject
    private ConfluenceSpaceKeyResolver spaceKeyResolver;

    @Inject
    private ConfluenceSpaceResolver spaceResolver;

    @Inject
    private EntityReferenceResolver<String> resolver;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private ComponentManager componentManager;

    @Inject
    private Logger logger;

    @Inject
    private ConfluenceResourceReferenceResolver confluenceResourceReferenceResolver;

    @Inject
    private Provider<EntityNameValidationManager> entityNameValidationManagerProvider;

    @Inject
    private JobProgressManager progressManager;

    /**
     * Fix broken references in all documents of the given space.
     * @param baseURLs the baseURLs to use when fixing absolute links. If not provided, will be guessed from migration
     *                 documents.
     * @param migrationReferences the migration documents
     * @param spaceReferences the spaces in which to fix the references
     * @param brokenRefType the type of the broken references
     * @param updateInPlace whether to update migrated documents with the fixed references in place instead of
     *                      creating a new revision.
     * @param dryRun true to simulate only (the fixed documents will not be saved)
     * @return the statistics of the reference fixing sessions
     */
    public Stats fixDocuments(List<EntityReference> migrationReferences, List<EntityReference> spaceReferences,
        String[] baseURLs, BrokenRefType brokenRefType, boolean updateInPlace, boolean dryRun)
    {
        logConfluenceReferenceParserPresence();
        Stats s = new Stats();
        int steps = ((migrationReferences == null ? 0 : migrationReferences.size())
                + (spaceReferences == null ? 0 : spaceReferences.size()));
        if (steps == 0) {
            logger.warn("There is nothing to fix");
            return s;
        }
        progressManager.pushLevelProgress(steps, this);
        fixDocumentsOfMigrations(s, migrationReferences, baseURLs, updateInPlace, dryRun);
        BrokenRefType b = brokenRefType == null ? BrokenRefType.UNKNOWN : brokenRefType;
        fixDocumentsOfSpaces(s, spaceReferences, baseURLs, b, updateInPlace, dryRun);
        progressManager.popLevelProgress(this);
        return s;
    }

    private void fixDocumentsOfSpaces(Stats s, List<EntityReference> spaceReferences, String[] baseURLs,
        BrokenRefType brokenRefType, boolean updateInPlace, boolean dryRun)
    {
        if (CollectionUtils.isNotEmpty(spaceReferences)) {
            int size = spaceReferences.size();
            int n = 0;
            for (EntityReference spaceReference : spaceReferences) {
                progressManager.startStep(this);
                logger.info("Browsing documents of space [{}] ({}/{})", spaceReference, ++n, size);
                fixDocumentsInSpace(s, spaceReference, baseURLs, brokenRefType, updateInPlace, dryRun);
                progressManager.endStep(this);
            }
        }
    }

    private void fixDocumentsOfMigrations(Stats s, List<EntityReference> migrationReferences, String[] baseURLs,
        boolean updateInPlace, boolean dryRun)
    {
        if (CollectionUtils.isEmpty(migrationReferences)) {
            logger.warn("There are no migrations to fix");
            return;
        }
        int size = migrationReferences.size();
        int n = 0;
        for (EntityReference migrationReference : migrationReferences) {
            progressManager.startStep(this);
            logger.info("Browsing documents of migration [{}] ({}/{})", migrationReference, ++n, size);
            fixDocumentsOfMigration(s, migrationReference, baseURLs, updateInPlace, dryRun);
            progressManager.endStep(this);
        }
    }

    private void fixDocumentsOfMigration(Stats s, EntityReference migrationReference, String[] baseURLs,
        boolean updateInPlace, boolean dryRun)
    {
        XWikiContext context = contextProvider.get();
        XWikiDocument migrationDoc;
        try {
            migrationDoc = context.getWiki().getDocument(migrationReference, context);
        } catch (XWikiException e) {
            logger.error("Failed to get the migration document [{}], skipping.", migrationReference, e);
            s.incFailedDocs();
            return;
        }

        if (migrationDoc.isNew()) {
            logger.warn("Failed to find migration document [{}], skipping", migrationReference);
            s.incFailedDocs();
            return;
        }

        Map<String, Object> inputProperties = null;
        String[] actualBaseURLs;
        if (baseURLs == null || baseURLs.length == 0) {
            inputProperties = getInputProperties(migrationDoc);
            actualBaseURLs = getBaseURLs(migrationDoc, inputProperties);
        } else {
            actualBaseURLs = baseURLs;
        }

        boolean foundShortcut = fixDocumentsListedInRefWarnings(s, migrationDoc, actualBaseURLs, updateInPlace, dryRun);

        if (!foundShortcut) {
            foundShortcut = fixDocumentsListedInBrokenLinks(s, migrationDoc, actualBaseURLs, updateInPlace, dryRun);
        }

        if (!foundShortcut) {
            logger.warn("Failed to find a strategy to find only affected documents, will browse all the documents");
            if (inputProperties == null) {
                inputProperties = getInputProperties(migrationDoc);
            }
            fixDocumentsInSpace(s, updateInPlace, migrationDoc, actualBaseURLs, inputProperties, dryRun);
        }
    }

    private void fixDocumentsInSpace(Stats s, boolean updateInPlace, XWikiDocument migrationDoc,
        String[] baseURLs, Map<String, Object> props, boolean dryRun)
    {
        EntityReference root;
        boolean guess;
        if (props == null) {
            guess = true;
            logger.warn("Missing input properties means we could not determine the root space of the migration [{}], "
                + "will attempt to guess.", migrationDoc.getDocumentReference());
            root = contextProvider.get().getWikiReference();
        } else {
            guess = false;
            root = computeRootSpace(props);
        }
        EntityNameValidation nameStrategy = entityNameValidationManagerProvider.get().getEntityReferenceNameStrategy();
        List<String> spaces = migrationDoc.getListValue("spaces");
        if (CollectionUtils.isEmpty(spaces)) {
            logger.warn("Migration document [{}]: Could not find any space to handle",
                migrationDoc.getDocumentReference());
        }

        for (String space : spaces) {
            logger.info("Browsing documents in space [{}]", space);
            EntityReference spaceReference = computeSpaceReference(space, nameStrategy, guess, root);
            if (spaceReference == null) {
                continue;
            }

            fixDocumentsInSpace(s, spaceReference, baseURLs, BrokenRefType.UNKNOWN, updateInPlace, dryRun);
        }
    }

    private EntityReference computeRootSpace(Map<String, Object> inputProperties)
    {
        String rootSpaceStr = (String) inputProperties.get("root");
        if (StringUtils.isEmpty(rootSpaceStr)) {
            // Gracefully handle the deprecated property
            rootSpaceStr = (String) inputProperties.get("rootSpace");
        }

        if (StringUtils.isEmpty(rootSpaceStr)) {
            return contextProvider.get().getWikiReference();
        }

        if (rootSpaceStr.startsWith("wiki:")) {
            return new WikiReference(rootSpaceStr.substring(5));
        }

        if (rootSpaceStr.startsWith("space:")) {
            return resolver.resolve(rootSpaceStr.substring(6), EntityType.SPACE);
        }

        if (rootSpaceStr.endsWith(DOT_WEB_HOME)) {
            rootSpaceStr = rootSpaceStr.substring(0, rootSpaceStr.length() - 8);
        }

        return resolver.resolve(rootSpaceStr, EntityType.SPACE);
    }

    private void fixDocumentsInSpace(Stats s, EntityReference spaceReference, String[] baseURLs,
        BrokenRefType brokenRefType, boolean updateInPlace, boolean dryRun)
    {
        String wiki;
        String spaceRef;
        EntityReference spaceRoot = spaceReference.getRoot();
        if (spaceRoot != null && spaceRoot.getType() == EntityType.WIKI) {
            spaceRef = serializer.serialize(spaceReference, spaceRoot);
            wiki = spaceRoot.getName();
        } else {
            XWikiContext context = contextProvider.get();
            wiki = context.getWikiId();
            spaceRef = serializer.serialize(spaceReference, context.getWikiReference());
        }

        // FIXME: escape :space in the like clause (the space reference could theoretically contain a '%' sign)
        List<String> docFullNames;
        try {
            docFullNames = queryManager
                .createQuery(
                    "select doc.fullName from Document doc where doc.fullName like concat(:space, '.%')",
                    Query.XWQL)
                .setWiki(wiki)
                .bindValue(SPACE, spaceRef)
                .execute();
        } catch (QueryException e) {
            logger.error("Failed to list the documents in space [{}], skipping.", spaceRef, e);
            return;
        }

        String[] baseURLsNotNull = baseURLs == null ? new String[0] : baseURLs;
        List<String> docs =
            docFullNames.stream().map(fullName -> wiki + ':' + fullName).collect(Collectors.toList());
        fixDocumentsInternal(s, docs, baseURLsNotNull, updateInPlace, brokenRefType, dryRun);
    }

    private EntityReference computeSpaceReference(String space, EntityNameValidation nameStrategy, boolean guess,
        EntityReference root)
    {
        EntityReference spaceReference = null;
        String validatedSpace = nameStrategy == null ? space : nameStrategy.transform(space);
        if (guess) {
            try {
                spaceReference = spaceKeyResolver.getSpaceByKey(space);
            } catch (ConfluenceResolverException e) {
                logger.error("Failed to resolve space [{}] using Confluence resolvers", e);
            }
        } else {
            spaceReference = new EntityReference(validatedSpace, EntityType.SPACE, root);
        }

        return spaceReference;
    }

    private boolean fixDocumentsListedInRefWarnings(Stats s, XWikiDocument migrationDoc, String[] baseURLs,
        boolean updateInPlace, boolean dryRun)
    {
        XWikiContext context = contextProvider.get();
        XWikiAttachment refWarningsAttachment = migrationDoc.getAttachment("confluenceRefWarnings.json");
        if (refWarningsAttachment == null) {
            return false;
        }

        Map<String, List<Map<String, Object>>> refWarnings;
        try {
            XWikiAttachmentContent attachmentContent = refWarningsAttachment.getAttachmentContent(context);
            InputStream contentInputStream = attachmentContent.getContentInputStream();
            refWarnings = new ObjectMapper().readValue(contentInputStream, CONFLUENCE_REF_WARNINGS_TYPE_REF);
        } catch (IOException | XWikiException e) {
            logger.error("Failed to get the list of broken references", e);
            return false;
        }

        Collection<String> docRefs = refWarnings.entrySet().stream().filter(warningListEntries -> {
            List<Map<String, Object>> warningList = warningListEntries.getValue();
            return warningList.stream().anyMatch(warning -> {
                Object originalVersion = warning.get("originalVersion");
                return originalVersion == null || originalVersion.equals(warning.get("pageId"));
            });
        }).map(Map.Entry::getKey).collect(Collectors.toList());

        fixDocumentsInternal(s, docRefs, baseURLs, updateInPlace, BrokenRefType.CONFLUENCE_REFS, dryRun);
        return true;
    }

    private boolean fixDocumentsListedInBrokenLinks(Stats s, XWikiDocument migrationDoc, String[] baseURLs,
        boolean updateInPlace, boolean dryRun)
    {
        XWikiContext context = contextProvider.get();
        // Older migrations had a brokenLinksPages.json object listing affected pages
        Map<String, Object> brokenLinksPages;
        XWikiAttachment brokenLinksPageAttachment = migrationDoc.getAttachment("brokenLinksPages.json");
        try {
            if (brokenLinksPageAttachment == null) {
                // Even older migrations stored them in a brokenLinksPages field
                String brokenLinksPageJSON = migrationDoc.getStringValue("brokenLinksPages");
                if (StringUtils.isEmpty(brokenLinksPageJSON)) {
                    return false;
                }
                brokenLinksPages = new ObjectMapper().readValue(brokenLinksPageJSON, CONFLUENCE_BROKEN_LINK_PAGES_REF);
            } else {
                XWikiAttachmentContent attachmentContent = brokenLinksPageAttachment.getAttachmentContent(context);
                InputStream contentInputStream = attachmentContent.getContentInputStream();
                brokenLinksPages = new ObjectMapper().readValue(contentInputStream, CONFLUENCE_BROKEN_LINK_PAGES_REF);
            }
        } catch (IOException | XWikiException e) {
            logger.error("Failed to get the list of affected pages affected by broken links", e);
            return false;
        }

        Set<String> docRefs = brokenLinksPages.keySet();
        fixDocumentsInternal(s, docRefs, baseURLs, updateInPlace, BrokenRefType.BROKEN_LINKS, dryRun);
        return true;
    }

    private Map<String, Object> getInputProperties(XWikiDocument migrationDoc)
    {
        String inputPropertiesString = migrationDoc.getStringValue("inputProperties");

        if (StringUtils.isEmpty(inputPropertiesString)) {
            logger.warn("Failed to find input properties for migration [{}]", migrationDoc.getDocumentReference());
            return null;
        }
        try {
            return new ObjectMapper().readValue(inputPropertiesString, INPUT_PROPERTIES_TYPE_REF);
        } catch (JsonProcessingException e) {
            logger.error("Failed to read input properties for migration [{}]", migrationDoc.getDocumentReference(), e);
        }
        return null;
    }

    private String[] getBaseURLs(XWikiDocument migrationDoc, Map<String, Object> props)
    {
        if (props == null) {
            logger.warn("Missing input properties means we could not find base URLs for migration [{}]",
                migrationDoc.getDocumentReference());
            return new String[0];
        }

        Object baseURLsString = props.get("baseURLs");
        if ((baseURLsString instanceof String) && !StringUtils.isEmpty((String) baseURLsString)) {
            return Arrays.stream(((String) baseURLsString)
                .split(","))
                .map(baseURL -> StringUtils.removeEnd(baseURL, "/"))
                .toArray(String[]::new);
        } else {
            logger.warn("Base URLs are not set for migration [{}], will not use them",
                migrationDoc.getDocumentReference());
        }
        return new String[0];
    }

    private void fixDocumentsInternal(Stats s, Collection<String> docRefs, String[] baseURLs, boolean updateInPlace,
        BrokenRefType brokenRefType, boolean dryRun)
    {
        if (CollectionUtils.isEmpty(docRefs)) {
            logger.warn("There are no documents to fix");
            return;
        }

        progressManager.pushLevelProgress(docRefs.size(), this);
        int size = docRefs.size();
        int n = 0;
        for (String migratedDocRefStr : docRefs) {
            progressManager.startStep(this);
            EntityReference migratedDocRef = resolver.resolve(migratedDocRefStr, EntityType.DOCUMENT);
            logger.info("Handling document [{}] ({}/{})", migratedDocRef, ++n, size);
            fixDocument(s, migratedDocRef, baseURLs, updateInPlace, brokenRefType, dryRun);
            progressManager.endStep(this);
        }
        progressManager.popLevelProgress(this);
    }

    private void logConfluenceReferenceParserPresence()
    {
        boolean present;
        try {
            present = componentManager.getInstance(ResourceReferenceTypeParser.class, "confluencePage") != null;
        } catch (ComponentLookupException ignored) {
            present =  false;
        }
        if (present) {
            logger.info("Confluence resource reference type parsers are present. "
                + "This means that some Confluence references may be automatically and silently fixed at parse time "
                + "and not while analysing the document content, and these fixes will not be logged.");
        } else {
            logger.info("Confluence resource reference type parsers are not present. "
                + "No fix will be done at parse time, all fixes will be done while analysing the document content. "
                + "Reference fix logging should be detailed.");
        }
    }

    private void fixDocument(Stats s, EntityReference migratedDocRef, String[] baseURLs, boolean updateInPlace,
        BrokenRefType brokenRefType, boolean dryRun)
    {
        XWikiDocument migratedDoc = getDocument(migratedDocRef);
        if (migratedDoc == null) {
            return;
        }
        try {
            String oldContent = migratedDoc.getContent();

            XDOM xdom = migratedDoc.getXDOM();
            String syntax = migratedDoc.getSyntax().toIdString();
            String[] baseURLsNotNull = baseURLs == null ? new String[0] : baseURLs;
            boolean updated = visitXDOMToFixRefs(s, xdom, syntax, migratedDocRef, baseURLsNotNull, brokenRefType);
            maybeUpdateDocument(s, migratedDoc, xdom, oldContent, updateInPlace, updated, dryRun);
        } catch (Exception e) {
            logger.error("Failed to fix document [{}]", migratedDocRef, e);
        }
    }

    private XWikiDocument getDocument(EntityReference migratedDocRef)
    {
        XWikiDocument migratedDoc;
        try {
            XWikiContext context = contextProvider.get();
            migratedDoc = context.getWiki().getDocument(migratedDocRef, context).clone();
        } catch (XWikiException e) {
            logger.error("Failed to get the migrated document [{}], skipping.", migratedDocRef, e);
            return null;
        }
        if (migratedDoc.isNew()) {
            logger.error("The migrated document [{}] doesn't exist, skipping.", migratedDocRef);
            return null;
        }
        return migratedDoc;
    }

    private void maybeUpdateDocument(Stats s, XWikiDocument migratedDoc, XDOM xdom, String oldContent,
        boolean updateInPlace, boolean updated, boolean dryRun)
    {
        String newContent = migratedDoc.getContent();
        Syntax origSyntax = migratedDoc.getSyntax();
        boolean upd = updated;
        DocumentReference migratedDocRef = migratedDoc.getDocumentReference();
        try {
            String syntax = origSyntax.toIdString();
            if (syntax.contains(CONFLUENCE)) {
                logger.warn("Document [{}] uses syntax [{}] and will be converted to [{}]. "
                    + "The report about references being updated at parse time would be inaccurate",
                    migratedDocRef, syntax, Syntax.XWIKI_2_1);
                migratedDoc.setSyntax(Syntax.XWIKI_2_1);
                upd = true;
            }
            migratedDoc.setContent(xdom);
        } catch (XWikiException e) {
            logger.error("Failed to update the document XDOM [{}]", migratedDocRef, e);
            s.incFailedDocs();
            return;
        }

        if (hasXDOMChangedAtParseTime(s, migratedDocRef, oldContent, newContent) || upd) {
            if (dryRun) {
                logger.info("Would update document [{}]", migratedDocRef);
                s.incSuccessfulDocs();
                return;
            }
            updateDocument(s, migratedDoc, updateInPlace, migratedDocRef);
        } else {
            logger.info(UNCHANGED_MARKER, "Document [{}] is left unchanged", migratedDocRef);
            s.incUnchangedDocs();
        }
    }

    private void updateDocument(Stats s, XWikiDocument migratedDoc, boolean updateInPlace,
        DocumentReference migratedDocRef)
    {
        try {
            if (updateInPlace) {
                logger.info(UPDATED_MARKER, "Updating document [{}] without adding a revision",
                    migratedDocRef);
                migratedDoc.setMetaDataDirty(false);
                migratedDoc.setContentDirty(false);
            } else {
                logger.info(UPDATED_MARKER, "Updating document [{}], adding a new revision",
                    migratedDocRef);
            }
            XWikiContext context = contextProvider.get();
            context.getWiki().saveDocument(migratedDoc, "Fix broken links", context);
            s.incSuccessfulDocs();
        } catch (XWikiException e) {
            logger.error("Failed to save document [{}]", migratedDocRef, e);
            s.incFailedDocs();
        }
    }

    private boolean hasXDOMChangedAtParseTime(Stats s, EntityReference migratedDocRef, String oldContent,
        String newContent)
    {
        // Even if the document has not been updated during the visit of its XDOM, it could have been updated
        // at parse type by Confluence reference type parsers.
        // To detect this, we could the occurrences of the string "confluence" in the old and the new Content.
        // This doesn't actually count the number of Confluence references because of course "confluence" could
        // appear in regular text, but any discrepancy will hint at this happening.
        // We don't bother counting if the document is to be updated anyway.
        int oldConfluenceOccurences = StringUtils.countMatches(oldContent, CONFLUENCE);
        int newConfluenceOccurrences = StringUtils.countMatches(newContent, CONFLUENCE);
        int diff = oldConfluenceOccurences - newConfluenceOccurrences;
        if (diff > 0) {
            logger.info(CHANGED_AT_PARSE_TIME_MARKER,
                "Document [{}] has changed at parse time ([{}] references updated)", migratedDocRef, diff);
            s.incSuccessfulRefs(diff);
            return true;
        }
        return false;
    }

    private ResourceReference maybeConvertURLAsResourceRef(Stats s, String maybeURL, EntityReference migratedDocRef,
        String[] baseURLs)
    {
        if (baseURLs.length == 0 || StringUtils.isEmpty(maybeURL)) {
            return null;
        }

        List<ConfluenceURLMapper> mappers = getConfluenceURLMappers(s, maybeURL, migratedDocRef);
        if (mappers == null) {
            return null;
        }

        try {
            for (String baseURL : baseURLs) {
                if (!maybeURL.startsWith(baseURL)) {
                    continue;
                }

                return maybeConvertURLAsResourceRef(s, maybeURL, migratedDocRef, baseURL, mappers);
            }
        } catch (Exception e) {
            logger.error(FAILED_REFERENCE_CONVERSION_MARKER,
                "Document [{}]: Failed to convert Confluence URL [{}] because of an exception",
                migratedDocRef, maybeURL, e);
        }

        return null;
    }

    private ResourceReference maybeConvertURLAsResourceRef(Stats s, String maybeURL, EntityReference migratedDocRef,
        String baseURL, List<ConfluenceURLMapper> mappers)
    {
        String url = StringUtils.removeStart(maybeURL, baseURL).replaceAll("^/+", "");
        String anchor = null;
        int anchorPos = url.indexOf('#');
        if (anchorPos != -1) {
            anchor = url.substring(anchorPos + 1);
            url = url.substring(0, anchorPos);
        }

        ResourceReference serializedEntity = maybeConvertURLAsResourceRef(url, migratedDocRef, mappers);
        if (serializedEntity == null) {
            logger.warn(FAILED_REFERENCE_CONVERSION_MARKER,
                "Document [{}]: Failed to convert Confluence URL [{}]", migratedDocRef, maybeURL);
            s.incFailedRefs();
            return null;
        }

        if (anchor != null && (serializedEntity instanceof DocumentResourceReference)) {
            ((DocumentResourceReference) serializedEntity).setAnchor(anchor);
        }

        logger.info("Document [{}]: Converting URL [{}] to [{}]", migratedDocRef, maybeURL, serializedEntity);
        s.incSuccessfulRefs();
        return serializedEntity;
    }

    private List<ConfluenceURLMapper> getConfluenceURLMappers(Stats s, String maybeURL, EntityReference migratedDocRef)
    {
        List<ConfluenceURLMapper> mappers = null;
        try {
            mappers = componentManager.getInstanceList(ConfluenceURLMapper.class);
        } catch (ComponentLookupException e) {
            logger.error(EXCEPTION_WHILE_RESOLVING, migratedDocRef, maybeURL, e);
            s.incFailedRefs();
        }
        return mappers;
    }

    private ResourceReference maybeConvertURLAsResourceRef(String url, EntityReference migratedDocRef,
        List<ConfluenceURLMapper> mappers)
    {
        for (ConfluenceURLMapper mapper : mappers) {
            Matcher m = null;
            boolean notFound = false;
            for (Pattern p : mapper.getSpecification().getRegexes()) {
                m = p.matcher(url);
                if (m.matches()) {
                    break;
                } else {
                    notFound = true;
                }
            }

            if (!notFound) {
                ResourceReference serializedEntity = convertConvertURLAsResourceRef(url, migratedDocRef, mapper, m);
                if (serializedEntity != null) {
                    return serializedEntity;
                }
            }
        }
        return null;
    }

    private ResourceReference convertConvertURLAsResourceRef(String url, EntityReference ref,
        ConfluenceURLMapper mapper, Matcher m)
    {
        Object resultObj = mapper.convert(new DefaultURLMappingMatch(url, "get", m, null));
        org.xwiki.resource.ResourceReference resourceReference = null;
        if (resultObj != null) {
            // The following condition is supposed to be always true, but the else branch is taken in Groovy
            if (resultObj instanceof URLMappingResult) {
                resourceReference = ((URLMappingResult) resultObj).getResourceReference();
            } else if (resultObj instanceof org.xwiki.resource.ResourceReference) {
                resourceReference = (org.xwiki.resource.ResourceReference) resultObj;
            }
        }
        if (resourceReference instanceof EntityResourceReference) {
            EntityResourceReference rr = (EntityResourceReference) resourceReference;
            String serializedEntity = serializer.serialize(rr.getEntityReference(), ref);
            String type = rr.getEntityReference().getType().getLowerCase();
            if (DOCUMENT.equals(type)) {
                return new DocumentResourceReference(serializedEntity);
            }
            return new ResourceReference(serializedEntity, new ResourceType(type));
        }
        return null;
    }

    private ResourceReference maybeConvertUnprefixedBrokenLink(Stats s, String oldRef, EntityReference migratedDocRef)
    {
        int dot = oldRef.indexOf('.');
        if (dot == -1) {
            return null;
        }

        // We are assuming that the migrator, at the time it output broken links, only output regular things like dotted
        // references, didn't do anything fancy, and we can safely ignore the prefix.
        // We'll parse the reference with a regex.
        Matcher m = BROKEN_LINK_PATTERN.matcher(oldRef);
        if (!m.matches()) {
            return null;
        }

        // Unfortunately, issuing broken links wasn't bad enough, we also accidentally issued broken links with
        // transformed titles. Hence the mouthful "name validated title" variable name instead of just "page title", to
        // emphasize the fact. Most likely this doesn't make any difference in most cases, because by default, no
        // name strategy is enforced and most Confluence users would have migrated without a name strategy set, but
        // let's play safe here.

        String nameValidatedTitle = m.group("nameValidatedTitle");
        if (containsUnescapedChar(nameValidatedTitle, '.') || WEB_HOME.equals(nameValidatedTitle)) {
            // Links we handle here should not contain dots, as they should be of the shape SPACE.page title, where
            // page title does not contain a dot. But the regular expression we use is to limited to check that the
            // title doesn't contain an unescaped dot.
            // If the name is 'WebHome', the reference is likely not a broken link: WebHome links were output only for
            // documents which were found during the migration.
            // And for pages with a title "WebHome", but that's unlikely, so we choose we ignore this case.
            // Ignoring this case allows the script to be fast by ignoring most (valid) links without querying the
            // database.
            // Worst case, it shouldn't be too hard to handle these hypothetical broken links with a custom script for
            // the odd page titled "WebHome" hanging around, or maybe the few affected links can even be fixed manually.
            return null;
        }

        String space = m.group(SPACE);
        String attachment = m.group("attachment");

        EntityReference newRef = null;
        try {
            newRef = tryResolvingBrokenLinkDoc(nameValidatedTitle, space);
        } catch (ConfluenceResolverException | QueryException e) {
            logger.error(EXCEPTION_WHILE_RESOLVING, migratedDocRef, oldRef, e);
        }

        if (newRef == null) {
            logger.warn(FAILED_REFERENCE_CONVERSION_MARKER, "Document [{}]: Failed to convert broken link [{}]",
                migratedDocRef, oldRef);
            s.incFailedRefs();
            return null;
        }

        ResourceReference rr;
        if (StringUtils.isEmpty(attachment)) {
            rr = new ResourceReference(serializer.serialize(newRef, migratedDocRef), ResourceType.DOCUMENT);
        } else {
            newRef = new EntityReference(attachment, EntityType.ATTACHMENT, newRef);
            rr = new ResourceReference(serializer.serialize(newRef, migratedDocRef), ResourceType.ATTACHMENT);
        }
        logger.info(SUCCESSFUL_REFERENCE_CONVERSION_MARKER, "Document [{}]: Converting broken link [{}] to [{}]",
            migratedDocRef, oldRef, rr);
        s.incSuccessfulRefs();
        return rr;
    }

    private String maybeConvertBrokenLink(Stats s, String str, EntityReference ref)
    {
        int prefixLength = getReferencePrefixLength(str);
        String oldRef = str.substring(prefixLength);
        String prefix = str.substring(0, prefixLength);
        if (prefix.equals(ATTACH) && !containsUnescapedChar(oldRef, '@')) {
            // This reference doesn't contain an '@' character, it should not be converted.
            return null;
        }

        ResourceReference newRef = maybeConvertUnprefixedBrokenLink(s, oldRef, ref);
        if (newRef == null) {
            return null;
        }
        return prefix + newRef.getReference();
    }

    private static boolean containsUnescapedChar(String str, char character)
    {
        int i = 0;
        int len = str.length();
        while (i < len) {
            char c = str.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == character) {
                return true;
            }
            i++;
        }
        return false;
    }

    private EntityReference tryResolvingBrokenLinkDoc(String nameValidatedTitle, String space)
        throws ConfluenceResolverException, QueryException
    {
        if ("@home".equals(nameValidatedTitle)) {
            EntityReference spaceRef = spaceKeyResolver.getSpaceByKey(space);
            if (spaceRef == null) {
                return null;
            }

            return new EntityReference(WEB_HOME, EntityType.DOCUMENT, spaceRef);
        }

        return pageTitleResolver.getDocumentByTitle(space, nameValidatedTitle);
    }

    private String maybeConvertMacroParameter(Stats s, String str, EntityReference migratedDocRef, String[] baseURLs)
    {
        ResourceReference rr = maybeConvertReference(s, str, migratedDocRef, baseURLs, BrokenRefType.CONFLUENCE_REFS);
        if (rr == null) {
            return null;
        }

        return rr.getReference();
    }

    private ResourceReference maybeConvertReference(Stats s, ResourceReference reference,
        EntityReference migratedDocRef, String[] baseURLs, BrokenRefType brokenRefType)
    {
        if (reference == null) {
            return null;
        }

        ResourceType type = reference.getType();

        if (type.equals(ResourceType.URL)) {
            return maybeConvertURLAsResourceRef(s, reference.getReference(), migratedDocRef, baseURLs);
        }

        String scheme = type.getScheme();
        if (scheme.startsWith(CONFLUENCE)) {
            for (ConfluenceResourceReferenceType confluenceType : ConfluenceResourceReferenceType.values()) {
                String candidateType = confluenceType.getId();
                if (candidateType.equals(scheme)) {
                    return maybeConvertConfluenceReference(s, reference.getReference(), migratedDocRef, confluenceType);
                }
            }

            logger.warn("Docuemnt [{}]: unrecognized Confluence resource reference type [{}] for reference [{}]",
                migratedDocRef, scheme, reference);
        }

        return maybeConvertReference(s, reference.getReference(), migratedDocRef, baseURLs, brokenRefType);
    }

    private ResourceReference maybeConvertReference(Stats s, String reference, EntityReference migratedDocRef,
        String[] baseURLs, BrokenRefType brokenRefType)
    {
        if (StringUtils.isEmpty(reference)) {
            return null;
        }

        if (reference.startsWith("url:")) {
            return maybeConvertURLAsResourceRef(s, reference.substring(4), migratedDocRef, baseURLs);
        }

        ResourceReference res = maybeConvertURLAsResourceRef(s, reference, migratedDocRef, baseURLs);
        if (res != null) {
            return res;
        }

        if (brokenRefType == BrokenRefType.BROKEN_LINKS || brokenRefType == BrokenRefType.UNKNOWN) {
            String unprefixedRef = reference.substring(getReferencePrefixLength(reference));
            res = maybeConvertUnprefixedBrokenLink(s, unprefixedRef, migratedDocRef);
            if (res != null) {
                return res;
            }
        }

        if (brokenRefType == BrokenRefType.CONFLUENCE_REFS || brokenRefType == BrokenRefType.UNKNOWN) {
            res = maybeConvertConfluenceReference(s, reference, migratedDocRef);
        }
        return res;
    }

    private ResourceReference maybeConvertConfluenceReference(Stats s, String reference, EntityReference migratedDocRef)
    {
        ConfluenceResourceReferenceType type = confluenceResourceReferenceResolver.getType(reference);
        if (type == null) {
            return null;
        }

        String typelessReference = reference.substring(type.getId().length() + 1);
        return maybeConvertConfluenceReference(s, typelessReference, migratedDocRef, type);
    }

    private ResourceReference maybeConvertConfluenceReference(Stats s, String reference, EntityReference migratedDocRef,
        ConfluenceResourceReferenceType type)
    {
        String ref = reference;
        try {
            if (ref.startsWith("page:@self") || ref.startsWith(SELF)) {
                String space = spaceResolver.getSpaceKey(migratedDocRef);
                if (StringUtils.isNotEmpty(space)) {
                    ref = StringUtils.replaceOnce(ref, SELF, space);
                }
            }
            ResourceReference r = confluenceResourceReferenceResolver.resolve(type, ref);
            if (r == null) {
                logger.warn(FAILED_REFERENCE_CONVERSION_MARKER,
                    "Document [{}]: Failed to convert Confluence reference [{}:{}]",
                    migratedDocRef, type.getId(), reference);
            } else {
                s.incSuccessfulRefs();
                EntityReference wikiRef = migratedDocRef.getRoot();
                if (wikiRef.getType() == EntityType.WIKI && r.getReference().startsWith(wikiRef.getName() + ':')) {
                    // Strip the wiki
                    r.setReference(r.getReference().substring(wikiRef.getName().length() + 1));
                }
                logger.info(SUCCESSFUL_REFERENCE_CONVERSION_MARKER,
                    "Document [{}]: Converting Confluence reference [{}:{}] to [{}]", migratedDocRef, type.getId(),
                    reference, r);
                return r;
            }
        } catch (ConfluenceResolverException e) {
            logger.error(FAILED_REFERENCE_CONVERSION_MARKER,
                "Document [{}]: Failed to convert Confluence reference [{}] because of an exception",
                migratedDocRef, reference, e);
        }
        s.incFailedRefs();
        return null;
    }

    private boolean updateMacroBrokenLinksParams(Stats s, String paramName, Map<String, String> parameters,
        MacroBlock block, EntityReference migratedDocRef)
    {
        String oldRef = parameters.get(paramName);
        if (oldRef == null) {
            return false;
        }

        String newRef = maybeConvertBrokenLink(s, oldRef, migratedDocRef);

        if (newRef == null) {
            return false;
        }

        logger.info(UPDATE_PARAM_LOG, migratedDocRef, block.getId(), paramName, oldRef, newRef);
        block.setParameter(paramName, newRef);
        return true;
    }

    private static int getReferencePrefixLength(String p)
    {
        if (p.startsWith("page:")) {
            return 5;
        }

        if (p.startsWith("doc:")) {
            return 4;
        }

        if (p.startsWith(DOCUMENT_COL)) {
            return 9;
        }

        if (p.startsWith(ATTACH)) {
            return 7;
        }

        return 0;
    }

    private boolean fixConfluenceRefsInMacroParams(Stats s, MacroBlock block, EntityReference migratedDocRef,
        String[] baseURLs)
    {
        boolean updated = false;
        for (Map.Entry<String, String> parameter : block.getParameters().entrySet()) {
            String oldRef = parameter.getValue();
            String prefix = "";
            if (StringUtils.isNotEmpty(oldRef) && oldRef.startsWith(DOCUMENT_COL)) {
                oldRef = oldRef.substring(9);
                prefix = DOCUMENT_COL;
            }
            String newRef = maybeConvertMacroParameter(s, oldRef, migratedDocRef,  baseURLs);
            if (newRef != null) {
                String paramName = parameter.getKey();
                block.setParameter(paramName, prefix + newRef);
                logger.info(UPDATE_PARAM_LOG, migratedDocRef, block.getId(), paramName, oldRef, newRef);
                updated = true;
            }
        }
        return updated;
    }

    private boolean fixBrokenLinksInMacroParameters(Stats s, MacroBlock block, EntityReference migratedDocRef)
    {
        if (!ALLOWED_BROKEN_LINK_MACROS.contains(block.getId())) {
            return false;
        }

        Map<String, String> parameters = block.getParameters();
        boolean updated = updateMacroBrokenLinksParams(s, "page", parameters, block, migratedDocRef);
        updated = updateMacroBrokenLinksParams(s, "reference", parameters, block, migratedDocRef) || updated;
        updated = updateMacroBrokenLinksParams(s, DOCUMENT, parameters, block, migratedDocRef) || updated;
        return updated;
    }

    private boolean visitXDOMToFixRefs(Stats s, XDOM xdom, String syntaxId, EntityReference migratedDocRef,
        String[] baseURLs, BrokenRefType brokenRefType)
    {
        boolean updated = false;
        OrBlockMatcher matcher = new OrBlockMatcher(
            new ClassBlockMatcher(LinkBlock.class),
            new ClassBlockMatcher(ImageBlock.class),
            new ClassBlockMatcher(MacroBlock.class)
        );

        List<Block> blocks = xdom.getBlocks(matcher, Block.Axes.DESCENDANT_OR_SELF);
        for (Block b : blocks) {
            updated = visitBlockToFixRefs(s, b, syntaxId, migratedDocRef, baseURLs, brokenRefType) || updated;
        }

        return updated;
    }

    private boolean visitBlockToFixRefs(Stats s, Block b, String syntaxId, EntityReference migratedDocRef,
        String[] baseURLs, BrokenRefType brokenRefType)
    {
        if (b instanceof MacroBlock) {
            return visitMacroBlockToFixRefs(s, syntaxId, migratedDocRef, baseURLs, brokenRefType, (MacroBlock) b);
        }
        if (b instanceof LinkBlock) {
            return updateLinkBlockToFixRef(s, (LinkBlock) b, migratedDocRef, baseURLs, brokenRefType);
        }

        if (b instanceof ImageBlock) {
            return updateImageBlockToFixRefs(s, (ImageBlock) b, migratedDocRef, baseURLs, brokenRefType);
        }

        return false;
    }

    private boolean updateImageBlockToFixRefs(Stats s, ImageBlock block, EntityReference migratedDocRef,
        String[] baseURLs, BrokenRefType brokenRefType)
    {
        ResourceReference oldRef = block.getReference();
        ResourceReference newRef = maybeConvertReference(s, oldRef, migratedDocRef, baseURLs, brokenRefType);
        if (newRef == null) {
            return false;
        }

        logger.info("Document [{}]: Updating image: [{}] -> [{}]", migratedDocRef, oldRef, newRef);

        block.getParent().replaceChild(new ImageBlock(newRef, block.isFreeStandingURI(), block.getParameters()), block);
        return true;
    }

    private boolean updateLinkBlockToFixRef(Stats s, LinkBlock block, EntityReference migratedDocRef,
        String[] baseURLs, BrokenRefType brokenRefType)
    {
        // We don't visit the children of links, they are visited when visiting the children of the parent.
        ResourceReference oldRef = block.getReference();
        ResourceReference newRef = maybeConvertReference(s, oldRef, migratedDocRef, baseURLs, brokenRefType);
        if (newRef == null) {
            return false;
        }
        logger.info("Document [{}]: Updating link: [{}] -> [{}]", migratedDocRef, oldRef, newRef);
        block.getParent().replaceChild(
            new LinkBlock(block.getChildren(), newRef, block.isFreeStandingURI(), block.getParameters()), block);
        return true;
    }

    private boolean visitMacroBlockToFixRefs(Stats s, String syntaxId, EntityReference migratedDocRef,
        String[] baseURLs, BrokenRefType brokenRefType, MacroBlock block)
    {
        MacroBlock b = block;
        boolean updated = false;
        String id = b.getId();
        String oldContent = b.getContent();
        String newContent = visitMacroContentToFixRefs(s, syntaxId, migratedDocRef, baseURLs, id, oldContent,
            brokenRefType);
        if (newContent != null) {
            b = new MacroBlock(id, b.getParameters(), newContent, b.isInline());
            block.getParent().replaceChild(b, block);
            updated = true;
        }

        if (brokenRefType == BrokenRefType.BROKEN_LINKS || brokenRefType == BrokenRefType.UNKNOWN) {
            updated = fixBrokenLinksInMacroParameters(s, block, migratedDocRef) || updated;
        }

        if (brokenRefType == BrokenRefType.CONFLUENCE_REFS || brokenRefType == BrokenRefType.UNKNOWN) {
            updated = fixConfluenceRefsInMacroParams(s, block, migratedDocRef, baseURLs) || updated;
        }

        return updated;
    }

    private XDOM parse(String text, String syntaxId)
    {
        // From RenderingScriptService
        XDOM result;
        try {
            Parser parser = this.componentManager.getInstance(Parser.class, syntaxId);
            result = parser.parse(new StringReader(text));
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    private String render(Block block, String outputSyntaxId)
    {
        // From RenderingScriptService
        String result;
        WikiPrinter printer = new DefaultWikiPrinter();
        try {
            BlockRenderer renderer =
                this.componentManager.getInstance(BlockRenderer.class, outputSyntaxId);
            renderer.render(block, printer);
            result = printer.toString();
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    private String visitMacroContentToFixRefs(Stats s, String syntaxId, EntityReference documentReference,
        String[] baseURLs, String macroId, String content, BrokenRefType brokenRefType)
    {
        if (!componentManager.hasComponent(Macro.class, macroId)) {
            return null;
        }

        if (StringUtils.isBlank(content)) {
            return null;
        }

        // Check if the macro content is wiki syntax, in which case we'll also verify the contents of the macro
        Macro<?> macro;
        try {
            macro = componentManager.getInstance(Macro.class, macroId);
        } catch (ComponentLookupException e) {
            logger.error("Failed to lookup macro [{}], its content will not be converted", macroId, e);
            return null;
        }

        ContentDescriptor contentDescriptor = macro.getDescriptor().getContentDescriptor();
        if (contentDescriptor == null || !contentDescriptor.getType().equals(Block.LIST_BLOCK_TYPE)) {
            return null;
        }

        // We will take a quick shortcut here and directly parse the macro content with the syntax of the document
        XDOM macroXDOM = parse(content, syntaxId);
        boolean updated = visitXDOMToFixRefs(s, macroXDOM, syntaxId, documentReference, baseURLs, brokenRefType);
        return updated ? render(macroXDOM, syntaxId) : null;
    }
}
