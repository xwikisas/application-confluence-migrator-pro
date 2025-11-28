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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.MacroConverter;
import org.xwiki.contrib.confluence.filter.AbstractMacroConverter;

import javax.inject.Singleton;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converter for all known MultiExcerpt include macros.
 * @version $Id$
 * @since 1.20.0
 */
@Component (hints = {
    "multi-excerpt-include",
    "multiexcerpt-include",
    "multiexcerpt-include-macro",
    "multiexcerpt-fast-include-block-macro",
    "multiexcerpt-fast-include-inline-macro"
})
@Singleton
public class MultiExcerptIncludeMacroConverter extends AbstractMacroConverter implements MacroConverter
{
    private static final String INLINE = "inline";
    private static final String NAME = "name";
    private static final String TRUE = "true";
    private static final String NOPANEL = "nopanel";
    private static final String FALSE = "false";
    private static final String[] TITLE_PARAMS = {"page", "PageWithExcerpt", "pageTitle"};
    private static final String[] NAME_PARAMS = { NAME, "MultiExcerptName" };
    private static final String REF_PARAM = "0";

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "excerpt-include";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {

        Map<String, String> p = new TreeMap<>();
        if (saveParameter(confluenceParameters, p, TITLE_PARAMS, REF_PARAM, true) == null) {
            p.put(REF_PARAM, "WebHome");
        }

        saveParameter(confluenceParameters, p, NAME_PARAMS, NAME, true);
        saveParameter(confluenceParameters, p, "templateData", true);

        handleInlineAndPanel(confluenceId, confluenceParameters, content, p);

        return p;
    }

    private void handleInlineAndPanel(String confluenceId, Map<String, String> confluenceParameters, String content,
        Map<String, String> p)
    {
        String nopanel = confluenceParameters.get(NOPANEL);
        String addpanel = confluenceParameters.get("addpanel");
        if (StringUtils.isEmpty(nopanel)) {
            nopanel = FALSE.equals(addpanel) ? TRUE : FALSE;
        }

        InlineSupport supportsInlineMode = supportsInlineMode(confluenceId, confluenceParameters, content);
        if (InlineSupport.YES.equals(supportsInlineMode)) {
            p.put(INLINE, TRUE);
            if (StringUtils.isNotEmpty(nopanel)) {
                p.put(NOPANEL, nopanel);
            }
        } else {
            if (InlineSupport.NO.equals(supportsInlineMode)) {
                p.put(INLINE, FALSE);
            }
            p.put(NOPANEL, TRUE);
        }
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {
        if (id.contains("block")) {
            return InlineSupport.NO;
        }

        if (id.contains(INLINE)) {
            return InlineSupport.YES;
        }

        return InlineSupport.MAYBE;
    }
}
