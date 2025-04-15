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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.descriptor.ContentDescriptor;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.script.service.ScriptService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentContent;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.diagram.script.DiagramScriptService;
/**
 * Confluence Reference Fixer.
 * @since 1.33.0
 * @version $Id$
 */
@Component(roles = DiagramConverter.class)
@Singleton
public class DiagramConverter
{
    private static final ClassBlockMatcher MACRO_MATCHER = new ClassBlockMatcher(MacroBlock.class);
    private static final String DIAGRAM_CLASS = "DiagramClass";
    private static final String FAILED_TO_UPDATE_DIAGRAM = "Document [{}]: failed to update diagram";
    private static final String DRAWIO = "drawio";
    private static final String GLIFFY = "gliffy";

    private static final String[] DIAGRAM_MACRO_NAMES = { GLIFFY, DRAWIO };

    private static final EntityReference DIAGRAM_CLASS_REFERENCE =
        new LocalDocumentReference("Diagram", DIAGRAM_CLASS);

    private static final EntityReference DIAGRAM_PRO_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("Confluence", "Macros"), DIAGRAM_CLASS);

    private static final TypeReference<Map<String, Map<String, Integer>>> MACRO_PAGES_TYPE_REF =
        new TypeReference<Map<String, Map<String, Integer>>>() { };

    private static final String CONFLUENCE_UNDERSCORE = "confluence_";
    private static final String DIAGRAM_NAME = "diagramName";

    @Inject
    private MigrationFixingTools migrationFixingTools;

    @Inject
    private Logger logger;

    @Inject
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("diagram")
    private ScriptService diagramService;

    /**
     * Fix broken references in all documents of the given space.
     * @param migrationReferences the migration documents
     * @param spaceReferences the spaces in which to fix the references
     * @param updateInPlace whether to update migrated documents with the fixed references in place instead of
     *                      creating a new revision.
     * @param dryRun true to simulate only (the fixed documents will not be saved)
     * @return the statistics of the reference fixing sessions
     */
    public MigrationFixingStats fixDocuments(List<EntityReference> migrationReferences,
        List<EntityReference> spaceReferences, boolean updateInPlace, boolean dryRun)
    {
        Stats s = new Stats();
        migrationFixingTools.fixDocuments(
            s,
            migrationReferences,
            spaceReferences,
            migratedDoc -> fixDocument(s, migratedDoc, updateInPlace, dryRun),
            migrationDoc -> fixDocumentsOfMigration(s, migrationDoc, updateInPlace, dryRun)
        );
        return s;
    }

    private void fixDocumentsOfMigration(Stats s, XWikiDocument migrationDoc, boolean updateInPlace, boolean dryRun)
    {
        Map<String, Map<String, Integer>> macroPages = null;
        XWikiAttachment macroPagesAttachment = migrationDoc.getAttachment("macroPages.json");
        if (macroPagesAttachment != null) {
            XWikiAttachmentContent attachmentContent = null;
            try {
                attachmentContent = macroPagesAttachment.getAttachmentContent(contextProvider.get());
            } catch (XWikiException e) {
                logger.error("Failed get macro pages data", e);
            }
            if (attachmentContent != null) {
                try {
                    InputStream contentInputStream = attachmentContent.getContentInputStream();
                    macroPages = new ObjectMapper().readValue(contentInputStream, MACRO_PAGES_TYPE_REF);
                } catch (IOException e) {
                    logger.error("Failed to read macro pages", e);
                }
            }
        }

        if (macroPages == null) {
            logger.warn("Failed to find the list of affected pages in macroPages.json, will browse all the documents");
            migrationFixingTools.fixDocumentsOfMigration(migrationDoc,
                migratedDoc -> fixDocument(s, migratedDoc, updateInPlace, dryRun));
        } else {
            List<String> docs = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> macroPageEntry : macroPages.entrySet()) {
                Map<String, Integer> macroInfo = macroPageEntry.getValue();
                if (hasDiagram(macroInfo)) {
                    docs.add(macroPageEntry.getKey());
                }
            }
            migrationFixingTools.fixDocuments(docs, migratedDoc -> fixDocument(s, migratedDoc, updateInPlace, dryRun));
        }
    }

    private static boolean hasDiagram(Map<String, Integer> macroInfo)
    {
        for (String macroName : DIAGRAM_MACRO_NAMES) {
            if (macroInfo.getOrDefault(CONFLUENCE_UNDERSCORE + macroName, 0) > 0
                || macroInfo.getOrDefault(macroName, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private static XDOM parse(ComponentManager componentManager, String text, String syntaxId)
    {
        XDOM result;
        try {
            Parser parser = componentManager.getInstance(Parser.class, syntaxId);
            result = parser.parse(new StringReader(text));
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    /**
     * @param macroBlock the macro to parse
     * @param syntaxId the syntax of the document
     * @return return the XDOM content of the given macro
     * @throws ComponentLookupException if something goes wrong when looking up the macro
     * @since 1.22.0
     */
    private XDOM getMacroXDOM(MacroBlock macroBlock, String syntaxId)
        throws ComponentLookupException
    {
        ComponentManager componentManager = componentManagerProvider.get();
        if (componentManager.hasComponent(Macro.class, macroBlock.getId())) {
            ContentDescriptor macroContentDescriptor =
                ((Macro<?>) componentManager.getInstance(Macro.class, macroBlock.getId()))
                    .getDescriptor()
                    .getContentDescriptor();

            if (macroContentDescriptor != null && macroContentDescriptor.getType().equals(Block.LIST_BLOCK_TYPE)
                && StringUtils.isNotBlank(macroBlock.getContent()))
            {
                return parse(componentManager, macroBlock.getContent(), syntaxId);
            }
        } else if (StringUtils.isNotBlank(macroBlock.getContent())) {
            // Just assume that the macro content is wiki syntax if we don't know the macro.
            logger.debug("Calling parse on unknown macro [{}] with syntax [{}]", macroBlock.getId(), syntaxId);
            return parse(componentManager, macroBlock.getContent(), syntaxId);
        }
        return null;
    }

    private boolean convertDiagramMacros(Stats s, XWikiDocument migratedDoc, XDOM xdom, String syntaxId, boolean dryRun)
    {
        boolean updated = false;
        List<MacroBlock> macros = xdom.getBlocks(MACRO_MATCHER, Block.Axes.DESCENDANT_OR_SELF);
        for (MacroBlock macroBlock : macros) {
            boolean mustConvert = false;
            for (String macroName : DIAGRAM_MACRO_NAMES) {
                String id = macroBlock.getId();
                if ((CONFLUENCE_UNDERSCORE + macroName).equals(id) || macroName.equals(id)) {
                    mustConvert = true;
                    break;
                }
            }

            try {
                if (mustConvert) {
                    updated = convertDiagramMacro(s, migratedDoc, dryRun, macroBlock) || updated;
                } else {
                    XDOM macroXDOM = getMacroXDOM(macroBlock, syntaxId);
                    if (macroXDOM != null) {
                        updated = convertDiagramMacros(s, migratedDoc, macroXDOM, syntaxId, dryRun) || updated;
                    }
                }
            } catch (ComponentLookupException e) {
                logger.error("Component lookup error trying to find the diagram macro", e);
            }
        }
        return updated;
    }

    private boolean convertDiagramMacro(Stats s, XWikiDocument migratedDoc, boolean dryRun, MacroBlock macroBlock)
    {
        boolean updated = false;
        try {
            if (convertDiagramMacro(migratedDoc, macroBlock, dryRun)) {
                updated = true;
                s.incSuccessfulDiagrams();
            } else {
                logger.error(FAILED_TO_UPDATE_DIAGRAM, migratedDoc.getDocumentReference());
                s.incFailedDiagrams();
            }
        } catch (XWikiException | IOException e) {
            logger.error(FAILED_TO_UPDATE_DIAGRAM, migratedDoc.getDocumentReference(), e);
            s.incFailedDiagrams();
        }
        return updated;
    }

    private boolean convertDiagramMacro(XWikiDocument migratedDoc, MacroBlock macroBlock, boolean dryRun)
        throws XWikiException, IOException
    {
        String diagramName = macroBlock.getParameter("name");
        if (StringUtils.isEmpty(diagramName)) {
            diagramName = macroBlock.getParameter(DIAGRAM_NAME);
        }

        String diagramPreview = macroBlock.getParameter("tempPreview");
        if (StringUtils.isEmpty(diagramPreview)) {
            diagramPreview = diagramName + ".png";
        }

        String diagramContent = getDiagramContent(migratedDoc, diagramName, macroBlock.getId());
        if (StringUtils.isEmpty(diagramContent)) {
            if (dryRun) {
                logger.warn("Document [{}]: would fail to convert convert diagram [{}]",
                    migratedDoc.getDocumentReference(), diagramName);
            }
            return false;
        }

        if (dryRun) {
            logger.info("Document [{}]: would successfully convert diagram [{}]", migratedDoc.getDocumentReference(),
                diagramName);
            return true;
        }

        XWikiContext context = contextProvider.get();
        XWiki wiki = context.getWiki();
        String targetDiagramName = diagramName;
        if (targetDiagramName.endsWith(".drawio")) {
            targetDiagramName = diagramName.substring(0, diagramName.length() - 7);
        }
        DocumentReference diagramReference = getDiagramReference(migratedDoc, targetDiagramName, wiki, context);

        XWikiDocument diagramDoc = wiki.getDocument(diagramReference, context);
        diagramDoc.newXObject(DIAGRAM_CLASS_REFERENCE, context);
        BaseObject diagramObject = diagramDoc.newXObject(DIAGRAM_PRO_CLASS_REFERENCE, context);
        diagramObject.setDBStringListValue("page",
            List.of(serializer.serialize(migratedDoc.getDocumentReference())));
        diagramObject.setStringValue(DIAGRAM_NAME, diagramReference.getName());
        diagramDoc.setTitle("Diagram " + targetDiagramName);
        diagramDoc.setContent(diagramContent);
        wiki.saveDocument(diagramDoc, context);
        String reference = serializer.serialize(new EntityReference(diagramReference.getName(), EntityType.DOCUMENT));
        Map<String, String> diagramParameters = Map.of("reference", reference, "cached", "false");
        macroBlock.getParent().replaceChild(
            List.of(new MacroBlock("diagram", diagramParameters, macroBlock.isInline())), macroBlock);
        return true;
    }

    private static DocumentReference getDiagramReference(XWikiDocument migratedDoc, String diagramName, XWiki wiki,
        XWikiContext context) throws XWikiException
    {
        EntityReference docSpace = migratedDoc.getDocumentReference().getParent();
        DocumentReference diagramReference = new DocumentReference(
            new EntityReference(diagramName, EntityType.DOCUMENT, docSpace));

        int i = 0;
        while (wiki.exists(diagramReference, context)) {
            String diagramDocumentName = diagramName + (++i);
            diagramReference = new DocumentReference(
                new EntityReference(diagramDocumentName, EntityType.DOCUMENT, docSpace));
        }
        return diagramReference;
    }

    private String getDiagramContent(XWikiDocument migratedDoc, String diagramName, String macroName)
        throws IOException, XWikiException
    {
        String diagramContent = "";
        XWikiAttachment diagramAttach = migratedDoc.getAttachment(diagramName);
        if (diagramAttach == null) {
            logger.error("Document [{}]: diagram attachment [{}] is missing",
                migratedDoc.getDocumentReference(), diagramName);
        } else {
            XWikiAttachmentContent attachmentContent = diagramAttach.getAttachmentContent(contextProvider.get());
            InputStream attachmentInputStream = attachmentContent.getContentInputStream();
            diagramContent = new String(attachmentInputStream.readAllBytes(), StandardCharsets.UTF_8);
            if (diagramContent.isEmpty()) {
                logger.error("Document [{}]: diagram attachment [{}] is empty",
                    migratedDoc.getDocumentReference(), diagramName);
            }
            if (!macroName.endsWith(DRAWIO)) {
                try {
                    return ((DiagramScriptService) diagramService).importDiagram(diagramContent, diagramName);
                } catch (Exception e) {
                    // It can be a com.google.gson.JsonSyntaxException / java.lang.NumberFormatException because our
                    // conversion code is old and doesn't manage newly created diagrams well
                    logger.error("Diagram conversion threw an exception", e);
                    return null;
                }
            }
        }
        return diagramContent;
    }

    private void fixDocument(Stats s, XWikiDocument migratedDoc, boolean updateInPlace, boolean dryRun)
    {
        XDOM xdom = migratedDoc.getXDOM();
        boolean updated = convertDiagramMacros(s, migratedDoc, xdom, migratedDoc.getSyntax().toIdString(), dryRun);
        if (updated && !dryRun) {
            try {
                migratedDoc.setContent(xdom);
            } catch (XWikiException e) {
                logger.error("Failed to update the document XDOM [{}]", migratedDoc.getDocumentReference(), e);
                s.incFailedDocs();
                return;
            }
        }
        migrationFixingTools.handleDocumentUpdate(s, migratedDoc, updated, updateInPlace, dryRun,
            "Convert Confluence diagrams");
    }

    private static final class Stats implements MigrationFixingStats
    {
        private int failedDocs;
        private int successfulDocs;
        private int unchangedDocs;
        private int successfulDiagrams;
        private int failedDiagrams;

        @Override
        public void incFailedDocs()
        {
            failedDocs++;
        }

        @Override
        public void incSuccessfulDocs()
        {
            successfulDocs++;
        }

        @Override
        public void incUnchangedDocs()
        {
            unchangedDocs++;
        }

        void incSuccessfulDiagrams()
        {
            successfulDiagrams++;
        }

        void incFailedDiagrams()
        {
            failedDiagrams++;
        }

        @Override
        public String toJSON()
        {
            return "{\"failedDocs\": " + failedDocs
                + ", \"successfulDocs\": " + successfulDocs
                + ", \"unchangedDocs\": " + unchangedDocs
                + ", \"successfulDiagrams\": " + successfulDiagrams
                + ", \"failedDiagrams\": " + failedDiagrams
                + "}";
        }
    }
}
