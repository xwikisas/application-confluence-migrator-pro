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
 * Converter for all known MultiExcerpt include macros.
 * @version $Id$
 * @since 1.20.0
 */
@Component (hints = {
    "multiexcerpt-include",
    "multiexcerpt-include-macro",
    "multiexcerpt-fast-include-block-macro",
    "multiexcerpt-fast-include-inline-macro"
})
@Singleton
public class MultiExcerptIncludeMacroConverter extends AbstractMacroConverter implements MacroConverter
{
    private static final String INLINE = "inline";
    private static final String BLOCK = "block";
    private static final String TEMPLATE_DATA = "templateData";
    private static final String NAME = "name";

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

        Map<String, String> p = new HashMap<>(4);

        String reference = confluenceParameters.get("page");
        if (reference == null) {
            reference = confluenceParameters.get("PageWithExcerpt");
        }

        reference = (reference == null || reference.isEmpty()) ? "WebHome" : reference;

        p.put("0", reference);

        String name = confluenceParameters.get(NAME);
        if (name == null) {
            name = confluenceParameters.get("MultiExcerptName");
        }

        p.put(NAME, name);

        InlineSupport supportsInlineMode = supportsInlineMode(confluenceId, confluenceParameters, content);
        if (InlineSupport.YES.equals(supportsInlineMode)) {
            p.put(INLINE, "true");
        } else if (InlineSupport.NO.equals(supportsInlineMode)) {
            p.put(INLINE, "false");
        }

        String templateData = confluenceParameters.get(TEMPLATE_DATA);
        if (templateData != null && !templateData.isEmpty()) {
            // as of writing this, templateData is not supported, but we don't want to lose this information.
            // it contains information to replace placeholders by actual values
            p.put(TEMPLATE_DATA, templateData);
        }

        return p;
    }

    @Override
    public InlineSupport supportsInlineMode(String id, Map<String, String> parameters, String content)
    {
        if (id.contains(BLOCK)) {
            return InlineSupport.NO;
        }

        if (id.contains(INLINE)) {
            return InlineSupport.YES;
        }

        return InlineSupport.MAYBE;
    }
}
