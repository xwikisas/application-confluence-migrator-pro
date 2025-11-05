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

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageTitleResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.logging.LogLevel;
import org.xwiki.logging.LoggerManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.script.service.ScriptService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Attachment;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.api.XWiki;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

/**
 * Metadata for Confluence Migrator Script Service.
 * @version $Id$
 * since 1.0.0
 */
@Component
@Singleton
@Named("confluencepro.metadataforconfluencemigrator")
public class MetadataForConfluenceMigrationScriptService implements ScriptService
{
    private static final String SPACE_KEY_PATTERN = Pattern.quote('$' + "{spaceKey}");
    private static final String SET_NAME_PATTERN = Pattern.quote('$' + "{setName}");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() { };
    private static final String YYYY_MM_DD = "yyyy-MM-dd";
    private static final String SELECT = "select";
    private static final String CHECKBOX = "CHECKBOX";
    private static final String DROPDOWN = "DROPDOWN";
    private static final String DATE = "DATE";
    private static final String TEXT = "TEXT";
    private static final String MIGRATION_CLASS = "ConfluenceMigratorPro.Metadata.Code.MigrationClass";

    private static final LocalDocumentReference PACKAGES_STORE_REF = new LocalDocumentReference(
        List.of("ConfluenceMigratorPro", "Metadata", "Code"), "PackagesStore");

    // FIXME: These static final classes shall be converted to records when we can use Java 17. This will also remove a
    //        bunch of sonar complaints.

    private static final class MetadataForConfluenceField
    {
        public String key;
        public String description;
        public String spaceKey;
        public String title;
        public String typeName;
        public String typeConfiguration;
    }

    private static final class MetadataForConfluenceFieldConfiguration
    {
        public String key;
        public boolean hidden;
        public boolean required;
        public Object defaultValue;
    }

    private static final class MetadataForConfluenceSet
    {
        public String key;
        public String description;
        public String spaceKey;
        public String title;
        public List<MetadataForConfluenceFieldConfiguration> metadataFields;
    }

    private static final class MetadataForConfluenceContentEntityObject
    {
        public String spaceKey;
        public String title;
        public boolean blogPost;
        public long blogPostPostingTimestamp;
        public Map<String, Object> metadataFieldValues;
    }

    private static final class MetadataForConfluenceValue
    {
        public String metadataSetKey;
        public String metadataSetSpaceKey;
        public List<MetadataForConfluenceContentEntityObject> contentEntityObjects;
    }

    private static final class MetadataForConfluenceExport
    {
        public List<MetadataForConfluenceField> metadataFields;
        public List<MetadataForConfluenceSet> metadataSets;
        public List<MetadataForConfluenceValue> metadataValues;
    }

    private static final class Stats
    {
        public long importedValues;
        public long importedSets;
        public long importedFields;
        public long failedValues;
        public long failedSets;
        public long skippedFields;
        public long unknownTypeFields;
        private final Collection<String> spaces;

        Stats()
        {
            this.spaces = new TreeSet<>();
        }
    }

    private final SimpleDateFormat metadataForConfluenceDateFormat = new SimpleDateFormat(YYYY_MM_DD);

    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    @Inject
    private LoggerManager loggerManager;

    @Inject
    private ConfluencePageTitleResolver confluencePageResolver;

    @Inject
    private ConfluenceSpaceKeyResolver confluenceSpaceKeyResolver;

    @Inject
    private JobProgressManager progress;

    /**
     * Migrate the provided json file.
     * @param migrationDoc the migration document
     * @param referenceTemplate the template to follow for the reference of the documents where the metadata sets
     *                          will be imported
     * @param titleTemplate the template to follow for the title of documents where the metadata sets will be imported
     */

