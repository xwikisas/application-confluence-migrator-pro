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

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageIdResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageTitleResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceResolver;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Confluence Migrator Pro Link Mapping Confluence resolver.
 * @since 1.28.0
 * @version $Id$
 */
@Component
@Singleton
@Named("prolinkmapping")
public class ProLinkMappingConfluenceResolver implements ConfluencePageIdResolver, ConfluencePageTitleResolver,
    ConfluenceSpaceKeyResolver, ConfluenceSpaceResolver
{
    @Inject
    private EntityReferenceResolver<String> resolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private LinkMappingStore store;

    @Override
    public EntityReference getDocumentById(long id)
    {
        String ref = store.get(id);
        return resolveDocument(ref);
    }

    @Override
    public EntityReference getDocumentByTitle(String spaceKey, String title)
    {
        String ref = store.get(spaceKey, title);
        return resolveDocument(ref);
    }

    private EntityReference resolveDocument(String ref)
    {
        if (ref == null) {
            return null;
        }

        return resolver.resolve(ref, EntityType.DOCUMENT);
    }

    private EntityReference resolveSpace(String ref)
    {
        if (ref == null) {
            return null;
        }

        return resolver.resolve(ref, EntityType.DOCUMENT).getParent();
    }

    @Override
    public EntityReference getSpaceByKey(String spaceKey)
    {
        String ref = store.getShortestReferenceForSpace(spaceKey);
        return resolveSpace(ref);
    }

    @Override
    public EntityReference getSpace(EntityReference reference)
    {
        String ref = store.getShortestReferenceForSpaceByReference(this.serializer.serialize(reference));
        return resolveSpace(ref);
    }

    @Override
    public String getSpaceKey(EntityReference reference)
    {
        return store.getSpaceForReference(this.serializer.serialize(reference));
    }
}
