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

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.filter.output.EntityOutputFilterStream;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseObjectReference;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.descriptor.FilterStreamDescriptor;
import org.xwiki.filter.event.model.WikiObjectFilter;
import org.xwiki.filter.instance.output.DocumentInstanceOutputProperties;
import org.slf4j.Logger;
import org.xwiki.filter.output.OutputFilterStream;
import org.xwiki.filter.output.OutputFilterStreamFactory;
import org.xwiki.filter.type.FilterStreamType;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.properties.ConverterManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
@Named(ConfluenceObjectsOnlyInstanceOutputFilterStream.ROLEHINT)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class ConfluenceObjectsOnlyInstanceOutputFilterStream
    implements OutputFilterStream, EntityOutputFilterStream<Object>, OutputFilterStreamFactory
{
    protected static final String ROLEHINT = "confluence+objectsonly";
    private static final String WEB_PREFERENCES = "WebPreferences";
    private static final String WEB_HOME = "WebHome";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<EntityReference> documentEntityResolver;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentStringResolver;

    @Inject
    private ConverterManager converter;

    private boolean enabled = true;

    private EntityReference currentEntityReference;

    private BaseObject currentObject;

    private List<BaseObject> currentObjects = new ArrayList<>();

    private DocumentInstanceOutputProperties properties;

    @Inject
    @Named("relative")
    private EntityReferenceResolver<String> relativeResolver;

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
        this.properties = properties;
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

    private EntityReference getDefaultDocumentReference()
    {
        if (this.properties != null && this.properties.getDefaultReference() != null) {
            return this.properties.getDefaultReference();
        }

        return null;
    }

    @Override
    public void onWikiAttachment(String name, InputStream content, Long size, FilterEventParameters parameters)
        throws FilterException
    {
        // ignore
    }

    @Override
    public void beginWikiClass(FilterEventParameters parameters) throws FilterException
    {
        // ignore
    }

    @Override
    public void endWikiClass(FilterEventParameters parameters) throws FilterException
    {
        // ignore
    }

    @Override
    public void beginWikiClassProperty(String name, String type, FilterEventParameters parameters)
        throws FilterException
    {
        // ignore
    }

    @Override
    public void endWikiClassProperty(String name, String type, FilterEventParameters parameters) throws FilterException
    {
        // ignore
    }

    @Override
    public void onWikiClassPropertyField(String name, String value, FilterEventParameters parameters)
        throws FilterException
    {
        // ignore
    }

    @Override
    public void beginWikiDocumentLocale(Locale locale, FilterEventParameters parameters) throws FilterException
    {
        // ignore
    }

    @Override
    public void endWikiDocumentLocale(Locale locale, FilterEventParameters parameters) throws FilterException
    {
        // ignore
    }

    @Override
    public void beginWikiDocumentRevision(String revision, FilterEventParameters parameters) throws FilterException
    {
        // ignore
    }

    @Override
    public void endWikiDocumentRevision(String revision, FilterEventParameters parameters) throws FilterException
    {
        // ignore
    }

    @Override
    public void beginWiki(String name, FilterEventParameters parameters) throws FilterException
    {
        this.currentEntityReference = new EntityReference(name, EntityType.WIKI, this.currentEntityReference);
    }

    @Override
    public void endWiki(String name, FilterEventParameters parameters) throws FilterException
    {
        this.currentEntityReference = this.currentEntityReference.getParent();
    }

    @Override
    public void beginWikiSpace(String name, FilterEventParameters parameters) throws FilterException
    {
        this.currentEntityReference = new EntityReference(name, EntityType.SPACE, this.currentEntityReference);
    }

    @Override
    public void endWikiSpace(String name, FilterEventParameters parameters) throws FilterException
    {
        this.currentEntityReference = this.currentEntityReference.getParent();
    }

    @Override
    public void beginWikiDocument(String name, FilterEventParameters parameters) throws FilterException
    {
        this.currentEntityReference = new EntityReference(name, EntityType.DOCUMENT, this.currentEntityReference);
        currentObjects.clear();
    }

    @Override
    public void endWikiDocument(String name, FilterEventParameters parameters) throws FilterException
    {
        if (!currentObjects.isEmpty()) {
            XWikiDocument doc = null;
            EntityReference docRef = this.currentEntityReference;
            if (!WEB_PREFERENCES.equals(docRef.getName()) && !WEB_HOME.equals(docRef.getName())) {
                EntityReference spaceRef = new EntityReference(this.currentEntityReference.getName(), EntityType.SPACE,
                    this.currentEntityReference.getParent());
                docRef = new EntityReference(WEB_HOME, EntityType.DOCUMENT, spaceRef);
            }
            try {
                doc = contextProvider.get().getWiki().getDocument(docRef, contextProvider.get());
            } catch (XWikiException e) {
                logger.error("Could not get document [{}]", docRef, e);
            }

            if (doc != null) {
                for (BaseObject o : currentObjects) {
                    doc.removeXObjects(o.getXClassReference());
                }

                for (BaseObject o : currentObjects) {
                    doc.addXObject(o);
                }

                try {
                    contextProvider.get().getWiki().saveDocument(doc, contextProvider.get());
                    logger.info("Saved document [{}]", docRef);
                } catch (XWikiException e) {
                    logger.error("Could not save document [{}]", docRef, e);
                }
            }

            currentObjects.clear();
        }

        this.currentEntityReference = this.currentEntityReference.getParent();
    }

    private <T> T get(Type type, String key, FilterEventParameters parameters, T def)
    {
        return get(type, key, parameters, def, true, true);
    }

    private <T> T get(Type type, String key, FilterEventParameters parameters, T def, boolean replaceNull,
                        boolean convert)
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

        return convert ? this.converter.convert(type, value) : (T) value;
    }

    private int getInt(String key, FilterEventParameters parameters, int def)
    {
        return get(int.class, key, parameters, def);
    }

    private DocumentReference getDocumentReference(String key, FilterEventParameters parameters,
                                                     DocumentReference def)
    {
        Object reference = get(Object.class, key, parameters, def, false, false);

        if (reference != null && !(reference instanceof DocumentReference)) {
            if (reference instanceof EntityReference) {
                reference =
                    this.documentEntityResolver.resolve((EntityReference) reference, this.currentEntityReference);
            } else {
                reference = this.documentStringResolver.resolve(reference.toString(), this.currentEntityReference);
            }
        }

        return (DocumentReference) reference;
    }

    private EntityReference getEntityReference(String key, FilterEventParameters parameters, EntityReference def)
    {
        Object reference = get(Object.class, key, parameters, def, false, false);

        if (reference != null && !(reference instanceof EntityReference)) {
            reference = this.relativeResolver.resolve(reference.toString(), EntityType.DOCUMENT);
        }

        return (EntityReference) reference;
    }

    private String getString(String key, FilterEventParameters parameters, String def)
    {
        return get(String.class, key, parameters, def);
    }

    @Override
    public void beginWikiObject(String name, FilterEventParameters parameters) throws FilterException
    {
        if (name == null) {
            return;
        }

        this.currentEntityReference = new EntityReference(name, EntityType.OBJECT, this.currentEntityReference);
        currentObject = new BaseObject();

        if (parameters.containsKey(WikiObjectFilter.PARAMETER_NAME)) {
            currentObject
                .setDocumentReference(getDocumentReference(WikiObjectFilter.PARAMETER_NAME, parameters, null));
        }

        int number = getInt(WikiObjectFilter.PARAMETER_NUMBER, parameters, -1);

        EntityReference classReference =
            getEntityReference(WikiObjectFilter.PARAMETER_CLASS_REFERENCE, parameters, null);
        if (classReference == null) {
            BaseObjectReference reference = new BaseObjectReference(this.currentEntityReference);

            classReference = reference.getXClassReference();

            if (number < 0 && reference.getObjectNumber() != null) {
                number = reference.getObjectNumber();
            }
        }
        currentObject.setXClassReference(classReference);
        currentObject.setNumber(number);
        currentObject.setGuid(getString(WikiObjectFilter.PARAMETER_GUID, parameters, null));
    }

    @Override
    public void endWikiObject(String name, FilterEventParameters parameters) throws FilterException
    {
        if (currentObject != null) {
            currentObjects.add(currentObject);
            currentObject = null;
        }

        if (this.currentEntityReference.getType() == EntityType.OBJECT) {
            this.currentEntityReference = this.currentEntityReference.getParent();
        }
    }

    @Override
    public void onWikiObjectProperty(String name, Object value, FilterEventParameters parameters) throws FilterException
    {
        if (currentObject != null) {
            currentObject.set(name, value, contextProvider.get());
        }
    }

    @Override
    public FilterStreamType getType()
    {
        return null;
    }

    @Override
    public FilterStreamDescriptor getDescriptor()
    {
        return null;
    }

    @Override
    public Collection<Class<?>> getFilterInterfaces() throws FilterException
    {
        return null;
    }

    @Override
    public OutputFilterStream createOutputFilterStream(Map<String, Object> properties) throws FilterException
    {
        return this;
    }

    @Override
    public void close() throws IOException
    {
        // ignore
    }
}