    public void run(Document migrationDoc, String referenceTemplate, String titleTemplate)
    {
        com.xpn.xwiki.api.Object obj = migrationDoc.getObject(MIGRATION_CLASS);
        if (obj == null) {
            logger.error("Please provide a migration document with a [{}] object", MIGRATION_CLASS);
            return;
        }
        Object pn = obj.getValue("package");
        if (pn instanceof String) {
            String packageName = (String) pn;
            XWikiContext context = contextProvider.get();
            Document storeDoc;
            try {
                storeDoc = new Document(context.getWiki().getDocument(PACKAGES_STORE_REF, context), context);
            } catch (XWikiException e) {
                logger.error("Failed to get the store document", e);
                return;
            }
            loggerManager.setLoggerLevel(logger.getName(), LogLevel.INFO);
            Attachment attachment  = storeDoc.getAttachment(packageName);
            Stats s = new Stats();
            runMig(s, attachment, referenceTemplate, titleTemplate);
            try {
                byte[] stats = new ObjectMapper().writeValueAsString(s).getBytes(StandardCharsets.UTF_8);
                migrationDoc.addAttachment("stats.json", stats);
            } catch (JsonProcessingException e) {
                logger.error("Failed to save the migration statistics", e);
            }
            obj.set("executed", 1);
            obj.set("titleTemplate", titleTemplate);
            obj.set("referenceTemplate", referenceTemplate);
            obj.set("spaces", List.copyOf(s.spaces));
            try {
                migrationDoc.save("Update migration status");
            } catch (XWikiException e) {
                logger.error("Failed to update the migration status", e);
            }
        } else {
            logger.error("package must be a string");
        }
    }

    private void runMig(Stats s, Attachment attachment, String referenceTemplate, String titleTemplate)
    {
        String json;
        try {
            json = attachment.getContentAsString();
        } catch (XWikiException e) {
            logger.error("Failed to get the JSON attachment content", e);
            return;
        }
        MetadataForConfluenceExport data;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            data = objectMapper.readValue(json, MetadataForConfluenceExport.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse the JSON attachment content", e);
            return;
        }

        if (data.metadataFields == null || data.metadataFields.isEmpty()) {
            logger.error("Metadata fields are missing. We can't migrate this export.");
            return;
        }

        Map<String, MetadataForConfluenceField> fields = new TreeMap<>();
        for (MetadataForConfluenceField field : data.metadataFields) {
            fields.put(field.key, field);
        }

        importSetsAndValues(s, referenceTemplate, titleTemplate, data, fields);
    }

    private void importSetsAndValues(Stats s, String referenceTemplate, String titleTemplate,
        MetadataForConfluenceExport data, Map<String, MetadataForConfluenceField> fields)
    {
        boolean noSets = data.metadataSets == null || data.metadataSets.isEmpty();
        boolean noValues = data.metadataValues == null || data.metadataValues.isEmpty();
        int steps = 2 - (noValues ? 1 : 0) - (noSets ? 1 : 0);

        if (steps > 0) {
            progress.pushLevelProgress(steps, this);
        }

        Map<String, Map<String, String>> importedSetsPerSpace = Map.of();
        if (noSets) {
            logger.info("There are no metadata sets in this export.");
        } else {
            importedSetsPerSpace = importMetadataSets(s, data.metadataSets, fields, referenceTemplate, titleTemplate);
        }

        if (noValues) {
            logger.info("There are no metadata values in this export.");
        } else {
            importMetadataValues(s, importedSetsPerSpace, data.metadataValues, fields);
        }

        if (steps > 0) {
            progress.popLevelProgress(this);
        }
    }

    private void importMetadataValues(Stats s, Map<String, Map<String, String>> importedSetsPerSpace,
        List<MetadataForConfluenceValue> metadataValues, Map<String, MetadataForConfluenceField> fields)
    {
        int count = 0;
        for (MetadataForConfluenceValue value : metadataValues) {
            count += value.contentEntityObjects.size();
        }

        progress.pushLevelProgress(count, this);
        for (MetadataForConfluenceValue value : metadataValues) {
            importContentEntityObjects(s, importedSetsPerSpace, fields, value);
        }
        progress.popLevelProgress(this);
    }

    private void importContentEntityObjects(Stats s, Map<String, Map<String, String>> importedSetsPerSpace,
        Map<String, MetadataForConfluenceField> fields, MetadataForConfluenceValue value)
    {
        for (MetadataForConfluenceContentEntityObject o : value.contentEntityObjects) {
            progress.startStep(this);
            s.spaces.add(o.spaceKey);
            Document valueDoc = getValueDoc(o);
            if (valueDoc == null) {
                s.failedValues++;
            } else {
                Map<String, String> sets = importedSetsPerSpace.get(value.metadataSetSpaceKey);
                String setRef = sets == null ? null : sets.get(value.metadataSetKey);
                if (setRef == null) {
                    logger.warn("Set [{}] in space [{}] could not be found. Skipping.",
                        value.metadataSetKey, value.metadataSetSpaceKey);
                    s.failedValues++;
                } else {
                    addValues(s, fields, o, valueDoc, setRef);
                    save(s, valueDoc);
                }
            }
            progress.endStep(this);
        }
    }

