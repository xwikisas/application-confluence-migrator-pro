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
import java.util.LinkedHashMap;
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
 * @since 1.26.0
 */
@Component(hints = { "hide-if", "show-if" })
@Singleton
public class ShowIfHideIfMacroConverter extends AbstractMacroConverter
{
    private static final String LIST_ITEM_PARAM_SEPARATOR = ",";

    private static final String GROUP_ID_PARAM = "groupIds";

    private static final String GROUP_PARAM = "group";

    private static final String GROUPS_PARAM = "groups";

    private static final String SPECIAL_USERNAME_PARAM = "specialUsername";

    private static final String SPECIAL_PARAM = "special";

    private static final String AUTHENTICATION_TYPE_PARAM = "authenticationType";

    private static final String DISPLAY_PARAM_VALUE_PRINTABLE = "printable";

    @Inject
    private ConfluenceConverter converter;

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> parameters = new LinkedHashMap<>(confluenceParameters.size());

        // Note that depending on if it's the Confluence cloud or Confluence server the parameter name and values are
        // different, so it's why we have 2 group parameters with the value handled differently.
        for (Map.Entry<String, String> entry : confluenceParameters.entrySet()) {
            String name = entry.getKey();
            String confluenceValue = entry.getValue();
            String xwikiValue = toXWikiParameterValue(name, confluenceValue, confluenceId, confluenceParameters,
                content);

            switch (name) {
                case "match":
                    parameters.put("matchUsing", xwikiValue.toUpperCase());
                    break;
                case "user":
                    if (xwikiValue.charAt(0) == '@') {
                        // Handle a specific legacy case when the special username was set in the user parameter
                        // (instead of in the new specific 'special' parameter)
                        // Note that to make this working correctly, in confluence-xml a custom code avoid to make any
                        // transformation when we have this specific parameter.
                        // This part of code is available here:
                        // https://github.com/xwiki-contrib/confluence/blob/5b12c59dd56253d559496d20b0f4d0d8ffac31ae/confluence-syntax-xhtml/src/main/java/org/xwiki/contrib/confluence/parser/xhtml/internal/wikimodel/UserTagHandler.java#L104-L118
                        String value = confluenceValue.substring(1).toUpperCase();
                        parameters.put(AUTHENTICATION_TYPE_PARAM, value);
                    } else {
                        parameters.put("users", xwikiValue);
                    }
                    break;
                case GROUP_ID_PARAM:
                    String valueGroupId = Arrays.stream(confluenceValue.split(LIST_ITEM_PARAM_SEPARATOR))
                        .map(i -> {
                            String groupRef = converter.convertGroupId(i);
                            return groupRef == null ? i : groupRef;
                        }).collect(Collectors.joining(LIST_ITEM_PARAM_SEPARATOR));
                    parameters.put(GROUPS_PARAM, valueGroupId);
                    break;

                case GROUP_PARAM:
                    String valueGroup = Arrays.stream(confluenceValue.split(LIST_ITEM_PARAM_SEPARATOR))
                        .map(i -> {
                            String groupRef = converter.toGroupReference(i);
                            return groupRef == null ? i : groupRef;
                        }).collect(Collectors.joining(LIST_ITEM_PARAM_SEPARATOR));
                    parameters.put(GROUPS_PARAM, valueGroup);
                    break;
                case SPECIAL_USERNAME_PARAM:
                    parameters.put(AUTHENTICATION_TYPE_PARAM, xwikiValue.toUpperCase());
                    break;
                case SPECIAL_PARAM:
                    String valueSpecial = confluenceValue.replace("@", "").toUpperCase();
                    parameters.put(AUTHENTICATION_TYPE_PARAM, valueSpecial);
                    break;
                case "type":
                    parameters.put("contentType", xwikiValue);
                    break;
                case "display":
                    if ("word".equalsIgnoreCase(xwikiValue)) {
                        xwikiValue = DISPLAY_PARAM_VALUE_PRINTABLE;
                    } else if ("pdf".equalsIgnoreCase(xwikiValue)) {
                        xwikiValue = DISPLAY_PARAM_VALUE_PRINTABLE;
                    }
                    parameters.put("displayType", xwikiValue.toUpperCase());
                    break;
                case "label":
                    parameters.put("tags", xwikiValue);
                    break;
                default:
                    String parameterName = toXWikiParameterName(name, confluenceId, confluenceParameters, content);
                    parameters.put(parameterName, xwikiValue);
                    break;
            }
        }
        return parameters;
    }
}
