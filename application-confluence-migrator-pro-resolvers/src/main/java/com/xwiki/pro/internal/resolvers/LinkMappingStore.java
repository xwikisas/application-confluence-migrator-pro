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
package com.xwiki.pro.internal.resolvers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.store.DatabaseProduct;
import com.xpn.xwiki.store.XWikiHibernateStore;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Link mapping store.
 * @since 1.28.0
 * @version $Id$
 */
@Component (roles = LinkMappingStore.class)
@Singleton
public class LinkMappingStore implements Initializable
{
    private static final List<DatabaseProduct> DATABASES_SUPPORTING_BIGINT = List.of(
        DatabaseProduct.POSTGRESQL,
        DatabaseProduct.HSQLDB,
        DatabaseProduct.H2,
        DatabaseProduct.MYSQL,
        DatabaseProduct.DB2,
        DatabaseProduct.DERBY,
        DatabaseProduct.MSSQL
    );

    private static final TypeReference<Map<String, String>> LM_TYPE_REF = new TypeReference<Map<String, String>>() { };
    private static final String IDS_SUFFIX = ":ids";
    private static final String CREATE_TABLE = "create table if not exists %s (%s, reference VARCHAR(768))";

    private static final String TABLE_BY_TITLE = "confluencepro_linkmapping_by_title";
    private static final String TABLE_BY_ID = "confluencepro_linkmapping_by_id";
    private static final String DELETE_FROM = "delete from ";
    private static final String INSERT_INTO = "insert into ";
    private static final String SELECT_REFERENCE_FROM = "select reference from ";
    private static final String WHERE_PAGE_ID = " where pageId = ?";
    private static final String WHERE_SPACE_KEY_AND_PAGE_TITLE = " where spaceKey = ? and pageTitle = ?";
    private static final String DROP_TABLE = "drop table ";
    private static final String SELECT_1_FROM = "select 1 from ";
    private static final String LIMIT_1 = " limit 1";
    private static final String SPACES = "spaces";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Logger logger;

    @Inject
    private QueryManager queryManager;

    private boolean initialized;

    private boolean dontExist;

    private boolean needsConversion = true;

    @Override
    public void initialize()
    {
        if (contextProvider.get() != null) {
            try {
                convertOldMappings();
            } catch (Exception e) {
                logger.error("Failed to convert old mappings. Link mapping issues may arise. ", e);
            }
        }
    }

    private boolean areTableAbsent()
    {
        boolean res;
        Session session = beginTransaction();
        try {
            res = areTableAbsent(session);
        } finally {
            endTransaction(false);
        }
        return res;
    }

    private boolean areTableAbsent(Session session)
    {
        if (dontExist) {
            return true;
        }

        if (initialized) {
            return false;
        }

        boolean found = session.createNativeQuery(
                "select 1 from information_schema.tables where lower(table_name) = '" + TABLE_BY_ID + "' "
                    + "and lower(table_schema) in ('xwiki', 'app', 'public')")
                .getResultStream().findFirst().isPresent();
        // we want to check that the existing table is only on the main wiki. On postgresql, table_schema is set to
        // public. On database not based on catalogs, the schema is xwiki.
        // We need to do this because former versions of Confluence Migrator Pro incorrectly created the table in
        // subwikis under certain conditions, including when running it on a subwiki.

        if (found) {
            initialized = true;
            return false;
        }
        dontExist = true;
        return true;
    }

    /**
     * Begin a hibernate transaction.
     * @return a Session
     */
    public Session beginTransaction()
    {
        XWikiContext context = contextProvider.get();
        try {
            XWikiHibernateStore store = XWiki.getMainXWiki(context).getHibernateStore();
            if (store.beginTransaction(context)) {
                return store.getSession(context);
            }
        } catch (XWikiException e) {
            logger.error("Failed to begin a transaction", e);
        }
        return null;
    }

    /**
     * End the current hibernate transaction.
     * @param commit whether the transaction should be committed
     */
    public void endTransaction(boolean commit)
    {
        XWikiContext context = contextProvider.get();
        XWikiHibernateStore store = context.getWiki().getHibernateStore();
        store.endTransaction(context, commit);
    }

