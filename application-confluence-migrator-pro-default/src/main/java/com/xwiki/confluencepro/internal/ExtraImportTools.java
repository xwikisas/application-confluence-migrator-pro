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
package com.xwiki.confluencepro.internal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.contrib.confluence.filter.Mapping;
import org.xwiki.contrib.confluence.filter.input.ConfluenceInputContext;
import org.xwiki.contrib.confluence.filter.input.ConfluenceInputProperties;
import org.xwiki.contrib.confluence.filter.internal.input.DefaultConfluenceInputContext;
import org.xwiki.properties.converter.Converter;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;

/**
 * Tools for calendar and group importer.
 *
 * @version $Id$
 * @since 1.43.4
 */
@Component(roles = { ExtraImportTools.class })
@Singleton
public class ExtraImportTools
{
    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private ComponentManager componentManager;

    /**
     * @param userMappingStr the user mapping in string format
     * @param groupMappingStr the group mapping in string format
     * @param userFormat the user format to apply
     * @param groupFormat the group format to apply
     * @throws ComponentLookupException In case a component can't be found.
     */
    public void configureUserGroupMapping(String userMappingStr, String groupMappingStr, String userFormat,
        String groupFormat)
        throws ComponentLookupException
    {
        var converterMappingType = new DefaultParameterizedType(null, Converter.class, Mapping.class);
        Converter<Mapping> mappingConverter = componentManager.getInstance(converterMappingType);
        Mapping userMapping = mappingConverter.convert(Mapping.class, userMappingStr);
        Mapping groupMapping = mappingConverter.convert(Mapping.class, groupMappingStr);

        ConfluenceInputContext confluenceInputContext = componentManager.getInstance(ConfluenceInputContext.class);
        var confluenceProperties = new ConfluenceInputProperties();
        confluenceProperties.setUserIdMapping(userMapping);
        confluenceProperties.setGroupMapping(groupMapping);
        confluenceProperties.setUserFormat(userFormat);
        confluenceProperties.setGroupFormat(groupFormat);
        ((DefaultConfluenceInputContext) confluenceInputContext).set(null, confluenceProperties,
                Map.of());
    }

    /**
     * @param attachment the attachment to read
     * @return an input stream reader from the given attachment, taking care of ignoring, if present, the UTF-8 BOM
     *     which can cause hard to understand issues, since the Apache Common CSV parser doesn't support it. NOTE:
     *     Microsoft Excel can generate CSVs with BOM.
     */
    public InputStreamReader getInputStreamReader(XWikiAttachment attachment) throws XWikiException, IOException
    {
        return new InputStreamReader(
            BOMInputStream.builder().setInputStream(attachment.getContentInputStream(contextProvider.get())).get());
    }

    /**
     * @param isr The InpoutStreamReader used to build the CSV parser.
     * @return The CSV  parser configured for group and calendar import.
     * @throws IOException in case something goes wrong with the InputStreamReader.
     */
    public static CSVParser getCsvParser(InputStreamReader isr) throws IOException
    {
        return CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).setDelimiter(';').build()
            .parse(isr);
    }
}
