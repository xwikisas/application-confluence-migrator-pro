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

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;

/**
 * Convert the confluence roundrect macro into a panel macro.
 *
 * @version $Id$
 * @since 1.23.2
 */
@Component
@Singleton
@Named("roundrect")
public class RoundRectMacroConvertor extends AbstractMacroConverter
{
    private static final String TITLE = "title";

    private static final String FOOTER = "footer";

    private static final String TITLEBGCOLOR = "titlebgcolor";

    private static final String BGCOLOR = "bgcolor";

    private static final String FOOTERBGCOLOR = "footerbgcolor";

    private static final String WIDTH = "width";

    private static final String HEIGHT = "height";

    private static final String CLASS = "class";

    @Inject
    private Logger logger;

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
        Map<String, String> xwikiParameters = new HashMap<>();
        for (String key : confluenceParameters.keySet()) {
            switch (key) {
                case TITLE:
                    xwikiParameters.put(TITLE, confluenceParameters.get(key));
                    break;
                case FOOTER:
                    xwikiParameters.put(FOOTER, confluenceParameters.get(key));
                    break;
                case TITLEBGCOLOR:
                    xwikiParameters.put("titleBGColor", confluenceParameters.get(key));
                    break;
                case BGCOLOR:
                    xwikiParameters.put("bgColor", confluenceParameters.get(key));
                    break;
                case FOOTERBGCOLOR:
                    xwikiParameters.put("footerBGColor", confluenceParameters.get(key));
                    break;
                case WIDTH:
                    xwikiParameters.put(WIDTH, confluenceParameters.get(key));
                    break;
                case HEIGHT:
                    xwikiParameters.put(HEIGHT, confluenceParameters.get(key));
                    break;
                case CLASS:
                    xwikiParameters.put("classes", confluenceParameters.get(key));
                    break;
                default:
                    xwikiParameters.put("confluence_" + key, confluenceParameters.get(key));
                    logger.warn(
                        String.format("Parameter %s is not supported and was converted into confluence_%s.", key, key));
                    break;
            }
        }
        return xwikiParameters;
    }
}
