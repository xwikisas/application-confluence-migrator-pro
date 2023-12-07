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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.search.solr.internal.api.SolrIndexer;

/**
 * Manages the prerequisites of a confluence migration.
 *
 * @version $Id$
 * @since 1.8
 */
@Component(roles = ConfluenceMigrationPrerequisitesManager.class)
@Singleton
public class ConfluenceMigrationPrerequisitesManager
{
    @Inject
    private SolrIndexer solrIndexer;

    /**
     * Reenable the prerequisites. See {@link #disablePrerequisites()} for the list of prerequisites.
     */
    public void enablePrerequisites()
    {

    }

    /**
     * Disable/deactivate a list of prerequisites that will make the migration run smoother. Among the prerequisites, we
     * have the following: Wait for the solr indexer queue size to be empty. The timeout period is of 5 minutes.
     */
    public void disablePrerequisites()
    {
        long startTime = System.nanoTime();
        // 5 minutes
        long timeout = 5L * 60 * 1000000000;

        while (solrIndexer.getQueueSize() > 0 || System.nanoTime() - startTime >= timeout) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
