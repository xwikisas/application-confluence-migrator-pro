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
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;

/**
 * Converts the basic feature of the excel macro into the office macro.
 *
 * @version $Id$
 * @since 1.23.2
 */
@Singleton
@Component
@Named("excel")
public class ExcelMacroConverter extends AbstractMacroConverter
{
    private static final String FILENAME = "att--filename";

    @Inject
    private Logger logger;

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "office";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> xwikiParameters = new HashMap<>();
        Set<String> keySet = confluenceParameters.keySet();
        if (keySet.contains(FILENAME)) {
            xwikiParameters.put("reference", confluenceParameters.get(FILENAME));
        }
        keySet.remove(FILENAME);
        for (String key : keySet) {
            xwikiParameters.put("confluence_" + key, confluenceParameters.get(key));
            logger.warn(String.format("Parameter %s is not supported and was converted into confluence_%s.", key, key));
        }
        return xwikiParameters;
    }
}
