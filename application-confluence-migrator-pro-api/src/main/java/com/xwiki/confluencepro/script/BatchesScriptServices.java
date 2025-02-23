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
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.script.service.ScriptService;

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
}
