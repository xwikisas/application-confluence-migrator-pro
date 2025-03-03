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
package com.xwiki.confluencepro.script;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xpn.xwiki.XWikiException;
import com.xwiki.confluencepro.BatchCreator;

/**
 * Expose various methods needed for the batch migrations.
 *
 * @version $Id$
 * @since 1.30.2
 */

@Component
@Named(BatchesScriptServices.ROLEHINT)
@Singleton
public class BatchesScriptServices implements ScriptService
{
    /**
     * The rolehint of the class.
     */
    public static final String ROLEHINT = "confluenceBatches";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    private BatchCreator batchCreator;

    /**
     * Computes the size of a batch.
     *
     * @param paths files that form the batch
     * @return size of the batch
     */
    public String computeBatchSize(List<String> paths)
    {
        double bytes = 0;
        for (String path : paths) {
            File file = new File(path);
            bytes += file.length();
        }
        String[] units = { "Bytes", "KB", "MB", "GB", "TB" };
        int index = 0;
        while (bytes >= 1024 && index < units.length - 1) {
            bytes /= 1024.0;
            index++;
        }
        return String.format("%.2f %s", bytes, units[index]);
    }

    /**
     * Returns the file separator used by the operating system.
     *
     * @return the system-dependent file separator string
     */
    public String getSystemFileSeparator()
    {
        return File.separator;
    }

    /**
     * Returns the files present in the specified directory or {@code null} if the given path is not a directory.
     *
     * @param path the source directory path
     * @return an array of files in the directory if the path is valid; otherwise, {@code null}
     */
    public File[] getZips(String path)
    {
        String batchPath = path.startsWith("file:") ? path.substring(5) : path;
        File folder = new File(batchPath);

        if (folder.isDirectory()) {
            return folder.listFiles();
        }
        return null;
    }

    /**
     * Returns the absolute path of the server to provide a hint to the user if the path given to
     * {@link #getZips(String)} is not valid.
     *
     * @return the absolute path of the server
     */
    public String getServerAbsolutePath()
    {
        return Paths.get("").toAbsolutePath().toString();
    }
        /**
         * Creates the batch and all its migrations.
         *
         * @param batchName The name of the batch.
         * @param sources The sources of the migration packages.
         * @param inputProperties The input properties of the migration.
         * @param outputProperties The output properties of the migration.
         * @param dryRun Whether the batch should actually be created or just log what would be created.
         * @param extraParams A map containing extra parameters for custom implementations that may require additional
         *     data.
         * @return A map containing logs of the creation process.
         */
    public Map<String, List<String>> createBatch(String batchName, List<String> sources, String inputProperties,
        String outputProperties, boolean dryRun, Map<String, String> extraParams)
        throws JsonProcessingException, XWikiException
    {
        return batchCreator.createBatch(batchName, sources, inputProperties, outputProperties, dryRun, extraParams);
    }

    /**
     * Receives a json in string format and returns a JSON object.
     * @param json json string
     * @return JSON object
     */
    public ObjectNode resolveJson(String json) throws JsonProcessingException
    {
        return (ObjectNode) OBJECT_MAPPER.readTree(json);
    }
}