    private void createTableIfNotExists(Session session)
    {
        if (!areTableAbsent(session)) {
            return;
        }

        String sqlLong = getSQLLongType(contextProvider.get().getWiki().getHibernateStore().getDatabaseProductName());

        session.createNativeQuery(String.format(
            CREATE_TABLE,
            TABLE_BY_ID,
            "pageId " + sqlLong + " not null unique")).executeUpdate();

        session.createNativeQuery(String.format(
            CREATE_TABLE,
            TABLE_BY_TITLE,
            "spaceKey varchar(255), pageTitle varchar(255)")).executeUpdate();

        session.createNativeQuery(
            "create index confluencepro_linkmapping_spacekey_idx on " + TABLE_BY_TITLE + " (spaceKey)"
            ).executeUpdate();

        dontExist = false;
        initialized = true;
    }

    private String getSQLLongType(DatabaseProduct databaseProductName)
    {
        if (DATABASES_SUPPORTING_BIGINT.contains(databaseProductName)
            || "MariaDB".equals(databaseProductName.getProductName())
        ) {
            return "bigint";
        }

        if (databaseProductName.equals(DatabaseProduct.ORACLE)) {
            return "number(19,0)";
        }

        return "numeric(19,0)";
    }

    String get(long pageId)
    {
        if (needsConversion) {
            convertOldMappings();
        }

        if (areTableAbsent()) {
            return null;
        }

        String ref;
        Session session = beginTransaction();
        try {
            ref = getOneString(
                session.createNativeQuery(SELECT_REFERENCE_FROM + TABLE_BY_ID + WHERE_PAGE_ID)
                    .setParameter(1, pageId));
        } finally {
            endTransaction(false);
        }
        return ref;
    }

    String get(String spaceKey, String pageTitle)
    {
        if (needsConversion) {
            convertOldMappings();
        }

        if (areTableAbsent()) {
            return null;
        }

        String ref;
        Session session = beginTransaction();
        try {
            ref = getOneString(session.createNativeQuery(
                    SELECT_REFERENCE_FROM + TABLE_BY_TITLE + WHERE_SPACE_KEY_AND_PAGE_TITLE)
                .setParameter(1, spaceKey)
                .setParameter(2, pageTitle));
        } finally {
            endTransaction(false);
        }
        return ref;
    }

    String getSpaceForReference(String reference)
    {
        if (needsConversion) {
            convertOldMappings();
        }

        if (areTableAbsent()) {
            return null;
        }

        String ref;
        Session session = beginTransaction();
        try {
            ref = getOneString(session.createNativeQuery(
                    "select spaceKey from " + TABLE_BY_TITLE + " where reference = ?")
                .setParameter(1, reference));
        } finally {
            endTransaction(false);
        }
        return ref;
    }

    String getShortestReferenceForSpace(String spaceKey)
    {
        if (needsConversion) {
            convertOldMappings();
        }

        Session session = beginTransaction();
        try {
            if (areTableAbsent(session)) {
                return null;
            }

            return getOneString(session.createNativeQuery(
                    "select reference from (select reference from " + TABLE_BY_TITLE
                        + " where spaceKey = ? order by length(reference) asc limit 1) sub")
                .setParameter(1, spaceKey));
        } finally {
            endTransaction(false);
        }
    }

    String getShortestReferenceForSpaceByReference(String reference)
    {
        if (needsConversion) {
            convertOldMappings();
        }

        if (areTableAbsent()) {
            return null;
        }

        String ref;
        Session session = beginTransaction();
        try {
            ref = getOneString(session.createNativeQuery(
                    SELECT_REFERENCE_FROM + TABLE_BY_TITLE + " where spaceKey in (select spaceKey from "
                        + TABLE_BY_TITLE + " where reference = ? limit 1) order by length(reference) asc limit 1")
                .setParameter(1, reference));
        } finally {
            endTransaction(false);
        }
        return ref;
    }

    private static String getOneString(org.hibernate.query.Query<?> q)
    {
        Optional<?> res = q.getResultStream().findFirst();
        if (res.isPresent()) {
            Object r = res.get();
            if (r instanceof String) {
                return (String) r;
            }
        }
        return null;
    }

    /**
     * Remove spaces from the link mapping. This is useful when the information is elsewhere, for instance in
     * ConfluencePageClass objects.
     * @param spaces the space to remove from the link mapping.
     */
    public void removeSpaces(Collection<String> spaces)
    {
        if (areTableAbsent()) {
            return;
        }

        Session session = beginTransaction();
        try {
            session.createNativeQuery(
                    DELETE_FROM + TABLE_BY_ID + " where reference in (select reference from " + TABLE_BY_TITLE
                        + " where spaceKey in (:spaces))")
                .setParameterList(SPACES, spaces)
                .executeUpdate();

            session.createNativeQuery(DELETE_FROM + TABLE_BY_TITLE + " where spaceKey in (:spaces)")
                .setParameter(SPACES, spaces)
                .executeUpdate();

            maybeDropTables(session);
        } finally {
            endTransaction(true);
        }
    }

