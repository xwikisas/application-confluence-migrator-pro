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
@Component (hints = {ViewFileMacroConverter.VIEW_FILE, "viewfile", "viewdoc", "viewppt", "viewxls", "viewpdf", "excel"})
@Singleton
public class ViewFileMacroConverter extends AbstractMacroConverter
{
    static final String VIEW_FILE = "view-file";
    private static final String NAME = "name";

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
        Map<String, String> parameters = new HashMap<>(4);
        parameters.put("display", VIEW_FILE.equals(confluenceId) ? "thumbnail" : "full");
        String filename = confluenceParameters.get("att--filename");
        if (StringUtils.isEmpty(filename)) {
            filename = confluenceParameters.get(NAME);
        }

        if (StringUtils.isEmpty(filename)) {
            throw new RuntimeException("Missing file name in viewfile-like macro [" + confluenceId + "],"
                + " killing the macro conversion.");
        }

        parameters.put(NAME, filename);
        retrieveParameter(confluenceParameters, parameters, "height");
        retrieveParameter(confluenceParameters, parameters, "width");
        retrieveParameter(confluenceParameters, parameters, "page");
        retrieveParameter(confluenceParameters, parameters, "exportFileDelimiter");
        return parameters;
    }

    private static void retrieveParameter(Map<String, String> confluenceParameters,
        Map<String, String> parameters, String key)
    {
        String v = confluenceParameters.get(key);
        if (StringUtils.isNotEmpty(v)) {
            parameters.put(key, v);
        }
    }
}
