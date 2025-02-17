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
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.descriptor.FilterStreamDescriptor;
import org.xwiki.filter.event.model.WikiAttachmentFilter;
import org.xwiki.filter.input.DefaultInputStreamInputSource;
import org.xwiki.filter.input.InputSource;
import org.xwiki.filter.input.InputStreamInputSource;
import org.xwiki.filter.instance.output.DocumentInstanceOutputProperties;
import org.xwiki.filter.output.OutputFilterStream;
import org.xwiki.filter.output.OutputFilterStreamFactory;
import org.xwiki.filter.type.FilterStreamType;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentArchive;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.doc.ListAttachmentArchive;
import com.xpn.xwiki.internal.filter.output.EntityOutputFilterStream;
import com.xpn.xwiki.store.AttachmentVersioningStore;
import com.xpn.xwiki.store.XWikiHibernateBaseStore;
import com.xpn.xwiki.user.api.XWikiRightService;

/**
 * An output filter stream that only keeps objects, keeping documents as is.
 * Objects of the same class as received objects are removed from documents.
 * @since 1.1.0
 * @version $Id$
 *
 * Most of this code is copy-pasted from:
 *  - com.xpn.xwiki.internal.filter.output.AbstractEntityOutputFilterStream
 *  - com.xpn.xwiki.internal.filter.output.BaseObjectOutputFilterStream
 */
