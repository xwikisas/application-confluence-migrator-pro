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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.filter.internal.input.ConfluenceConverter;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xwiki.date.DateMacroConfiguration;

/**
 * Some description.
 *
 * @version $Id$
 * @since 1.24.2
 */
@Component
@Singleton
@Named("tasks-report-macro")
public class TasksReportMacroConverter extends AbstractMacroConverter
{
    private static final String PARAMETER_DUEDATE = "duedate";

    private static final String PARAMETER_PAGES = "pages";

    private static final String PARAMETER_SORT_BY = "sortBy";

    private static final String PARAMETER_COLUMNS = "columns";

    private static final String PARAMETER_ASSIGNEES = "assignees";

    private static final String PARAMETER_STATUS = "status";

    private static final String PARAMETER_OWNER = "owner";

    private static final String PARAMETER_VALUE_INCOMPLETE = "incomplete";

    private static final String EMPTY_STRING = "";

    private static final String DELIMITER_REGEX = "\\s*,\\s*";

    private static final String DELIMITER = ",";

    private static final String PARAMETER_ASSIGNEE = "assignee";

    // Confluence: description, duedate, assignee, location, labels,     completedate
    // XWiki     : name       , duedate, assignee, owner,    createDate, number
    private static final Map<String, String> COLUMN_MAP = Map.of(
        "description", "name",
        PARAMETER_DUEDATE, PARAMETER_DUEDATE,
        PARAMETER_ASSIGNEE, PARAMETER_ASSIGNEE,
        "location", PARAMETER_OWNER,
        "completedate", "completeDate"
    );

    @Inject
    private ConfluenceConverter converter;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private DateMacroConfiguration dateMacroConfiguration;

    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "task-report";
    }

    @Override
    protected Map<String, String> toXWikiParameters(String confluenceId, Map<String, String> confluenceParameters,
        String content)
    {
        Map<String, String> xwikiParams = new HashMap<>();

        List<String> pagesFilter = new ArrayList<>();
        // The pages parameter coming from confluence consists of page ids.
        Arrays.stream(confluenceParameters.getOrDefault(PARAMETER_PAGES, EMPTY_STRING).split(DELIMITER_REGEX))
            .filter(pageId -> !pageId.isEmpty())
            .map(pageId -> converter.convertDocumentReference(Long.parseLong(pageId), false))
            .map(pageRef -> serializer.serialize(pageRef))
            .forEach(pagesFilter::add);

        pagesFilter.addAll(
            Arrays.asList(confluenceParameters.getOrDefault("spaces", EMPTY_STRING).split(DELIMITER_REGEX)));

        if (!pagesFilter.isEmpty()) {
            xwikiParams.put(PARAMETER_PAGES, String.join(DELIMITER, pagesFilter));
        }

        xwikiParams.compute("tags", (a, b) -> confluenceParameters.get("labels"));
        xwikiParams.compute(PARAMETER_STATUS, (a, b) ->
            confluenceParameters.getOrDefault(PARAMETER_STATUS, PARAMETER_VALUE_INCOMPLETE)
                .equals(PARAMETER_VALUE_INCOMPLETE) ? "InProgress" : "Done");
        xwikiParams.compute("reporters", (a, b) -> confluenceParameters.get("creators"));
        xwikiParams.compute(PARAMETER_ASSIGNEES, (a, b) -> confluenceParameters.get(PARAMETER_ASSIGNEES));

        String sortBy = confluenceParameters.getOrDefault(PARAMETER_SORT_BY, PARAMETER_DUEDATE);
        if (sortBy.equals("page title")) {
            sortBy = PARAMETER_OWNER;
        }
        xwikiParams.put(PARAMETER_SORT_BY, sortBy);

        xwikiParams.compute("limit", (a, b) -> confluenceParameters.get("pageSize"));
        // Confluence: description, duedate, assignee, location, labels,     completedate
        // XWiki     : name       , duedate, assignee, owner,    createDate, number
        List<String> colList =
            Arrays.stream(confluenceParameters.getOrDefault(PARAMETER_COLUMNS, EMPTY_STRING).split(DELIMITER_REGEX))
                .map(column -> COLUMN_MAP.getOrDefault(column, EMPTY_STRING))
                .filter(column -> !column.isEmpty())
                .collect(Collectors.toList());

        if (!colList.isEmpty()) {
            xwikiParams.put(PARAMETER_COLUMNS, String.join(DELIMITER, colList));
        }

        String confluenceDate = confluenceParameters.getOrDefault("createddateFrom", EMPTY_STRING);
        if (!confluenceDate.isEmpty()) {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
                Date date = simpleDateFormat.parse(confluenceDate);
                simpleDateFormat = new SimpleDateFormat(dateMacroConfiguration.getStorageDateFormat());
                xwikiParams.put("createdAfter", simpleDateFormat.format(date));
            } catch (Exception ignored) {

            }
        }

        return xwikiParams;
    }
}
