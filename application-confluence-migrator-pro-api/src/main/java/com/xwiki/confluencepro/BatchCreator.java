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

import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.Role;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xpn.xwiki.XWikiException;

/**
 * API for creating a batch.
 *
 * @version $Id$
 * @since 1.31.1
 */
@Role
public interface BatchCreator
{
    /**
     * Creates the batch and all its migrations.
     *
     * @param batchName The name of the batch.
     * @param sources The sources of the migration packages.
     * @param inputProperties The input properties of the migration.
     * @param outputProperties The output properties of the migration.
     * @param dryRun Whether the batch should actually be created or just log what would be created.
     * @param extraParams A map containing extra parameters for custom implementations that may require additional
     * data.
     * @return A map containing logs of the creation process.
     * @throws JsonProcessingException If an error occurs during JSON processing.
     * @throws XWikiException If an XWiki-related error occurs.
     */
    Map<String, List<String>> createBatch(String batchName, List<String> sources, String inputProperties,
        String outputProperties, boolean dryRun, Map<String, String> extraParams) throws JsonProcessingException,
        XWikiException;
}

