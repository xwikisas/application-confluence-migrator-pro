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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.ObservationManager;
import org.xwiki.search.solr.internal.api.SolrIndexer;

import com.xwiki.confluencepro.ConfluenceMigrationPrerequisites;

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
    private static final String LISTENER_NOTIFICATION_FILTERS = "NotificationsFiltersPreferences-DocumentMovedListener";

    private static final String LISTENER_NOTIFICATION_EMAIL = "Live Notification Email Listener";

    private static final String LISTENER_AUTOMATIC_NOTIFICATION = "AutomaticNotificationsWatchModeListener";

    private static final String LISTENER_NOTIFICATION_PREFILTERING = "Prefiltering Live Notification Email Listener";

    @Inject
    private SolrIndexer solrIndexer;

    @Inject
    private ConfluenceMigrationPrerequisites prerequisites;

    @Inject
    private ObservationManager observationManager;

    @Inject
    private Logger logger;

    private Map<EventListener, Integer> removedListeners = new HashMap<>();

    /**
     * Reenable the prerequisites. See {@link #disablePrerequisites()} for the list of prerequisites.
     */
    public void enablePrerequisites()
    {
        addListener(LISTENER_AUTOMATIC_NOTIFICATION);
        addListener(LISTENER_NOTIFICATION_PREFILTERING);
        addListener(LISTENER_NOTIFICATION_FILTERS);
        addListener(LISTENER_NOTIFICATION_EMAIL);
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

        logger.info("Waiting for the solr queue to be empty..");
        while (solrIndexer.getQueueSize() > 0 || System.nanoTime() - startTime >= timeout) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        logger.info("Clearing user notifications preferences..");
        prerequisites.checkCurrentUserNotificationCleanup();

        removeListener(LISTENER_AUTOMATIC_NOTIFICATION);
        removeListener(LISTENER_NOTIFICATION_EMAIL);
        removeListener(LISTENER_NOTIFICATION_FILTERS);
        removeListener(LISTENER_NOTIFICATION_PREFILTERING);
    }

    private synchronized void removeListener(String listenerName)
    {
        logger.info("Disabling listener [{}]..", listenerName);
        EventListener listener = this.observationManager.getListener(listenerName);
        if (listener == null) {
            listener = this.removedListeners.keySet().stream().filter(e -> listenerName.equals(e.getName())).findFirst()
                .orElse(null);
            if (listener == null) {
                return;
            }
        }
        this.removedListeners.put(listener, this.removedListeners.getOrDefault(listener, 0) + 1);
        if (this.removedListeners.get(listener) == 1) {
            this.observationManager.removeListener(listenerName);
        }
    }

    private synchronized void addListener(String listenerName)
    {
        logger.info("Enabling listener [{}]..", listenerName);
        EventListener listener =
            this.removedListeners.keySet().stream().filter(k -> listenerName.equals(k.getName())).findFirst()
                .orElse(null);
        if (listener == null) {
            return;
        }
        int layers = this.removedListeners.get(listener) - 1;
        if (layers <= 0) {
            this.observationManager.addListener(listener);
            this.removedListeners.remove(listener);
        } else {
            this.removedListeners.put(listener, layers);
        }
    }
}
