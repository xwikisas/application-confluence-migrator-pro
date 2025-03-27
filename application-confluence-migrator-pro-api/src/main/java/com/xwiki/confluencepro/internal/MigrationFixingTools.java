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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Tools to write post migration fixes.
 * @version $Id$
 * @since 1.33.0
 */
@Component (roles = MigrationFixingTools.class)
@Singleton
public class MigrationFixingTools
{
    private static final Marker UPDATED_MARKER = MarkerFactory.getMarker("confluencemigrationfixer.updated");
    private static final Marker UNCHANGED_MARKER = MarkerFactory.getMarker("confluencemigrationfixer.unchanged");
    private static final TypeReference<Map<String, Object>> INPUT_PROPERTIES_TYPE_REF =
        new TypeReference<Map<String, Object>>() { };

    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceResolver<String> resolver;

    @Inject
    private ConfluenceSpaceKeyResolver spaceKeyResolver;

    @Inject
    private Provider<EntityNameValidationManager> entityNameValidationManagerProvider;

    @Inject
    private JobProgressManager progressManager;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private QueryManager queryManager;

    /**
     * Fix the documents of this migration.
     * @param migrationDoc the migration document to consider
     * @param documentFixer the function to use to fix the documents of this migration
     */
    public void fixDocumentsOfMigration(XWikiDocument migrationDoc,
        Consumer<XWikiDocument> documentFixer)
    {
        Map<String, Object> inputProperties = getInputProperties(migrationDoc);

        EntityReference root;
        boolean guess;
        if (inputProperties == null) {
            guess = true;
            logger.warn("Missing input properties means we could not determine the root space of the migration [{}], "
                + "will attempt to guess.", migrationDoc.getDocumentReference());
            root = contextProvider.get().getWikiReference();
        } else {
            guess = false;
            root = computeRootSpace(inputProperties, resolver);
        }
        List<String> spaces = migrationDoc.getListValue("spaces");
        if (CollectionUtils.isEmpty(spaces)) {
            logger.warn("Migration document [{}]: Could not find any space to handle",
                migrationDoc.getDocumentReference());
        }

        EntityNameValidation nameStrategy = entityNameValidationManagerProvider.get().getEntityReferenceNameStrategy();

        for (String space : spaces) {
            logger.info("Browsing documents in space [{}]", space);
            EntityReference spaceReference = computeSpaceReference(space, nameStrategy, guess, root);
            if (spaceReference == null) {
                continue;
            }

            fixDocumentsInSpace(spaceReference, documentFixer);
        }
    }


    /**
     * Fix the documents in the given spaces.
     * @param spaceReferences the spaces in which to fix the docuemnts
     * @param migratedDocFixer the function to call to fix documents
     */
    public void fixDocumentsOfSpaces(List<EntityReference> spaceReferences, Consumer<XWikiDocument> migratedDocFixer)
    {
        if (CollectionUtils.isNotEmpty(spaceReferences)) {
            int size = spaceReferences.size();
            int n = 0;
            for (EntityReference spaceReference : spaceReferences) {
                progressManager.startStep(this);
                logger.info("Browsing documents of space [{}] ({}/{})", spaceReference, ++n, size);
                fixDocumentsInSpace(spaceReference, migratedDocFixer);
                progressManager.endStep(this);
            }
        }
    }

    /**
     * Fix the documents in the given migrations and spaces.
     * @param s the statistics to update
     * @param migrationReferences the migrations to consider
     * @param spaceReferences the spaces to consider
     * @param migratedDocFixer the function to call to fix documents
     * @param migrationFixer the function to call for each migration, which will need to browse documents and fix them
     */
    public void fixDocuments(MigrationFixingStats s, List<EntityReference> migrationReferences,
        List<EntityReference> spaceReferences, Consumer<XWikiDocument> migratedDocFixer,
        Consumer<XWikiDocument> migrationFixer)
    {
        int steps = ((migrationReferences == null ? 0 : migrationReferences.size())
            + (spaceReferences == null ? 0 : spaceReferences.size()));
        if (steps == 0) {
            logger.warn("There is nothing to fix");
            return;
        }
        progressManager.pushLevelProgress(steps, this);
        fixDocumentsOfMigrations(s, migrationReferences, migrationFixer);
        fixDocumentsOfSpaces(spaceReferences, migratedDocFixer);
        progressManager.popLevelProgress(this);
    }

