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
package com.xwiki.confluencepro.internal.configuration;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.stability.Unstable;

/**
 * Configuration of the confluence migrator pro.
 *
 * @version $Id$
 * @since 1.34.5
 */
@Component(roles = DefaultConfluenceMigratorProConfiguration.class)
@Singleton
@Unstable
public class DefaultConfluenceMigratorProConfiguration
{

    @Inject
    @Named(ConfluenceMigratorConfigurationSource.HINT)
    private ConfigurationSource configDocument;

    /**
     * @return list of extension IDs.
     */
    public List<String> getExtensionIDs()
    {
        String value = this.configDocument.getProperty("extensionIDs");
        return List.of(value.split(","));
    }
}
