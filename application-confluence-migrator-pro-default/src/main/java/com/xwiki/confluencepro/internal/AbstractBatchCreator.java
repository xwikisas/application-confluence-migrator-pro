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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.confluencepro.BatchCreator;

/**
 * Common behavior of the BatchCreator.
 *
 * @version $Id$
 * @since 1.31.1
 */
public abstract class AbstractBatchCreator implements BatchCreator
{
    @Inject
    protected Provider<XWikiContext> contextProvider;

    Document getMigrationDocument(String migrationBaseName, List<String> migrationsSpace)
    {
        String wikiId = contextProvider.get().getWikiId();
        int migrationIndex = 0;
        XWikiDocument migrationDocument = null;
        while (migrationDocument == null || !migrationDocument.isNew()) {
            DocumentReference migrationReference =
                new DocumentReference(wikiId, migrationsSpace, migrationBaseName + "_" + migrationIndex);
            migrationDocument = new XWikiDocument(migrationReference);
            migrationIndex += 1;
        }
        return new Document(migrationDocument, contextProvider.get());
    }
}