    /**
     * Fix the documents in the given space.
     * @param spaceReference the space in which to fix the documents
     * @param migratedDocFixer the function to call to fix documents
     */
    public void fixDocumentsInSpace(EntityReference spaceReference, Consumer<XWikiDocument> migratedDocFixer)
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
                .bindValue("space", spaceRef)
                .execute();
        } catch (QueryException e) {
            logger.error("Failed to list the documents in space [{}], skipping.", spaceRef, e);
            return;
        }

        List<String> docs =
            docFullNames.stream().map(fullName -> wiki + ':' + fullName).collect(Collectors.toList());
        fixDocuments(docs, migratedDocFixer);
    }

    /**
     * Fix the given documents.
     * @param docRefs the references of documents to fix
     * @param documentFixer the function to use to fix the documents
     */
    public void fixDocuments(Collection<String> docRefs, Consumer<XWikiDocument> documentFixer)
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
            XWikiDocument migratedDoc = getDocument(migratedDocRef);
            if (migratedDoc == null) {
                continue;
            }
            documentFixer.accept(migratedDoc);
            progressManager.endStep(this);
        }
        progressManager.popLevelProgress(this);
    }

    /**
     * Handle the update of this document, and the associated logs.
     * @param s the migration fixing stats to update
     * @param updated whether the document is to be actually updated. If yes, the document will be saved.
     * @param migratedDoc the migrated document to update
     * @param updateInPlace whether to update in place
     * @param dryRun whether to fake the update
     * @param revisionComment the comment to use when saving the document
     */
    public void handleDocumentUpdate(MigrationFixingStats s, XWikiDocument migratedDoc, boolean updated,
        boolean updateInPlace, boolean dryRun, String revisionComment)
    {
        if (!updated) {
            logger.info(UNCHANGED_MARKER, "Document [{}] is left unchanged", migratedDoc.getDocumentReference());
            s.incUnchangedDocs();
            return;
        }

        DocumentReference migratedDocRef = migratedDoc.getDocumentReference();
        if (dryRun) {
            logger.info("Would update document [{}]", migratedDocRef);
            s.incSuccessfulDocs();
            return;
        }
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
            context.getWiki().saveDocument(migratedDoc, revisionComment, context);
            s.incSuccessfulDocs();
        } catch (XWikiException e) {
            logger.error("Failed to save document [{}]", migratedDocRef, e);
            s.incFailedDocs();
        }
    }

    /**
     * @param migrationDoc the migration to consider
     * @return the input properties of this migration
     */
    public Map<String, Object> getInputProperties(XWikiDocument migrationDoc)
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

    private EntityReference computeSpaceReference(String space, EntityNameValidation nameStrategy, boolean guess,
        EntityReference root)
    {
        EntityReference spaceReference = null;
        String validatedSpace = nameStrategy == null ? space : nameStrategy.transform(space);
        if (guess) {
            try {
                spaceReference = spaceKeyResolver.getSpaceByKey(space);
            } catch (ConfluenceResolverException e) {
                logger.error("Failed to resolve space [{}] using Confluence resolvers", space, e);
            }
        } else {
            spaceReference = new EntityReference(validatedSpace, EntityType.SPACE, root);
        }

        return spaceReference;
    }

    private EntityReference computeRootSpace(Map<String, Object> inputProperties,
        EntityReferenceResolver<String> resolver)
    {
        XWikiContext context = contextProvider.get();
        String rootSpaceStr = (String) inputProperties.get("root");
        if (StringUtils.isEmpty(rootSpaceStr)) {
            // Gracefully handle the deprecated property
            rootSpaceStr = (String) inputProperties.get("rootSpace");
        }

        if (StringUtils.isEmpty(rootSpaceStr)) {
            return context.getWikiReference();
        }

        if (rootSpaceStr.startsWith("wiki:")) {
            return new WikiReference(rootSpaceStr.substring(5));
        }

        if (rootSpaceStr.startsWith("space:")) {
            return resolver.resolve(rootSpaceStr.substring(6), EntityType.SPACE);
        }

        if (rootSpaceStr.endsWith(".WebHome")) {
            rootSpaceStr = rootSpaceStr.substring(0, rootSpaceStr.length() - 8);
        }

        return resolver.resolve(rootSpaceStr, EntityType.SPACE);
    }

    private void fixDocumentsOfMigrations(MigrationFixingStats s, List<EntityReference> migrationReferences,
        Consumer<XWikiDocument> migrationFixer)
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
            XWikiDocument doc = getDoc(s, migrationReference);
            if (doc != null) {
                migrationFixer.accept(doc);
            }
            progressManager.endStep(this);
        }
    }

    private XWikiDocument getDoc(MigrationFixingStats s, EntityReference migrationReference)
    {
        XWikiContext context = contextProvider.get();
        XWikiDocument migrationDoc;
        try {
            migrationDoc = context.getWiki().getDocument(migrationReference, context);
        } catch (XWikiException e) {
            logger.error("Failed to get the migration document [{}], skipping.", migrationReference, e);
            s.incFailedDocs();
            return null;
        }

        if (migrationDoc.isNew()) {
            logger.warn("Failed to find migration document [{}], skipping", migrationReference);
            s.incFailedDocs();
            return null;
        }

        return migrationDoc;
    }

    private XWikiDocument getDocument(EntityReference migratedDocRef)
    {
        try {
            XWikiContext context = contextProvider.get();
            XWikiDocument migratedDoc = context.getWiki().getDocument(migratedDocRef, context).clone();
            if (migratedDoc == null || migratedDoc.isNew()) {
                logger.error("The migrated document [{}] doesn't exist, skipping.", migratedDocRef);
                return null;
            }
            return  migratedDoc;
        } catch (XWikiException e) {
            logger.error("Failed to get the migrated document [{}], skipping.", migratedDocRef, e);
        }
        return null;
    }
}