    /**
     * If there is no link mapping stored in the tables, remove them. This makes resolving Confluence pages faster
     * and helps clean up temporary data that should not be kept in the wiki forever.
     * @param session the session to use
     */
    private void maybeDropTables(Session session)
    {
        if (session.createNativeQuery(SELECT_1_FROM + TABLE_BY_ID + LIMIT_1)
            .getResultStream()
            .findFirst()
            .isPresent()) {
            return;
        }

        if (session.createNativeQuery(SELECT_1_FROM + TABLE_BY_TITLE + LIMIT_1)
            .getResultStream()
            .findFirst()
            .isPresent()) {
            return;
        }

        empty(session);
    }

    /**
     * Drop the link mapping tables.
     */
    public void empty()
    {
        Session session = beginTransaction();
        try {
            empty(session);
        } finally {
            endTransaction(true);
        }
    }

    /**
     * @return the number of entries.
     */
    public long getEntryCount()
    {
        long res = 0;
        Session session = beginTransaction();
        try {
            if (!areTableAbsent(session)) {
                res += getCount(session, TABLE_BY_ID);
                res += getCount(session, TABLE_BY_TITLE);
            }
        } finally {
            endTransaction(true);
        }
        return res;
    }

    private long getCount(Session session, String table)
    {
        Optional<?> r = session.createNativeQuery("select count(reference) from " + table)
            .getResultStream()
            .findFirst();
        if (r.isPresent()) {
            Object res = r.get();
            if (res instanceof Number) {
                return ((Number) res).longValue();
            }
        }
        logger.error("Could not count entries in " + TABLE_BY_ID);
        return 0;
    }

    private void empty(Session session)
    {
        session.createNativeQuery(DROP_TABLE + TABLE_BY_ID).executeUpdate();
        session.createNativeQuery(DROP_TABLE + TABLE_BY_TITLE).executeUpdate();

        dontExist = true;
    }

    /**
     * Add the following page to the link mapping table.
     * @param session a hibernate session retrieved using #beginTransaction()
     * @param pageId the page id
     * @param spaceKey the space key
     * @param pageTitle the page title
     * @param reference the XWiki reference
     */
    public void add(Session session, long pageId, String spaceKey, String pageTitle, EntityReference reference)
    {
        String ref = serializer.serialize(reference);
        add(session, pageId, ref);
        add(session, spaceKey, pageTitle, ref);
    }

    /**
     * Add the following page to the link mapping table.
     * @param session a hibernate session retrieved using #beginTransaction()
     * @param pageId the page id
     * @param reference the XWiki reference
     */
    public void add(Session session, long pageId, String reference)
    {
        createTableIfNotExists(session);

        session.createNativeQuery(DELETE_FROM + TABLE_BY_ID + WHERE_PAGE_ID)
            .setParameter(1, pageId)
            .executeUpdate();

        session.createNativeQuery(INSERT_INTO + TABLE_BY_ID + " (pageId, reference) values (?,?)")
            .setParameter(1, pageId)
            .setParameter(2, reference)
            .executeUpdate();
    }

    /**
     * Add the following page to the link mapping table.
     * @param session a hibernate session retrieved using #beginTransaction()
     * @param spaceKey the space key
     * @param pageTitle the page title
     * @param reference the XWiki reference
     */
    public void add(Session session, String spaceKey, String pageTitle, String reference)
    {
        createTableIfNotExists(session);

        session.createNativeQuery(DELETE_FROM + TABLE_BY_TITLE + WHERE_SPACE_KEY_AND_PAGE_TITLE)
            .setParameter(1, spaceKey)
            .setParameter(2, pageTitle)
            .executeUpdate();

        session.createNativeQuery(INSERT_INTO + TABLE_BY_TITLE + " (spaceKey, pageTitle, reference) values(?,?,?)")
            .setParameter(1, spaceKey)
            .setParameter(2, pageTitle)
            .setParameter(3, reference)
            .executeUpdate();
    }

