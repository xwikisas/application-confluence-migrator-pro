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
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.AbstractMacroConverter;
import org.xwiki.contrib.confluence.filter.ConversionException;

/**
 * Convert ui-button to button.
 * @version $Id$
 * @since 1.37.0
 */
@Component
@Singleton
@Named("ui-button")
public class UIButtonMacroConverter extends AbstractMacroConverter
{
    private static final String ICON = "icon";
    private static final String LABEL = "label";
    private static final String DISPLAY = "display";
    private static final String TITLE = "title";
    private static final String RSS = "rss";
    private static final String COLOR = "color";

    // We map some icons that are not recognized to equivalent enough Font Awesome 4 icons
    private static final Map<String, String> CONVERTED_ICONS = Map.of(
        LABEL, "tag",
        "mail", "envelope",
        "text", "align-justify",
        "idea", "lightbulb-o",
        "settings", "cog",
        "news", RSS,
        "blog", RSS,
        "location", "map-marker",
        "notification", "bell",
        "reminder", "clock-o"
    );

    private static final Map<String, String> COLOR_MAP = Map.of(
        "yellow", "#ebd364",
        "green", "#79c97b",
        "magenta", "#eb64a3",
        "orange", "#ef7d58",
        "purple", "#d968cd",
        "red", "#ed707f",
        "turquoise", "#43c5d0",
        "blue", "#71b0e0"
    );

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "button";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content) throws ConversionException
    {
        Map<String, String> parameters = new LinkedHashMap<>(confluenceParameters.size());
        saveParameter(confluenceParameters, parameters, "url", true);
        String color = confluenceParameters.get(COLOR);
        if (StringUtils.isNotEmpty(color)) {
            parameters.put(COLOR, COLOR_MAP.getOrDefault(color, color));
        }
        saveParameter(confluenceParameters, parameters, TITLE, LABEL, true);
        saveParameter(confluenceParameters, parameters, "tooltip", TITLE, true);
        markHandledParameter(confluenceParameters, DISPLAY, true);
        String icon = confluenceParameters.get(ICON);
        if (StringUtils.isNotEmpty(icon)) {
            parameters.put(ICON, CONVERTED_ICONS.getOrDefault(icon, icon));
        }
        return parameters;
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {
        return "block".equals(parameters.get(DISPLAY)) ? InlineSupport.NO : InlineSupport.YES;
    }
}
