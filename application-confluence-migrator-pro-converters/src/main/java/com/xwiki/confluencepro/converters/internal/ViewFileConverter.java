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
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Convert the Confluence view* macros into a view-file macro.
 *
 * @version $Id$
 * @since 1.21.0
 */
@Component (hints = {ViewFileConverter.VIEW_FILE, "viewfile", "viewdoc", "viewppt", "viewdoc", "viewxls", "viewpdf"})
@Singleton
public class ViewFileConverter extends AbstractMacroConverter
{
    static final String VIEW_FILE = "view-file";

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return VIEW_FILE;
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> parameters = new HashMap<>(confluenceParameters.size() + 1);
        parameters.put("display", VIEW_FILE.equals(confluenceId) ? "thumbnail" : "full");
        for (Map.Entry<String, String> p : confluenceParameters.entrySet()) {
            String key = p.getKey();
            if (key.equals("att-filename")) {
                key = "name";
            }
            parameters.put(key, p.getValue());
        }
        return parameters;
    }
}