    private void addValues(Stats s, Map<String, MetadataForConfluenceField> fields,
        MetadataForConfluenceContentEntityObject o, Document valueDoc, String setRef)
    {
        com.xpn.xwiki.api.Object object = valueDoc.getObject(setRef, true);
        if (o.metadataFieldValues == null || o.metadataFieldValues.isEmpty()) {
            logger.info("There are no values for set [{}] in this export", setRef);
            return;
        }

        logger.info("Setting values in [{}] for set [{}]", valueDoc.getDocumentReference(), setRef);
        for (Map.Entry<String, Object> field : o.metadataFieldValues.entrySet()) {
            String fieldName = field.getKey();
            String xwikiFieldName = getXWikiFieldName(fieldName);
            Object val = field.getValue();
            if (val != null) {
                MetadataForConfluenceField fieldDescriptor = fields.get(field.getKey());
                if (fieldDescriptor == null) {
                    logger.error("Failed to find field [{}] on [{}] for class [{}], value will be skipped",
                        field.getKey(), valueDoc.getDocumentReference(), setRef);
                    s.failedValues++;
                } else {
                    val = parseVal(field, val, fieldDescriptor, valueDoc.getDocumentReference(), setRef);
                    object.set(xwikiFieldName, val);
                }
            }
        }
    }

    private void save(Stats s, Document valueDoc)
    {
        try {
            valueDoc.save("Import values from Metadata for Confluence");
            s.importedValues++;
        } catch (XWikiException e) {
            logger.error("Failed to save document [{}], values won't be imported",
                valueDoc.getDocumentReference());
            s.failedValues++;
        }
    }

    private Document getValueDoc(MetadataForConfluenceContentEntityObject o)
    {
        EntityReference valueDocRef;
        try {
            valueDocRef = confluencePageResolver.getDocumentByTitle(o.spaceKey, o.title);
            if (valueDocRef == null) {
                logger.warn("Failed to find Confluence document with spaceKey [{}]  and title [{}],"
                    + " values for this page will not be imported", o.spaceKey, o.title);
                return null;
            }
        } catch (ConfluenceResolverException e) {
            logger.error("Failed to resolve Confluence page space=[{}] title=[{}],"
                + " values for this page won't be imported", o.spaceKey, o.title, e);
            return null;
        }
        XWikiContext context = contextProvider.get();
        XWiki wiki = new XWiki(context.getWiki(), context);
        Document valueDoc;
        try {
            valueDoc = wiki.getDocument(valueDocRef);
        } catch (XWikiException e) {
            logger.error("Failed to get the migrated Confluence document [{}],"
                + " values for this document won't be imported", valueDocRef, e);
            return null;
        }
        return valueDoc;
    }

    private Object parseVal(Map.Entry<String, Object> field, Object val, MetadataForConfluenceField fieldDescriptor,
        EntityReference valueDocRef, String setRef)
    {
        try {
            switch (fieldDescriptor.typeName) {
                case CHECKBOX:
                    return new ObjectMapper().readValue((String) val, STRING_LIST_TYPE);
                case DROPDOWN:
                    List<String> v = new ObjectMapper().readValue((String) val, STRING_LIST_TYPE);
                    if (v.size() > 1) {
                        return v;
                    }

                    return v.get(0);
                case DATE:
                    return metadataForConfluenceDateFormat.parse(
                        StringUtils.removeEnd(((String) val).trim(), "+").trim());
                case TEXT:
                    return escapeXWiki21((String) val);
                default:
                    logger.warn("Unhandled field type [{}], the imported value might be wrong",
                        fieldDescriptor.typeName);
                    return val;
            }
        } catch (JsonProcessingException | ParseException e) {
            logger.error("Could not parse value [{}] for field [{}] in [{}], set [{}], the "
                + "stored value might be wrong", val, field.getKey(), valueDocRef, setRef);
        }

        return val;
    }

    private String escapeXWiki21(String content)
    {
        // Inspired of org.xwiki.rendering.internal.util.XWikiSyntaxEscaper#escape(String, Syntax)
        char[] result = new char[content.length() * 2];
        for (int i = 0; i < content.length(); i++) {
            result[2 * i] = '~';
            result[2 * i + 1] = content.charAt(i);
        }
        return String.valueOf(result);
    }