@Component
@Named(ConfluenceAttachmentsOnlyInstanceOutputFilterStream.ROLEHINT)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class ConfluenceAttachmentsOnlyInstanceOutputFilterStream
    implements OutputFilterStream, EntityOutputFilterStream<Object>, OutputFilterStreamFactory
{
    protected static final String ROLEHINT = "confluence+attachmentsonly";

    protected static final Pattern VALID_VERSION = Pattern.compile("\\d*\\.\\d*");

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    @Inject
    @Named("user/current")
    private DocumentReferenceResolver<String> userStringResolver;

    @Inject
    @Named("user/current")
    private DocumentReferenceResolver<EntityReference> userEntityResolver;

    @Inject
    private Provider<ComponentManager> componentManagerProvider;

    private boolean enabled = true;

    private EntityReference currentEntityReference;

    private XWikiDocument doc;

    private XWikiAttachment currentAttachment;

    private ListAttachmentArchive attachmentArchive;

    private AttachmentVersioningStore archiveStore;

    private AttachmentVersioningStore getAttachmentVersioningStore()
        throws ComponentLookupException
    {
        if (this.archiveStore == null && this.currentAttachment != null) {
            if (this.currentAttachment.isArchiveStoreSet()) {
                String hint = Objects.requireNonNullElse(this.currentAttachment.getArchiveStore(),
                    XWikiHibernateBaseStore.HINT);
                this.archiveStore = componentManagerProvider.get().getInstance(AttachmentVersioningStore.class, hint);
            } else {
                this.archiveStore =
                    contextProvider.get().getWiki().getDefaultAttachmentArchiveStore();

                this.currentAttachment.setArchiveStore(this.archiveStore.getHint());
            }
        }
        return this.archiveStore;
    }

    private ListAttachmentArchive getArchive() throws FilterException
    {
        // FIXME: ListAttachmentArchive is internal
        if (this.currentAttachment == null) {
            return null;
        }

        if (this.attachmentArchive == null) {
            try {
                XWikiContext context = contextProvider.get();
                XWikiAttachmentArchive archive = this.currentAttachment.getAttachmentArchive(context);
                this.attachmentArchive = (archive instanceof ListAttachmentArchive)
                    ? (ListAttachmentArchive) archive
                    : new ListAttachmentArchive(
                        Arrays.stream(this.currentAttachment.getVersions())
                            .map(v -> {
                                try {
                                    return archive.getRevision(this.currentAttachment, v.toString(), context);
                                } catch (XWikiException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .collect(Collectors.toList())
                    );
            } catch (Exception e) {
                throw new FilterException(e);
            }
        }
        return this.attachmentArchive;
    }


    @Override
    public Object getFilter()
    {
        return this;
    }

    @Override
    public Object getEntity()
    {
        return null;
    }

    @Override
    public void setEntity(Object entity)
    {
        // ignore
    }

    @Override
    public void setProperties(DocumentInstanceOutputProperties properties)
    {
        // ignore
    }

    @Override
    public boolean isEnabled()
    {
        return enabled;
    }

    @Override
    public void enable()
    {
        enabled = true;
    }

    @Override
    public void disable()
    {
        enabled = false;
    }

    @Override
    public void onWikiAttachment(String name, InputStream content, Long size, FilterEventParameters parameters)
        throws FilterException
    {
        beginWikiDocumentAttachment(name, content != null ? new DefaultInputStreamInputSource(content) : null, size,
            parameters);
        endWikiDocumentAttachment(name, null, size, parameters);
    }

    @Override
    public void beginWikiDocumentAttachment(String name, InputSource content, Long size,
        FilterEventParameters parameters) throws FilterException
    {
        initializeDoc();
        if (this.doc == null) {
            return;
        }

        this.currentAttachment = this.doc.getAttachment(name);
        if (this.currentAttachment == null) {
            this.currentAttachment = createAttachment(name, content, size, parameters);
            this.doc.setAttachment(this.currentAttachment);
        } else {
            String version = getString(WikiAttachmentFilter.PARAMETER_REVISION, parameters, null);
            beginWikiAttachmentRevision(version, content, size, parameters);
        }
    }

    private XWikiAttachment createAttachment(String name, InputSource source, Long size,
        FilterEventParameters parameters) throws FilterException
    {
        XWikiAttachment attachment = new XWikiAttachment();

        fillAttachment(attachment, name, source, size, parameters);

        return attachment;
    }

    private <T> T get(Type type, String key, FilterEventParameters parameters, T def)
    {
        return get(type, key, parameters, def, true);
    }

    private <T> T get(Type type, String key, FilterEventParameters parameters, T def, boolean replaceNull)
    {
        if (parameters == null) {
            return def;
        }

        if (!parameters.containsKey(key)) {
            return def;
        }

        Object value = parameters.get(key);

        if (value == null) {
            return replaceNull ? def : null;
        }

        if (TypeUtils.isInstance(value, type)) {
            return (T) value;
        }

        return null;
    }

    protected String getString(String key, FilterEventParameters parameters, String def)
    {
        return get(String.class, key, parameters, def);
    }

    protected Date getDate(String key, FilterEventParameters parameters, Date def)
    {
        return get(Date.class, key, parameters, def);
    }

    private String fixVersion(String version)
    {
        if (StringUtils.isEmpty(version)) {
            return null;
        }

        if (VALID_VERSION.matcher(version).matches()) {
            return version;
        }

        if (NumberUtils.isDigits(version)) {
            return version + ".1";
        }

        logger.warn("Failed to set a proper revision number from version [{}]", version);
        return null;
    }

    private void setVersion(XWikiAttachment attachment, FilterEventParameters parameters)
    {
        if (parameters.containsKey(WikiAttachmentFilter.PARAMETER_REVISION)) {
            String version = getString(WikiAttachmentFilter.PARAMETER_REVISION, parameters, null);
            String revision = fixVersion(version);
            if (revision != null) {
                attachment.setVersion(revision);
            }
        }
    }

    protected DocumentReference getUserDocumentReference(String key, FilterEventParameters parameters)
    {
        DocumentReference userReference = null;

        Object reference = get(Object.class, key, parameters, (DocumentReference) null, false);

        if (reference != null && !(reference instanceof DocumentReference)) {
            userReference = toUserDocumentReference(reference);
        }

        return userReference;
    }

    protected DocumentReference toUserDocumentReference(Object reference)
    {
        DocumentReference userDocumentReference;

        if (reference instanceof EntityReference) {
            userDocumentReference =
                this.userEntityResolver.resolve((EntityReference) reference, this.currentEntityReference != null
                    ? this.currentEntityReference.extractReference(EntityType.WIKI) : null);
        } else {
            userDocumentReference =
                this.userStringResolver.resolve(reference.toString(), this.currentEntityReference != null
                    ? this.currentEntityReference.extractReference(EntityType.WIKI) : null);
        }

        if (userDocumentReference != null && userDocumentReference.getName().equals(XWikiRightService.GUEST_USER)) {
            userDocumentReference = null;
        }

        return userDocumentReference;
    }


    private void fillAttachment(XWikiAttachment attachment, String name, InputSource source, Long size,
        FilterEventParameters parameters) throws FilterException
    {
        attachment.setFilename(name);
        if (size != null) {
            attachment.setLongSize(size);
        }
        attachment.setMimeType(getString(WikiAttachmentFilter.PARAMETER_MIMETYPE, parameters, null));
        attachment.setCharset(getString(WikiAttachmentFilter.PARAMETER_CHARSET, parameters, null));

        fillAttachmentContent(attachment, source);

        // Author

        attachment.setAuthorReference(
            getUserDocumentReference(WikiAttachmentFilter.PARAMETER_REVISION_AUTHOR, parameters));

        // Revision

        setVersion(attachment, parameters);
        attachment.setComment(getString(WikiAttachmentFilter.PARAMETER_REVISION_COMMENT, parameters, ""));
        attachment.setDate(getDate(WikiAttachmentFilter.PARAMETER_REVISION_DATE, parameters, new Date()));
        attachment.setMetaDataDirty(false);
    }

    private void fillAttachmentContent(XWikiAttachment attachment, InputSource source)
        throws FilterException
    {
        if (source != null) {
            if (source instanceof InputStreamInputSource) {
                try (InputStreamInputSource streamSource = (InputStreamInputSource) source) {
                    attachment.setContent(streamSource.getInputStream());
                } catch (IOException e) {
                    throw new FilterException("Failed to set attachment content", e);
                }
            } else {
                throw new FilterException(
                    "Unsupported input stream type [" + source.getClass() + "] for the attachment content");
            }
        }
    }

    private void initializeDoc() throws FilterException
    {
        if (this.currentEntityReference == null || this.currentEntityReference.getType() != EntityType.DOCUMENT) {
            return;
        }

        XWikiContext context = contextProvider.get();
        try {
            this.doc = context.getWiki().getDocument(this.currentEntityReference, context);
        } catch (XWikiException e) {
            throw new FilterException("Failed to load document [" + this.currentEntityReference + "]", e);
        }
    }

    @Override
    public void endWikiDocumentAttachment(String name, InputSource content, Long size, FilterEventParameters parameters)
    {
        this.currentAttachment = null;
        this.archiveStore = null;
        this.attachmentArchive = null;
    }

    @Override
    public void beginWikiAttachmentRevision(String version, InputSource content, Long size,
        FilterEventParameters parameters) throws FilterException
    {
        if (this.currentAttachment == null || !shouldPushRevision(version)) {
            return;
        }

        ListAttachmentArchive archive = getArchive();
        if (archive == null) {
            logger.warn("Could not get the attachment archive for [{}]", this.currentAttachment.getFilename());
            return;
        }
        try {
            archive.add(createAttachment(this.currentAttachment.getFilename(), content, size, parameters));
            AttachmentVersioningStore attachmentVersioningStore = getAttachmentVersioningStore();
            if (attachmentVersioningStore == null) {
                logger.warn("Could not get the attachment archive store for [{}]",
                    this.currentAttachment.getFilename());
                return;
            }
            attachmentVersioningStore.saveArchive(archive, contextProvider.get(), true);
        } catch (ComponentLookupException | XWikiException e) {
            throw new FilterException(e);
        }
    }

    private boolean shouldPushRevision(String version) throws FilterException
    {
        String revision = fixVersion(version);
        if (revision == null) {
            return false;
        }

        XWikiAttachment attachmentRevision;
        XWikiContext xcontext = contextProvider.get();
        try {
            attachmentRevision =
                this.currentAttachment.getAttachmentRevision(revision, xcontext);
        } catch (XWikiException e) {
            throw new FilterException(e);
        }

        // only push non-existing revisions.
        return attachmentRevision == null;
    }

    @Override
    public void endWikiAttachmentRevision(String version, InputSource content, Long size,
        FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void beginWikiClass(FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void endWikiClass(FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void beginWikiClassProperty(String name, String type, FilterEventParameters parameters)

    {
        // ignore
    }

    @Override
    public void endWikiClassProperty(String name, String type, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void onWikiClassPropertyField(String name, String value, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void beginWikiDocumentLocale(Locale locale, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void endWikiDocumentLocale(Locale locale, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void beginWikiDocumentRevision(String revision, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void endWikiDocumentRevision(String revision, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void beginWiki(String name, FilterEventParameters parameters)
    {
        this.currentEntityReference = new EntityReference(name, EntityType.WIKI, this.currentEntityReference);
    }

    @Override
    public void endWiki(String name, FilterEventParameters parameters)
    {
        this.currentEntityReference = this.currentEntityReference.getParent();
    }

    @Override
    public void beginWikiSpace(String name, FilterEventParameters parameters)
    {
        this.currentEntityReference = new EntityReference(name, EntityType.SPACE, this.currentEntityReference);
    }

    @Override
    public void endWikiSpace(String name, FilterEventParameters parameters)
    {
        this.currentEntityReference = this.currentEntityReference.getParent();
    }

    @Override
    public void beginWikiDocument(String name, FilterEventParameters parameters)
    {
        this.currentEntityReference = new EntityReference(name, EntityType.DOCUMENT, this.currentEntityReference);
    }

    @Override
    public void endWikiDocument(String name, FilterEventParameters parameters) throws FilterException
    {
        this.currentEntityReference = this.currentEntityReference.getParent();
        this.doc = null;
    }

    @Override
    public void beginWikiObject(String name, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void endWikiObject(String name, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public void onWikiObjectProperty(String name, Object value, FilterEventParameters parameters)
    {
        // ignore
    }

    @Override
    public FilterStreamType getType()
    {
        return null;
    }

    @Override
    public FilterStreamDescriptor getDescriptor()
    {
        return new ConfluenceAttachmentsOnlyInstanceOutputFilterStreamDescriptor();
    }

    @Override
    public Collection<Class<?>> getFilterInterfaces()
    {
        return null;
    }

    @Override
    public OutputFilterStream createOutputFilterStream(Map<String, Object> properties)
    {
        return this;
    }

    @Override
    public void close() throws IOException
    {
        // ignore
    }
}
