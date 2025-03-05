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

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convert the Confluence net-presago-stream-macro macro into a msStream macro.
 *
 * @version $Id$
 * @since 1.21.0
 */
@Component
@Singleton
@Named("net-presago-stream-macro")
public class MSStreamConverter extends AbstractMacroConverter
{
    private static final String URI = "uri";
    private static final String URL = "url";

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "msStream";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        markHandledParameter(confluenceParameters, URI, true);
        markHandledParameter(confluenceParameters, URL, true);
        markHandledParameter(confluenceParameters, "width", true);
        markHandledParameter(confluenceParameters, "height", true);
        markHandledParameter(confluenceParameters, "start", true);
        markHandledParameter(confluenceParameters, "showinfo", true);
        markHandledParameter(confluenceParameters, "autoplay", true);

        Map<String, String> parameters = new LinkedHashMap<>(confluenceParameters.size());
        for (Map.Entry<String, String> p : confluenceParameters.entrySet()) {
            String key = p.getKey();
            if (key.equals(URI)) {
                key = URL;
            }
            parameters.put(key, p.getValue());
        }
        return parameters;
    }
}
