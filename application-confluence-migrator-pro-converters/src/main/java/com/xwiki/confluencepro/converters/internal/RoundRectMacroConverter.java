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

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.AbstractMacroConverter;

/**
 * Convert the confluence roundrect macro to a panel macro.
 *
 * @version $Id$
 * @since 1.23.2
 */
@Component
@Singleton
@Named("roundrect")
public class RoundRectMacroConverter extends AbstractMacroConverter
{
    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "panel";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> xwikiParameters = new LinkedHashMap<>();
        saveParameter(confluenceParameters, xwikiParameters, "bgcolor", "bgColor", true);
        saveParameter(confluenceParameters, xwikiParameters, "footer", true);
        saveParameter(confluenceParameters, xwikiParameters, "titlebgcolor", "titleBGColor", true);
        saveParameter(confluenceParameters, xwikiParameters, "class", "classes", true);
        saveParameter(confluenceParameters, xwikiParameters, "width", true);
        saveParameter(confluenceParameters, xwikiParameters, "footerbgcolor", "footerBGColor", true);
        saveParameter(confluenceParameters, xwikiParameters, "title", true);
        saveParameter(confluenceParameters, xwikiParameters, "height", true);
        return xwikiParameters;
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {
        return InlineSupport.NO;
    }
}