    private Map<String, Map<String, String>> importMetadataSets(Stats s, List<MetadataForConfluenceSet> metadataSets,
        Map<String, MetadataForConfluenceField> fields, String referenceTemplate, String titleTemplate)
    {
        // Maps space keys to set key -> set reference maps
        Map<String, Map<String, String>> importedSetsPerSpace = new HashMap<>(fields.size());
        progress.pushLevelProgress(metadataSets.size(), this);
        for (MetadataForConfluenceSet set : metadataSets) {
            progress.startStep(this);
            importMetadataSet(s, importedSetsPerSpace, set, fields,  referenceTemplate, titleTemplate);
            progress.endStep(this);
        }
        progress.popLevelProgress(this);

        return importedSetsPerSpace;
    }

    private void importMetadataSet(Stats s, Map<String, Map<String, String>> importedSetsPerSpace,
        MetadataForConfluenceSet set, Map<String, MetadataForConfluenceField> fields, String referenceTemplate,
        String titleTemplate)
    {
        String setReferenceString = applyTemplate(referenceTemplate, set);
        if (StringUtils.isEmpty(setReferenceString)) {
            setReferenceString = "MetadataPro.Sets." + getSetName(set);
        }

        DocumentReference setReference = resolver.resolve(setReferenceString);

        Document setDoc = getSetDocument(s, set, titleTemplate, setReference);
        if (setDoc == null) {
            return;
        }

        BaseClass baseClass = setDoc.getxWikiClass().getXWikiClass();

        Map<String, List<?>> defaultValues = new TreeMap<>();
        Collection<String> requiredFields = new TreeSet<>();
        long importedFields = 0;
        for (MetadataForConfluenceFieldConfiguration fieldConf : set.metadataFields) {
            MetadataForConfluenceField field = fields.get(fieldConf.key);
            if (field == null) {
                logger.warn("Could not find field [{}] when importing set [{}]. Skipping.", fieldConf.key, set.key);
                s.skippedFields++;
            } else {
                String xwikiFieldName = getXWikiFieldName(fieldConf.key);
                if (addField(s, baseClass, xwikiFieldName, field, fieldConf)) {
                    logger.info("Imported field [{}] to [{}] as [{}]", fieldConf.key, setReference, xwikiFieldName);
                    importedFields++;
                } else {
                    logger.warn("Field [{}] in [{}] as [{}] already exists, it will be updated",
                        fieldConf.key, setReference, xwikiFieldName);
                    s.skippedFields++;
                }
                if (fieldConf.required) {
                    requiredFields.add(xwikiFieldName);
                }
                List<?> d = getDefaultValues(fieldConf.defaultValue);
                if (d != null && !d.isEmpty()) {
                    defaultValues.put(xwikiFieldName, d);
                }
            }
        }

        Map<String, String> sets =
            importedSetsPerSpace.computeIfAbsent(set.spaceKey, k -> new HashMap<>());
        sets.put(set.key, setReferenceString);

        saveSet(s, set, setDoc, setReference, requiredFields, defaultValues, importedFields);
    }

    private Document getSetDocument(Stats s, MetadataForConfluenceSet set, String titleTemplate,
        DocumentReference setReference)
    {
        String title = applyTemplate(titleTemplate, set);
        if (StringUtils.isEmpty(title)) {
            title = getSetName(set);
        }

        logger.info("Importing set [{}] to [{}] with title [{}]", set.key, setReference, title);

        XWikiContext context = contextProvider.get();

        Document setDoc;
        try {
            setDoc = new Document(context.getWiki().getDocument(setReference, context), context);
            setDoc.setTitle(title);
        } catch (XWikiException e) {
            logger.error("Failed to get the document for storing the set [{}]. Skipping.", setReference, e);
            s.failedSets++;
            return null;
        }

        if (!setDoc.isNew()) {
            logger.warn("Set [{}] already exists, it will be updated", setReference);
        }

        return setDoc;
    }

    private void saveSet(Stats s, MetadataForConfluenceSet set, Document setDoc, DocumentReference setReference,
        Collection<String> requiredFields, Map<String, List<?>> defaultValues, long importedFields)
    {
        com.xpn.xwiki.api.Object object = setDoc.getObject("MetadataPro.Code.MetadataSetClass", true);

        EntityReference space = null;
        try {
            space = confluenceSpaceKeyResolver.getSpaceByKey(set.spaceKey);
        } catch (ConfluenceResolverException e) {
            logger.error("Failed to resolve space [{}]", set.spaceKey, e);
        }

        if (space == null) {
            logger.warn("Failed to resolve space [{}], set [{}] will not be restricted to this space", set.spaceKey,
                setReference);
        } else {
            object.set("includedSpaces", List.of(serializer.serialize(space)));
        }

        object.set("requiredFields", List.copyOf(requiredFields));

        try {
            object.set("defaultValues", new ObjectMapper().writeValueAsString(defaultValues));
        } catch (JsonProcessingException e) {
            logger.error("Failed to write the default values. Fields won't have default values.");
        }

        try {
            setDoc.save("Import set from Metadata for Confluence");
            s.importedSets++;
            s.importedFields += importedFields;
        } catch (XWikiException e) {
            logger.error("Failed to save set [{}]. Skipping.", setReference);
            s.failedSets++;
            s.skippedFields += importedFields;
        }
    }

