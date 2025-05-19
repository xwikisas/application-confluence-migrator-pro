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
package com.xwiki.confluencepro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.repository.InstalledExtensionRepository;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;
import com.xwiki.confluencepro.internal.configuration.DefaultConfluenceMigratorProConfiguration;
import com.xwiki.licensing.Licensor;

/**
 * Handles the computation of extra details of the migration.
 *
 * @version $Id$
 * @since 1.34.5
 */
@Component(roles = MigrationExtraDetails.class)
@Singleton
@Unstable
public class MigrationExtraDetails
{
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Inject
    private DefaultConfluenceMigratorProConfiguration migratorProConfiguration;

    @Inject
    private InstalledExtensionRepository installedExtensionRepository;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Licensor licensor;

    /**
     * Identifies the versions of the extensions that are listed in the configs.
     *
     * @return a json with all the extensions and their version.
     */
    public String identifyDependencyVersions() throws JsonProcessingException
    {
        Map<String, String> versions = new HashMap<>();

        String wikiName = contextProvider.get().getWikiId();
        for (String extensionID : migratorProConfiguration.getExtensionIDs()) {
            InstalledExtension extension =
                installedExtensionRepository.getInstalledExtension(extensionID, "wiki:" + wikiName);
            versions.put(extensionID, String.valueOf(extension.getId().getVersion()));
        }
        return JSON_MAPPER.writeValueAsString(versions);
    }

    /**
     * @return the type of license.
     */
    public String identifyLicenseType()
    {
        DocumentReference mainWiki =
            new DocumentReference(contextProvider.get().getMainXWiki(), List.of("ConfluenceMigratorPro", "Code"),
                "MigrationClass");
        return licensor.getLicense(mainWiki).getType().toString();
    }
}
