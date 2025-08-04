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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.input.ConfluenceConverter;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;
import org.xwiki.rendering.listener.Listener;

/**
 * Convert the confluence listlabels macro into a tagList macro.
 *
 * @version $Id$
 * @since 1.22.6
 */
@Component
@Singleton
@Named("listlabels")
public class ListLabelsMacroConverter extends AbstractMacroConverter
{

    private static final String SPACE_KEY = "spaceKey";

    private static final String EXCLUDED_LABELS = "excludedLabels";

    private static final String SPACES = "spaces";

    @Inject
    private ConfluenceConverter converter;


    @Override
    public void toXWiki(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline, Listener listener)
    {
        super.toXWiki("tagList", confluenceParameters, confluenceContent, inline, listener);
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> xwikiParameters = new HashMap<>(2);
        String spaceKey = confluenceParameters.get(SPACE_KEY);
        if (StringUtils.isNotEmpty(spaceKey)) {
            xwikiParameters.put(SPACES, spaceKey);
        } else {
            xwikiParameters.put(SPACES, converter.convertSpaceReference("@self"));
        }
        String excludedLabels = confluenceParameters.get(EXCLUDED_LABELS);
        if (StringUtils.isNotEmpty(excludedLabels)) {
            xwikiParameters.put("excludedTags", excludedLabels);
        }
        return xwikiParameters;
    }
}
