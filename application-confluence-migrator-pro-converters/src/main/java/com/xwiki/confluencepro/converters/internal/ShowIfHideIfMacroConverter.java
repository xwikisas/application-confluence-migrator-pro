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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.input.ConfluenceConverter;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;

/**
 * Converter used for hide-if and show-if macro.
 *
 * @version $Id$
 * @since 1.24.6
 */
@Component(hints = {"hide-if", "show-if"})
@Singleton
public class ShowIfHideIfMacroConverter extends AbstractMacroConverter
{
    private static final String GROUP_ID_PARAM = "groupIds";

    private static final String GROUP_ID_SEPARATOR = ",";

    @Inject
    private ConfluenceConverter converter;

    @Override
    protected String toXWikiParameterValue(String confluenceParameterName, String confluenceParameterValue,
        String confluenceId, Map<String, String> parameters, String confluenceContent)
    {
        if (confluenceParameterName.equals(GROUP_ID_PARAM)) {
            return Arrays.stream(confluenceParameterValue.split(GROUP_ID_SEPARATOR))
                .map(i -> {
                    String groupRef = converter.convertGroupId(i);
                    return groupRef == null ? i : groupRef;
                }).collect(Collectors.joining(GROUP_ID_SEPARATOR));
        }
        return super.toXWikiParameterValue(confluenceParameterName, confluenceParameterValue, confluenceId, parameters,
            confluenceContent);
    }

    @Override
    protected String toXWikiParameterName(String confluenceParameterName, String id,
        Map<String, String> confluenceParameters, String confluenceContent)
    {
        if (confluenceParameterName.equals(GROUP_ID_PARAM)) {
            return "groups";
        }
        if (confluenceParameterName.equals("specialUsername")) {
            return "authenticationType";
        }
        return super.toXWikiParameterName(confluenceParameterName, id, confluenceParameters, confluenceContent);
    }
}
