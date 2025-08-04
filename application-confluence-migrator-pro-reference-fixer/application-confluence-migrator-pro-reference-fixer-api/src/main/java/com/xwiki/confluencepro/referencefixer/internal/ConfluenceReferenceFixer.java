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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentContent;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.confluencepro.internal.MigrationFixingTools;
import com.xwiki.confluencepro.referencefixer.BrokenRefType;
import org.apache.commons.collections.IteratorUtils;
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
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
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

import java.io.ByteArrayInputStream;
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
    private static final String WEB_HOME = "WebHome";

    private static final Marker FAILED_REFERENCE_CONVERSION_MARKER =
        MarkerFactory.getMarker("confluencereferencefixer.failedrefconversion");
    private static final Marker SUCCESSFUL_REFERENCE_CONVERSION_MARKER =
        MarkerFactory.getMarker("confluencereferencefixer.successfulrefconversion");
    private static final Marker CHANGED_AT_PARSE_TIME_MARKER
        = MarkerFactory.getMarker("confluencereferencefixer.changedatparsetime");

    private static final String UPDATE_PARAM_LOG = "Document [{}]: Updating macro [{}]'s parameter [{}]: [{}] -> [{}]";
    private static final String CONFLUENCE = "confluence";
    private static final String EXCEPTION_WHILE_RESOLVING
        = "Document [{}]: Failed to resolve [{}] because of an exception";
    private static final List<String> ALLOWED_BROKEN_LINK_MACROS = List.of("include",
        "display",
        "locationsearch",
        "children",
        "confluence_children");
    private static final String SELF = "@self";

    private static final String ATTACH = "attach:";

    private static final String DOCUMENT_COL = "document:";

    private static final String COMMENT = "comment";

    private static final String SLASH = "/";

    private static final String BROKEN_LINKS_PAGES_JSON = "brokenLinksPages.json";

    private static final String CONFLUENCE_REF_WARNINGS_JSON = "confluenceRefWarnings.json";

    private static final String BROKEN_LINKS_PAGES = "brokenLinksPages";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private ConfluencePageTitleResolver pageTitleResolver;

    @Inject
    private ConfluenceSpaceKeyResolver spaceKeyResolver;

    @Inject
    private ConfluenceSpaceResolver spaceResolver;

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
    private MigrationFixingTools migrationFixingTools;

    /**
     * Fix broken references in all documents of the given space.
     * @param baseURLs the baseURLs to use when fixing absolute links. If not provided, will be guessed from migration
     *                 documents.
     * @param migrationReferences the migration documents
     * @param spaceReferences the spaces in which to fix the references
     * @param brokenRefType the type of the broken references
     * @param exhaustive whether to ignore information about page containing missing references in migrations
     * @param updateInPlace whether to update migrated documents with the fixed references in place instead of
     *                      creating a new revision.
     * @param dryRun true to simulate only (the fixed documents will not be saved)
     * @return the statistics of the reference fixing sessions
     */
    public Stats fixDocuments(List<EntityReference> migrationReferences, List<EntityReference> spaceReferences,
        String[] baseURLs, BrokenRefType brokenRefType, boolean exhaustive, boolean updateInPlace, boolean dryRun)
    {
        logConfluenceReferenceParserPresence();
        BrokenRefType b = brokenRefType == null ? BrokenRefType.UNKNOWN : brokenRefType;
        Stats s = new Stats();
        String[] baseURLsNotNull = baseURLs == null ? new String[0] : baseURLs;
        migrationFixingTools.fixDocuments(
            s,
            migrationReferences,
            spaceReferences,
            migratedDoc -> fixDocument(s, migratedDoc, baseURLsNotNull, updateInPlace, b, dryRun),
            migrationDoc -> fixDocumentsOfMigration(s, migrationDoc, baseURLs, exhaustive, updateInPlace, dryRun)
        );
        return s;
    }

    private void fixDocumentsOfMigration(Stats s, XWikiDocument migrationDoc, String[] baseURLs, boolean exhaustive,
        boolean updateInPlace, boolean dryRun)
    {
        String[] actualBaseURLs;
        if (baseURLs == null || baseURLs.length == 0) {
            actualBaseURLs = getBaseURLs(migrationDoc, migrationFixingTools.getInputProperties(migrationDoc));
        } else {
            actualBaseURLs = baseURLs;
        }

        boolean foundShortcut = false;

        if (!exhaustive) {
            foundShortcut = fixDocumentsListedInRefWarnings(s, migrationDoc, actualBaseURLs, updateInPlace, dryRun);

            if (!foundShortcut) {
                foundShortcut = fixDocumentsListedInBrokenLinks(s, migrationDoc, actualBaseURLs, updateInPlace, dryRun);
            }
        }

        if (!foundShortcut) {
            if (!exhaustive) {
                logger.warn("Failed to find a strategy to find only affected documents, will browse all the documents");
            }
            BrokenRefType b = getBrokenRefType(migrationDoc);
            migrationFixingTools.fixDocumentsOfMigration(migrationDoc,
                migratedDoc -> fixDocument(s, migratedDoc, actualBaseURLs, updateInPlace, b, dryRun));
        }
    }

    private static BrokenRefType getBrokenRefType(XWikiDocument migrationDoc)
    {
        if (migrationDoc.getAttachment(CONFLUENCE_REF_WARNINGS_JSON) != null) {
            return BrokenRefType.CONFLUENCE_REFS;
        }

        if (migrationDoc.getAttachment(BROKEN_LINKS_PAGES_JSON) != null
            || StringUtils.isNotEmpty(migrationDoc.getStringValue(BROKEN_LINKS_PAGES))
        ) {
            return BrokenRefType.BROKEN_LINKS;
        }

        return BrokenRefType.UNKNOWN;
    }

    private boolean fixDocumentsListedInRefWarnings(Stats s, XWikiDocument migrationDoc, String[] baseURLs,
        boolean updateInPlace, boolean dryRun)
    {
        XWikiContext context = contextProvider.get();
        XWikiAttachment refWarningsAttachment = migrationDoc.getAttachment(CONFLUENCE_REF_WARNINGS_JSON);
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

        migrationFixingTools.fixDocuments(docRefs,
            migratedDoc -> fixDocument(s, migratedDoc, baseURLs, updateInPlace, BrokenRefType.CONFLUENCE_REFS, dryRun));
        return true;
    }

    private boolean fixDocumentsListedInBrokenLinks(Stats s, XWikiDocument migrationDoc, String[] baseURLs,
        boolean updateInPlace, boolean dryRun)
    {
        XWikiContext context = contextProvider.get();
        // Older migrations had a brokenLinksPages.json object listing affected pages
        Map<String, Object> brokenLinksPages;
        XWikiAttachment brokenLinksPageAttachment = migrationDoc.getAttachment(BROKEN_LINKS_PAGES_JSON);
        try {
            if (brokenLinksPageAttachment == null) {
                // Even older migrations stored them in a brokenLinksPages field
                String brokenLinksPageJSON = migrationDoc.getStringValue(BROKEN_LINKS_PAGES);
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
        migrationFixingTools.fixDocuments(docRefs,
            migratedDoc -> fixDocument(s, migratedDoc, baseURLs, updateInPlace, BrokenRefType.BROKEN_LINKS, dryRun));
        return true;
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
                .map(baseURL -> StringUtils.removeEnd(baseURL, SLASH))
                .toArray(String[]::new);
        } else {
            logger.warn("Base URLs are not set for migration [{}], will not use them",
                migrationDoc.getDocumentReference());
        }
        return new String[0];
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

    private void fixDocument(Stats s, XWikiDocument migratedDoc, String[] baseURLs, boolean updateInPlace,
        BrokenRefType brokenRefType, boolean dryRun)
    {
        DocumentReference migratedDocRef = migratedDoc.getDocumentReference();
        try {
            String oldContent = migratedDoc.getContent();
            XDOM xdom = migratedDoc.getXDOM();
            String syntax = migratedDoc.getSyntax().toIdString();
            String[] baseURLsNotNull = baseURLs == null ? new String[0] : baseURLs;
            boolean updated = visitXDOMToFixRefs(s, xdom, syntax, migratedDocRef, baseURLsNotNull, brokenRefType);
            updated = updateComments(s, migratedDoc, syntax, migratedDocRef, baseURLsNotNull, brokenRefType) || updated;
            updated = updateGliffyDiagrams(s, migratedDoc, migratedDocRef, baseURLsNotNull) || updated;
            maybeUpdateDocument(s, migratedDoc, xdom, oldContent, updateInPlace, updated, dryRun);
        } catch (Exception e) {
            logger.error("Failed to fix document [{}]", migratedDocRef, e);
        }
    }

    private boolean convertGliffyDiagramNode(Stats s, JsonNode jsonNode, EntityReference migratedDocRef,
        String[] baseURLsNotNull)
    {
        if (jsonNode.isArray()) {
            boolean updated = false;
            for (JsonNode node : jsonNode) {
                updated = convertGliffyDiagramNode(s, node, migratedDocRef, baseURLsNotNull) || updated;
            }
            return updated;
        }

        if (jsonNode.isObject()) {
            return convertGliffyDiagramObject(s, jsonNode, migratedDocRef, baseURLsNotNull);
        }

        return false;
    }

    private boolean convertGliffyDiagramObject(Stats s, JsonNode jsonNode, EntityReference migratedDocRef,
        String[] baseURLsNotNull)
    {
        boolean updated = false;
        List<Map.Entry<String, JsonNode>> fieldsCopy = IteratorUtils.toList(jsonNode.fields());
        for (Map.Entry<String, JsonNode> field : fieldsCopy) {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if ("url".equals(key) || "href".equals(key) && value.isValueNode() && value.isTextual()) {
                String url = value.asText();
                if (StringUtils.startsWith(url, "data:")) {
                    continue;
                }
                ResourceReference resourceReference =
                    maybeConvertURLAsResourceRef(s, url, migratedDocRef, baseURLsNotNull, true);
                if (resourceReference != null && resourceReference.getType() == ResourceType.URL) {
                    ((ObjectNode) jsonNode).put(key, resourceReference.getReference());
                    updated = true;
                }
            } else  {
                updated = convertGliffyDiagramNode(s, value, migratedDocRef, baseURLsNotNull) || updated;
            }
        }
        return updated;
    }

    private boolean updateGliffyDiagrams(Stats s, XWikiDocument migratedDoc, EntityReference migratedDocRef,
        String[] baseURLsNotNull) throws XWikiException
    {
        boolean updated = false;
        for (XWikiAttachment a : migratedDoc.getAttachmentList()) {
            String filename = a.getFilename();
            if (migratedDoc.getAttachment(filename + ".png") != null) {
                // The current attachment is likely a Gliffy diagram because it has an associated .png file
                updated = convertGliffyDiagram(s, migratedDocRef, baseURLsNotNull, a, filename) || updated;
            }
        }

        return updated;
    }

    private boolean convertGliffyDiagram(Stats s, EntityReference migratedDocRef, String[] baseURLsNotNull,
        XWikiAttachment a, String filename) throws XWikiException
    {
        XWikiAttachmentContent attachmentContent = a.getAttachmentContent(contextProvider.get());
        if (attachmentContent == null) {
            logger.error("Document [{}]: could not convert Gliffy diagram: Content of attachment [{}] is null",
                migratedDocRef, filename);
            return false;
        }

        JsonNode jsonRoot;
        try {
            jsonRoot = new ObjectMapper().readTree(attachmentContent.getContentInputStream());
        } catch (IOException e) {
            logger.warn("Document [{}]: could not convert Gliffy diagram: Could not parse JSON of "
                    + "attachment [{}]. Maybe this is not a Gliffy diagram after all",
                migratedDocRef, filename, e);
            return false;
        }

        if (convertGliffyDiagramNode(s, jsonRoot, migratedDocRef, baseURLsNotNull)) {
            try {
                attachmentContent.setContent(
                    new ByteArrayInputStream(new ObjectMapper().writeValueAsBytes(jsonRoot)));
            } catch (IOException e) {
                logger.error("Document [{}]: could not update the Gliffy diagram (attachment [{}])",
                    migratedDocRef, filename, e);
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean updateComments(Stats s, XWikiDocument migratedDoc, String syntaxId, EntityReference migratedDocRef,
        String[] baseURLsNotNull, BrokenRefType brokenRefType)
    {
        boolean updated = false;
        for (BaseObject comment : migratedDoc.getComments()) {
            String content = comment.getLargeStringValue(COMMENT);
            XDOM xdom = getCommentXDOM(syntaxId, content);
            if (xdom == null) {
                continue;
            }
            boolean commentUpdated = hasXDOMChangedAtParseTime(s, migratedDocRef, content, render(xdom, syntaxId),
                true);
            commentUpdated = commentUpdated || visitXDOMToFixRefs(s, xdom, syntaxId, migratedDocRef,
                baseURLsNotNull,
                brokenRefType);
            if (commentUpdated) {
                String newContent = render(xdom, syntaxId);
                comment.setLargeStringValue(COMMENT, newContent);
            }
            updated = updated || commentUpdated;
        }
        return updated;
    }

    private XDOM getCommentXDOM(String syntaxId, String content)
    {
        if (StringUtils.isEmpty(content)) {
            return null;
        }
        XDOM xdom = parse(content, syntaxId);
        if (xdom == null) {
            logger.warn("Failed to parse comment content, skipping.");
            return null;
        }
        return xdom;
    }

    private void maybeUpdateDocument(Stats s, XWikiDocument migratedDoc, XDOM xdom, String oldContent,
        boolean updateInPlace, boolean updated, boolean dryRun)
    {
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

        String newContent = migratedDoc.getContent();

        upd = hasXDOMChangedAtParseTime(s, migratedDocRef, oldContent, newContent, false) || upd;
        migrationFixingTools.handleDocumentUpdate(s, migratedDoc, upd, updateInPlace, dryRun, "Fix broken links");
    }

    private boolean hasXDOMChangedAtParseTime(Stats s, EntityReference migratedDocRef, String oldContent,
        String newContent, boolean inAComment)
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
            if (inAComment) {
                logger.info(CHANGED_AT_PARSE_TIME_MARKER,
                    "Document [{}] has changed at parse time ([{}] references updated) in a comment",
                    migratedDocRef, diff);
            } else {
                logger.info(CHANGED_AT_PARSE_TIME_MARKER,
                    "Document [{}] has changed at parse time ([{}] references updated)", migratedDocRef, diff);
            }
            s.incSuccessfulRefs(diff);
            return true;
        }
        return false;
    }

    private ResourceReference maybeConvertURLAsResourceRef(Stats s, String maybeURL, EntityReference migratedDocRef,
        String[] baseURLs, boolean asURL)
    {
        List<ConfluenceURLMapper> mappers = getConfluenceURLMappers(s, maybeURL, migratedDocRef);
        if (StringUtils.isEmpty(maybeURL) || mappers == null) {
            return null;
        }

        if (maybeURL.startsWith("/x/")) {
            String urlWithoutExtraSlashes = maybeURL.replaceAll("/+", SLASH);
            return maybeConvertURLAsResourceRef(s, urlWithoutExtraSlashes, migratedDocRef, "", mappers, asURL);
        }

        if (baseURLs.length == 0) {
            return null;
        }

        try {
            for (String baseURL : baseURLs) {
                if (!maybeURL.startsWith(baseURL)) {
                    continue;
                }

                return maybeConvertURLAsResourceRef(s, maybeURL, migratedDocRef, baseURL, mappers, asURL);
            }
        } catch (Exception e) {
            logger.error(FAILED_REFERENCE_CONVERSION_MARKER,
                "Document [{}]: Failed to convert Confluence URL [{}] because of an exception",
                migratedDocRef, maybeURL, e);
        }

        return null;
    }

    private ResourceReference maybeConvertURLAsResourceRef(Stats s, String maybeURL, EntityReference migratedDocRef,
        String baseURL, List<ConfluenceURLMapper> mappers, boolean asURL)
    {
        String url = StringUtils.removeStart(maybeURL, baseURL).replaceAll("^/+", "");
        String anchor = null;
        int anchorPos = url.indexOf('#');
        if (anchorPos != -1) {
            anchor = url.substring(anchorPos + 1);
            url = url.substring(0, anchorPos);
        }

        ResourceReference serializedEntity = maybeConvertURLAsResourceRef(url, migratedDocRef, mappers, asURL);
        if (serializedEntity == null) {
            logger.warn(FAILED_REFERENCE_CONVERSION_MARKER,
                "Document [{}]: Failed to convert Confluence URL [{}]", migratedDocRef, maybeURL);
            s.addFailedRef(maybeURL);
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
            s.addFailedRef(maybeURL);
        }
        return mappers;
    }

    private ResourceReference maybeConvertURLAsResourceRef(String url, EntityReference migratedDocRef,
        List<ConfluenceURLMapper> mappers, boolean asURL)
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
                ResourceReference serializedEntity = convertConvertURLAsResourceRef(url, migratedDocRef, mapper, m,
                    asURL);
                if (serializedEntity != null) {
                    return serializedEntity;
                }
            }
        }
        return null;
    }

    private ResourceReference convertConvertURLAsResourceRef(String url, EntityReference ref,
        ConfluenceURLMapper mapper, Matcher m, boolean asURL)
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
            if (asURL) {
                XWikiContext context = contextProvider.get();
                String convertedURL = context.getWiki().getURL(rr.getEntityReference(), context);
                return new ResourceReference(convertedURL, ResourceType.URL);
            }

            String serializedEntity = serializer.serialize(rr.getEntityReference(), ref);
            String type = rr.getEntityReference().getType().getLowerCase();
            if (DOCUMENT.equals(type)) {
                return new DocumentResourceReference(serializedEntity);
            }
            return new ResourceReference(serializedEntity, new ResourceType(type));
        }
        return null;
    }

    private ResourceReference maybeConvertUnprefixedBrokenLink(Stats s, String oldRef, EntityReference migratedDocRef,
        boolean isAttachment)
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
        String attachment = m.group("attachment");
        if (containsUnescapedChar(nameValidatedTitle, '.')
            || WEB_HOME.equals(nameValidatedTitle)
            || (isAttachment && StringUtils.isEmpty(attachment))
        ) {
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

            // The isAttachment check is there to ignore attach:filename.ext references, which would match the broken
            // links regex but are not to be converted.
            return null;
        }

        String space = m.group("space");

        EntityReference newRef = null;
        try {
            newRef = tryResolvingBrokenLinkDoc(nameValidatedTitle, space);
        } catch (ConfluenceResolverException e) {
            logger.error(EXCEPTION_WHILE_RESOLVING, migratedDocRef, oldRef, e);
        }

        if (newRef == null) {
            logger.warn(FAILED_REFERENCE_CONVERSION_MARKER, "Document [{}]: Failed to convert broken link [{}]",
                migratedDocRef, oldRef);
            s.addFailedRef(oldRef);
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
        boolean attachment = prefix.equals(ATTACH);
        ResourceReference newRef = maybeConvertUnprefixedBrokenLink(s, oldRef, ref, attachment);
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
        throws ConfluenceResolverException
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
        ResourceReference rr = maybeConvertReference(s, str, migratedDocRef, baseURLs, BrokenRefType.CONFLUENCE_REFS,
            false);
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

        if (type.equals(ResourceType.MAILTO) || type.equals(ResourceType.DATA)) {
            return null;
        }

        if (type.equals(ResourceType.URL)) {
            return maybeConvertURLAsResourceRef(s, reference.getReference(), migratedDocRef, baseURLs, false);
        }

        String scheme = type.getScheme();
        if (scheme.startsWith(CONFLUENCE)) {
            for (ConfluenceResourceReferenceType confluenceType : ConfluenceResourceReferenceType.values()) {
                String candidateType = confluenceType.getId();
                if (candidateType.equals(scheme)) {
                    return maybeConvertConfluenceReference(s, reference.getReference(), migratedDocRef, confluenceType);
                }
            }

            logger.warn("Document [{}]: unrecognized Confluence resource reference type [{}] for reference [{}]",
                migratedDocRef, scheme, reference);
        }

        boolean attachment = type.equals(ResourceType.ATTACHMENT);
        return maybeConvertReference(s, reference.getReference(), migratedDocRef, baseURLs, brokenRefType, attachment);
    }

    private ResourceReference maybeConvertReference(Stats s, String reference, EntityReference migratedDocRef,
        String[] baseURLs, BrokenRefType brokenRefType, boolean attachment)
    {
        // the attachment boolean is only relevent for BROKEN_LINKS BrokenRefType and should not be used for
        // CONFLUENCE_REFS

        if (StringUtils.isEmpty(reference)) {
            return null;
        }

        if (reference.startsWith("url:")) {
            return maybeConvertURLAsResourceRef(s, reference.substring(4), migratedDocRef, baseURLs, false);
        }

        ResourceReference res = maybeConvertURLAsResourceRef(s, reference, migratedDocRef, baseURLs, false);
        if (res != null) {
            return res;
        }

        if (brokenRefType == BrokenRefType.BROKEN_LINKS || brokenRefType == BrokenRefType.UNKNOWN) {
            String unprefixedRef = reference.substring(getReferencePrefixLength(reference));
            res = maybeConvertUnprefixedBrokenLink(s, unprefixedRef, migratedDocRef, attachment);
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
                "Document [{}]: Failed to convert Confluence reference [{}:{}] because of an exception",
                migratedDocRef, type.getId(), reference, e);
        }
        s.addFailedRef(type.getId() + ':' + reference);
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
