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

import java.util.Map;
import java.util.HashMap;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;
import org.xwiki.rendering.listener.Listener;

/**
 * Convert the confluence time macro into a date macro.
 *
 * @version $Id$
 * @since 1.20.2
 */
@Component
@Singleton
@Named("time")
public class DateMacroConverter extends AbstractMacroConverter
{
    @Override
    public void toXWiki(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline, Listener listener)
    {
        Map<String, String> parameters = new HashMap<>(confluenceParameters);
        parameters.put("format", "yyyy-MM-dd");

        super.toXWiki("date", parameters, confluenceContent, inline, listener);
    }

    @Override
    protected String toXWikiParameterName(String confluenceParameterName, String id,
        Map<String, String> confluenceParameters, String confluenceContent)
    {
        if (confluenceParameterName.equals("datetime")) {
            return "value";
        }
        return super.toXWikiParameterName(confluenceParameterName, id, confluenceParameters, confluenceContent);
    }
}
