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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts link mapping objects from and to JSON strings.
 * @version $Id$
 * @since 1.12
 */
@Component(roles = LinkMappingConverter.class)
@Singleton
public class LinkMappingConverter
{
    @Inject
    private Logger logger;

    @Inject
    private EntityReferenceResolver<String> entityReferenceResolver;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    private final TypeReference<Map<String, EntityReference>> typeRef =
        new TypeReference<Map<String, EntityReference>>() { };

    private EntityReference ensureWiki(EntityReference docRef, WikiReference wikiReference)
    {
        return EntityType.WIKI.equals(docRef.getRoot().getType())
            ? docRef
            : docRef.appendParent(wikiReference);
    }

    Map<String, EntityReference> convertSpaceLinkMapping(String spaceMapping, String spaceKey)
        throws JsonProcessingException
    {
        if (StringUtils.isEmpty(spaceMapping)) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addDeserializer(EntityReference.class, new JsonDeserializer<EntityReference>()
        {
            @Override
            public EntityReference deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException
            {
                return entityReferenceResolver.resolve(
                    deserializationContext.readValue(jsonParser, String.class), EntityType.DOCUMENT);
            }
        });
        mapper.registerModule(simpleModule);
        try {
            return mapper.readValue(spaceMapping, typeRef);
        } catch (JsonProcessingException e) {
            logger.warn("Could not parse the previous link mapping for space [{}], ignoring it.", spaceKey, e);
            return new LinkedHashMap<>();
        }
    }

    String convertSpaceLinkMapping(Map<String, EntityReference> spaceMapping, WikiReference wikiReference)
        throws JsonProcessingException
    {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(EntityReference.class, new JsonSerializer<EntityReference>()
        {
            @Override
            public void serialize(EntityReference entityReference, JsonGenerator gen, SerializerProvider provider)
                throws IOException
            {
                gen.writeString(entityReferenceSerializer.serialize(ensureWiki(entityReference, wikiReference)));
            }
        });
        mapper.registerModule(simpleModule);
        return mapper.writeValueAsString(spaceMapping);
    }
}
