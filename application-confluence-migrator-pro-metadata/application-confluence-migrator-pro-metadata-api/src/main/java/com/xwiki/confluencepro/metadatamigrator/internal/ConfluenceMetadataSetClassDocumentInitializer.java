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
package com.xwiki.confluencepro.metadatamigrator.internal;

import java.util.Arrays;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.AbstractMandatoryClassInitializer;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.ListClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

/**
 * Update Confluence.Code.ConfluenceMetadataSetClass document with all required information.
 *
 * @version $Id$
 * @since 1.39.0
 */
@Component
@Named("Confluence.Code.ConfluenceMetadataSetClass")
@Singleton
public class ConfluenceMetadataSetClassDocumentInitializer extends AbstractMandatoryClassInitializer
{

    /**
     * Local reference of the XWikiUsers class document.
     */
    static final LocalDocumentReference DOCUMENT_REFERENCE =
        new LocalDocumentReference(Arrays.asList("Confluence", "Code"), "ConfluenceMetadataSetClass");

    private static final String COMMA = ",";

    /**
     * Default constructor.
     */
    public ConfluenceMetadataSetClassDocumentInitializer()
    {
        super(DOCUMENT_REFERENCE);
    }

    @Override
    protected void createClass(BaseClass xclass)
    {
        xclass.addTextField("key", "Key", 30);
        xclass.addTextField("lowerKey", "Lower key", 30);
        xclass.addTextField("spaceKey", "Space", 30);
        xclass.addTextField("lowerSpaceKey", "Lower space", 30);
        xclass.addTextField("title", "Title", 30);
        xclass.addTextAreaField("description", "Description", 80, 15,
            TextAreaClass.EditorType.PURE_TEXT, TextAreaClass.ContentType.PURE_TEXT);
        xclass.addStaticListField(
            "fields", "Fields", 10, true, true, "",
            null, COMMA, "", ListClass.FREE_TEXT_ALLOWED, false);
        xclass.addStaticListField(
            "lowerFields", "Lower fields", 10, true, true, "",
            null, COMMA, "", ListClass.FREE_TEXT_ALLOWED, false);
    }
}
