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
package com.xwiki.confluencepro.converters.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

/**
 * Convert task macros.
 *
 * @version $Id$
 * @since 1.22.6
 */
@Component
@Singleton
@Named("userlister")
public class UserListerMacroConverter extends AbstractMacroConverter
{
    private static final String GROUPS = "groups";
    private static final String USERS = "users";

    @Inject
    @Named("group")
    private EntityReferenceResolver<String> referenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "userList";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        markHandledParameter(confluenceParameters, GROUPS, true);
        markHandledParameter(confluenceParameters, USERS, true);

        Map<String, String> parameters = new HashMap<>(confluenceParameters.size());
        List<String> groupList = new ArrayList<>();
        for (Map.Entry<String, String> p : confluenceParameters.entrySet()) {
            String key = p.getKey();
            String value = p.getValue();
            if (key.equals(GROUPS)) {
                value = convertGroupsParameter(p, groupList);
            }
            parameters.put(key, value);
        }
        return parameters;
    }

    private String convertGroupsParameter(Map.Entry<String, String> p, List<String> groupList)
    {
        String value;
        for (String group : p.getValue().split("\\s*,\\s*")) {
            String groupName;
            if (group.equals("*")) {
                groupList.clear();
                break;
            } else if (group.startsWith("site-admins") || group.startsWith("confluence-admins") || group.equals(
                "administrators"))
            {
                groupName = "XWikiAdminGroup";
            } else if (group.startsWith("site-users") || group.startsWith("confluence-users") || group.equals(
                USERS))
            {
                groupName = "XWikiAllGroup";
            } else {
                groupName = group;
            }

            if (!groupName.isEmpty()) {
                EntityReference reference = referenceResolver.resolve(groupName, EntityType.DOCUMENT);
                groupList.add(serializer.serialize(reference));
            }
        }
        value = String.join(",", groupList);
        return value;
    }
}
