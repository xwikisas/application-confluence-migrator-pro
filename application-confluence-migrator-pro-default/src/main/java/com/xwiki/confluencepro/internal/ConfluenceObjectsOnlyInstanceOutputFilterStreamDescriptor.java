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

import org.xwiki.filter.descriptor.FilterStreamDescriptor;
import org.xwiki.filter.descriptor.FilterStreamPropertyDescriptor;

import java.util.Collection;
import java.util.Collections;

/**
 * Confluence Objects Only Output stream Descriptor.
 * @since 1.12.0
 * @version $Id$
 */
public class ConfluenceObjectsOnlyInstanceOutputFilterStreamDescriptor implements FilterStreamDescriptor
{
    @Override
    public String getName()
    {
        return "Confluence Migrator Pro objects only output stream";
    }

    @Override
    public String getDescription()
    {
        return "Add objects to pages, ignoring everything else, instead of overwriting them";
    }

    @Override
    public <T> FilterStreamPropertyDescriptor<T> getPropertyDescriptor(String propertyName)
    {
        return null;
    }

    @Override
    public Collection<FilterStreamPropertyDescriptor<?>> getProperties()
    {
        return Collections.emptyList();
    }
}