    private void convertOldMappings()
    {
        List<Object[]> res = getOldMappings();
        if (res == null || res.isEmpty()) {
            needsConversion = false; return;
        }
        List<XWikiDocument> documentsToDelete = new ArrayList<>(res.size());
        Session session = beginTransaction();
        try {
            logger.info("Migrating old link mapping documents to the new SQL storage");
            int i = 0;
            for (Object[] line : res) {
                i++;
                XWikiDocument d = (XWikiDocument) line[0];
                logger.info("Converting [{}] ({}/{})", d, i, res.size());
                String spaceKey = getSpaceKey(line);
                if (isSpaceFoundInConfluencePageClassObj(spaceKey)) {
                    logger.info("ConfluencePageClass objects found for the related space, skipping import of [{}]", d);
                    documentsToDelete.add(d);
                } else {
                    try {
                        boolean isPageIdMapping = d.getDocumentReference().getName().endsWith(IDS_SUFFIX);
                        parseOldMapping(session, (String) line[2], spaceKey, isPageIdMapping);
                        documentsToDelete.add(d);
                    } catch (Exception e) {
                        logger.error("Failed to convert [{}], the document will not be removed", d);
                    }
                }
            }
        } finally {
            endTransaction(true);
        }
        removeOldMappings(documentsToDelete);
        needsConversion = false;
    }

    private void removeOldMappings(List<XWikiDocument> documentsToDelete)
    {
        XWikiContext context = contextProvider.get();
        logger.info("Removing the [{}] old link mapping documents we managed to convert", documentsToDelete.size());
        int i = 0;
        for (XWikiDocument notLoadedDocument : documentsToDelete) {
            i++;
            logger.info("Removing [{}] ({}/{})", notLoadedDocument, i, documentsToDelete.size());
            try {
                // We can't add the XWikiDocument we have directly because it's not loaded, we need to load it
                XWikiDocument d = context.getWiki().getDocument(notLoadedDocument.getDocumentReference(), context);
                context.getWiki().deleteDocument(d, context);
            } catch (XWikiException e) {
                logger.error("Could not remove old mapping document [{}]", notLoadedDocument, e);
            }
        }
        logger.info("Finished removing old link mapping documents");
    }

    private static String getSpaceKey(Object[] line)
    {
        String spaceKey = (String) line[1];
        if (spaceKey.endsWith(IDS_SUFFIX)) {
            spaceKey = spaceKey.substring(0, spaceKey.length() - 4);
        }
        return spaceKey;
    }

    private List<Object[]> getOldMappings()
    {
        try {
            return queryManager.createQuery(
                "select doc, spaceProp.value, mappingProp.value from "
                    + "XWikiDocument doc, BaseObject o, StringProperty spaceProp, LargeStringProperty mappingProp "
                    + "where "
                    + "doc.fullName = o.name and "
                    + "o.className = 'ConfluenceMigratorPro.Code.LinkMappingStateSpaceClass' and "
                    + "o.id = spaceProp.id.id and "
                    + "spaceProp.id.name = 'spaceKey' and "
                    + "o.id = mappingProp.id.id and "
                    + "mappingProp.id.name = 'mapping' "
                    + "order by doc.fullName",
                Query.HQL).execute();
        } catch (QueryException e) {
            logger.error("Failed to find the old mappings", e);
        }
        return null;
    }

    private boolean isSpaceFoundInConfluencePageClassObj(String spaceKey)
    {
        try {
            return !queryManager.createQuery(
                "select 1 from BaseObject o, StringProperty p "
                    + "where o.className = 'Confluence.Code.ConfluencePageClass' and "
                    + "o.id = p.id.id and "
                    + "p.id.name = 'space' and "
                    + "p.value = :space", Query.HQL
            ).setLimit(1).bindValue("space", spaceKey).execute().isEmpty();
        } catch (QueryException e) {
            logger.error("Failed to determine whether data on space [{}] is already present in the wiki", spaceKey, e);
        }
        // Let's assume it's not there
        return false;
    }

    private void parseOldMapping(Session session, String mapping, String spaceKey, boolean pageIds)
    {
        try {
            Map<String, String> m = (new ObjectMapper()).readValue(mapping, LM_TYPE_REF);
            for (Map.Entry<String, String> entry : m.entrySet()) {
                String ref = entry.getValue();
                if (pageIds) {
                    add(session, Long.parseLong(entry.getKey()), ref);
                } else {
                    add(session, spaceKey, entry.getKey(), ref);
                }
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse old mapping", e);
        }
    }
}
