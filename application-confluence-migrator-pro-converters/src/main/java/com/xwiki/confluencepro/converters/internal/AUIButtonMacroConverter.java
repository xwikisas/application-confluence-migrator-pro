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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;
import org.xwiki.icon.IconException;
import org.xwiki.icon.IconManager;

/**
 * Convert Confluence auibutton macro.
 *
 * @version $Id$
 * @since 1.33
 */
@Component
@Singleton
@Named("auibutton")
public class AUIButtonMacroConverter extends AbstractMacroConverter
{
    private static final String URL = "url";

    private static final String ICON = "icon";

    private static final String ID = "id";

    private static final String CLASS = "class";

    private static final String PARAM_TYPE = "type";

    private static final String PARAM_TYPE_DEFAULT = "DEFAULT";

    @Inject
    private IconManager iconManager;

    @Inject
    private Logger logger;

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "button";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> parameters = new LinkedHashMap<>(confluenceParameters.size());

        parameters.put("label", confluenceParameters.get("title"));
        parameters.put(URL, confluenceParameters.get(URL));
        String type;
        switch (confluenceParameters.get(PARAM_TYPE)) {
            case "standard":
                type = PARAM_TYPE_DEFAULT;
                break;
            case "primary":
                type = "PRIMARY";
                break;
            default:
                type = PARAM_TYPE_DEFAULT;
        }
        parameters.put(PARAM_TYPE, type);
        parameters.put("newTab", confluenceParameters.get("target"));

        String icon = confluenceParameters.get(ICON);
        if (StringUtils.isNotEmpty(icon)) {
            List<String> iconList = List.of();
            try {
                iconList = iconManager.getIconNames();
            } catch (IconException e) {
                logger.error("Can't get icon list", e);
            }
            if (iconList.contains(icon)) {
                parameters.put(ICON, icon);
            } else {
                markUnhandledParameterValue(confluenceParameters, ICON);
            }
        }
        String id = confluenceParameters.get(ID);
        if (StringUtils.isNotEmpty(id)) {
            parameters.put(ID, id);
        }
        String classParam = confluenceParameters.get(CLASS);
        if (StringUtils.isNotEmpty(classParam)) {
            parameters.put(CLASS, classParam);
        }
        return parameters;
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {
        return InlineSupport.YES;
    }
}
