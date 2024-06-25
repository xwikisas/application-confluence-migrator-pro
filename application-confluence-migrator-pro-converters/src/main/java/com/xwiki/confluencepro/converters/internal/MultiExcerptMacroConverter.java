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

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.MacroConverter;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Converter for all known MultiExcerpt macros.
 * @version $Id$
 * @since 1.20.0
 */
@Component (hints = {
    "multiexcerpt-fast-block-macro",
    "multiexcerpt-fast-inline-macro",
    "multiexcerpt-macro",
    "multiexcerpt"
})
@Singleton
public class MultiExcerptMacroConverter extends AbstractMacroConverter implements MacroConverter
{
    private static final String ATLASSIAN_MACRO_OUTPUT_TYPE = "atlassian-macro-output-type";

    private static final String HIDDEN = "hidden";
    private static final String FALSE = "false";
    private static final String TRUE = "true";
    private static final String INLINE = "inline";
    private static final String BLOCK = "block";
    private static final String NAME = "name";

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "excerpt";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> p = new HashMap<>(4);

        String name = confluenceParameters.get(NAME);
        if (name == null) {
            name = confluenceParameters.get("MultiExcerptName");
        }

        p.put(NAME, name);

        if (!FALSE.equals(confluenceParameters.getOrDefault(HIDDEN, FALSE))) {
            p.put(HIDDEN, TRUE);
        }

        if (!FALSE.equals(confluenceParameters.getOrDefault("fallback", FALSE))) {
            p.put("allowUnprivilegedInclude", TRUE);
        }

        InlineSupport supportsInlineMode = supportsInlineMode(confluenceId, confluenceParameters, content);

        if (InlineSupport.NO.equals(supportsInlineMode)) {
            p.put(INLINE, FALSE);
        } else if (InlineSupport.YES.equals(supportsInlineMode)) {
            p.put(INLINE, TRUE);
        }

        return p;
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {

        if (id.contains(BLOCK) || "BLOCK".equals(parameters.get(ATLASSIAN_MACRO_OUTPUT_TYPE))) {
            return InlineSupport.NO;
        }

        if (id.contains(INLINE) || "INLINE".equals(parameters.get(ATLASSIAN_MACRO_OUTPUT_TYPE))) {
            return InlineSupport.YES;
        }

        return InlineSupport.MAYBE;
    }
}