    private static String getXWikiFieldName(String fieldName)
    {
        return StringUtils.removeStart(fieldName, "metadatafield.");
    }

    private boolean addField(Stats s, BaseClass baseClass, String fieldName, MetadataForConfluenceField field,
        MetadataForConfluenceFieldConfiguration fieldConf)
    {
        switch (field.typeName) {
            case CHECKBOX: return addStaticListField(baseClass, fieldName, field, fieldConf, CHECKBOX, true);
            case DROPDOWN: return addStaticListField(baseClass, fieldName, field, fieldConf, SELECT, false);
            case DATE: return baseClass.addDateField(fieldName, field.title, YYYY_MM_DD, 0);
            case TEXT: return baseClass.addTextAreaField(fieldName, field.title, 80, 15,
                TextAreaClass.EditorType.WYSIWYG, TextAreaClass.ContentType.WIKI_TEXT);
            default:
                if (baseClass.addTextField(fieldName, field.title, 20)) {
                    logger.warn("Unhandled field type [{}], falling back to small string", field.typeName);
                    s.unknownTypeFields++;
                    return true;
                }
                return false;
        }
    }

    private static List<?> getDefaultValues(Object d)
    {
        if (d == null) {
            return null;
        }

        if (d instanceof String) {
            try {
                return new ObjectMapper().readValue((String) d, STRING_LIST_TYPE);
            } catch (JsonProcessingException e) {
                // ignore
            }
        }

        return List.of(d);
    }

    private boolean addStaticListField(BaseClass baseClass, String fieldName, MetadataForConfluenceField field,
        MetadataForConfluenceFieldConfiguration fieldConf, String displayType, boolean multiSelect)
    {
        List<?> defValue = getDefaultValues(fieldConf.defaultValue);
        List<String> values;
        try {
            values = new ObjectMapper().readValue(field.typeConfiguration, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse list of values for field [{}]. Skipping.", fieldConf.key);
            values = List.of();
        }
        String sep = findSep(values);
        String vals = String.join(sep, values);
        String first = null;
        Object firstDefVal = (defValue == null || defValue.isEmpty()) ? null : defValue.get(0);
        if (firstDefVal != null) {
            first = firstDefVal instanceof String ? (String) firstDefVal : firstDefVal.toString();
        }
        return baseClass.addStaticListField(fieldName, field.title, SELECT.equals(displayType) ? 1 : 20,
            multiSelect, false, vals, displayType, sep, first);
    }

    /**
     * @return a separator that's not used in any value (best-effort)
     * @param values the values to consider
     */
    private String findSep(List<String> values)
    {
        return findSep(values, "|;,/^:&+ ", 0);
    }

    private String findSep(List<String> values, String candidateSeparators, int i)
    {
        if (i >= candidateSeparators.length()) {
            // none of the candidate separators could be found, we'll just find whichever string of '+'s suffices
            return findSepPlus(values, "++");
        }

        char candidateSeparator = candidateSeparators.charAt(i);

        for (String v : values) {
            if (StringUtils.isNotEmpty(v) && v.indexOf(candidateSeparator) != -1) {
                return findSep(values, candidateSeparators, i + 1);
            }
        }

        return String.valueOf(candidateSeparator);
    }

    private String findSepPlus(List<String> values, String candidateSeparator)
    {
        for (String v : values) {
            if (StringUtils.isNotEmpty(v) && v.contains(candidateSeparator)) {
                return findSepPlus(values, candidateSeparator + '+');
            }
        }

        return candidateSeparator;
    }

    private String applyTemplate(String template, MetadataForConfluenceSet set)
    {
        if (StringUtils.isEmpty(template)) {
            return null;
        }

        String setName = getSetName(set);
        return template.replaceAll(SPACE_KEY_PATTERN, set.spaceKey).replaceAll(SET_NAME_PATTERN, setName);
    }

    private static String getSetName(MetadataForConfluenceSet set)
    {
        return StringUtils.removeStart(set.key, "metadataset.").replace('.', '_');
    }
}
