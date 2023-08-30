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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.refactoring.job.question.EntitySelection;

/**
 * Space question.
 *
 * @version $Id$
 * @since 1.0
 */
public class SpaceQuestion
{
    /**
     * The map of spaces that can be imported from Confluence.
     */
    private Map<EntitySelection, Map<String, String>> confluenceSpaces;

    /**
     * Default constructor.
     *
     * @param confluenceSpaces the confluence spaces.
     */
    public SpaceQuestion(Map<EntitySelection, Map<String, String>> confluenceSpaces)
    {
        this.confluenceSpaces = confluenceSpaces;
    }

    /**
     * @param entityReference the reference of a space
     * @return the EntitySelection corresponding to the space, or null if the entity is not concerned by the
     *     refactoring.
     */
    public EntitySelection get(EntityReference entityReference)
    {
        Optional<EntitySelection> maybeSelection =
            this.confluenceSpaces.keySet()
                .stream()
                .filter(e -> entityReference.equals(e.getEntityReference()))
                .findFirst();
        return maybeSelection.orElse(null);
    }

    /**
     * @return the confluence spaces.
     */
    public Map<EntitySelection, Map<String, String>> getConfluenceSpaces()
    {
        return this.confluenceSpaces;
    }

    /**
     * Unselect all the spaces.
     */
    public void unselectAll()
    {
        setSelectAllSpaces(false);
    }

    /**
     * Select all the spaces.
     */
    public void selectAll()
    {
        setSelectAllSpaces(true);
    }

    /**
     * @param selected true if all extensions should be selected
     */
    public void setSelectAllSpaces(boolean selected)
    {
        for (EntitySelection entitySelection : confluenceSpaces.keySet()) {
            entitySelection.setSelected(selected);
        }
    }

    /**
     * @param docId the string that identifies the document.
     * @return true is the document is selected, false otherwise.
     */
    public boolean isSelected(String docId)
    {
        EntityReference entityReference = new EntityReference(docId, EntityType.DOCUMENT);
        EntitySelection selection = get(entityReference);
        if (selection != null) {
            return selection.isSelected();
        }
        return false;
    }

    /**
     * Select a specific list of spaces.
     *
     * @param confluenceSpaces the spaces that are selected.
     */
    public void setSelectedDocuments(Set<EntityReference> confluenceSpaces)
    {
        for (EntityReference document : confluenceSpaces) {
            EntitySelection entitySelection = get(document);
            if (entitySelection != null) {
                entitySelection.setSelected(true);
            }
        }
    }
}
