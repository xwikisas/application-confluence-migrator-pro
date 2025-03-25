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
package com.xwiki.confluencepro.referencefixer.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.xwiki.confluencepro.internal.MigrationFixingStats;

/**
 * Reference fixing session statistics.
 * @since 1.29.0
 * @version $Id$
 */
public final class Stats implements MigrationFixingStats
{
    private long failedDocs;
    private long failedRefs;
    private long successfulRefs;
    private long successfulDocs;
    private long unchangedDocs;
    private Map<String, Integer> failedReferences;

    /**
     * Contructor.
     */
    Stats()
    {
        this.failedReferences = new HashMap<>();
    }

    @Override
    public String toJSON()
    {
        return "{"
            + "\"successfulDocs\": " + successfulDocs + ','
            + "\"unchangedDocs\": " + unchangedDocs + ','
            + "\"failedDocs\": " + failedDocs + ','
            + "\"successfulRefs\": " + successfulRefs + ','
            + "\"failedRefs\": " + failedRefs
            + "}";
    }

    String getFailedReferencesTSV()
    {
        return failedReferences.entrySet()
            .stream()
            .map(e -> e.getKey() + "\t" + e.getValue())
            .collect(Collectors.joining("\n"));
    }

    @Override
    public void incFailedDocs()
    {
        this.failedDocs++;
    }

    void addFailedRef(String reference)
    {
        int count = failedReferences.getOrDefault(reference, 0);
        failedReferences.put(reference, count + 1);
        this.failedRefs++;
    }

    @Override
    public void incSuccessfulDocs()
    {
        this.successfulDocs++;
    }

    @Override
    public void incUnchangedDocs()
    {
        this.unchangedDocs++;
    }

    void incSuccessfulRefs()
    {
        this.successfulRefs++;
    }

    void incSuccessfulRefs(long c)
    {
        this.successfulRefs += c;
    }
}
