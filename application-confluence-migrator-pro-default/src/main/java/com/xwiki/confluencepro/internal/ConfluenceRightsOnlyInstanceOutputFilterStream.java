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
import org.jodconverter.core.util.StringUtils;
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
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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
@Named(ConfluenceRightsOnlyInstanceOutputFilterStream.ROLEHINT)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class ConfluenceRightsOnlyInstanceOutputFilterStream
    implements OutputFilterStream, EntityOutputFilterStream<Object>, OutputFilterStreamFactory
{
    protected static final String ROLEHINT = "confluence+rightsonly";

    private static final class FilteredObject
    {
        private final String name;
        private final FilterEventParameters parameters;
        private final List<FilteredObjectField> fields = new ArrayList<>();

        FilteredObject(String name, FilterEventParameters parameters)
        {
            this.name = name;
            this.parameters = parameters;
        }

        void add(FilteredObjectField p)
        {
            this.fields.add(p);
        }
    }

    private static final class FilteredObjectField
    {
        private final String name;
        private final Object value;

        FilteredObjectField(String name, Object value)
        {
            this.name = name;
            this.value = value;
        }
    }

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

    private FilteredObject currentObject;

    private List<FilteredObject> currentObjects;

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

    @Override
    public void onWikiAttachment(String name, InputStream content, Long size, FilterEventParameters parameters)
        throws FilterException
    {
        // ignoreObject
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
        currentObjects = null;
        EntityReference wiki = currentEntityReference.extractReference(EntityType.WIKI);
        if (wiki == null) {
            currentEntityReference = currentEntityReference.appendParent(contextProvider.get().getWikiReference());
        }
        currentEntityReference = new DocumentReference(currentEntityReference, locale);
    }

    @Override
    public void endWikiDocumentLocale(Locale locale, FilterEventParameters parameters) throws FilterException
    {
        saveObjectsInCurrentDocumentNoThrow();
        currentEntityReference = new DocumentReference(currentEntityReference, (Locale) null);
    }

    @Override
    public void beginWikiDocumentRevision(String revision, FilterEventParameters parameters) throws FilterException
    {
        // We only change the objects of the last revision
        currentObjects = null;
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
        currentObjects = null;
    }

    @Override
    public void endWikiDocument(String name, FilterEventParameters parameters) throws FilterException
    {
        saveObjectsInCurrentDocumentNoThrow();
        this.currentEntityReference = this.currentEntityReference.getParent();
    }

    private void saveObjectsInCurrentDocumentNoThrow()
    {
        try {
            saveObjectsInCurrentDocument();
        } catch (Exception e) {
            // saveObjectsInCurrentDocument is only supposed to throw XWikiException, but setting object fields can
            // throw NPE if the base class isn't found in the wiki.
            logger.error("Failed to update document [{}]", currentEntityReference, e);
        }
    }

    private void saveObjectsInCurrentDocument() throws XWikiException
    {
        if (currentObjects == null) {
            // no object to save. We don't remove existing objects. Nothing to do.
            return;
        }

        XWikiContext context = contextProvider.get();
        XWikiDocument doc = context.getWiki().getDocument(currentEntityReference, context).clone();

        Map<EntityReference, Queue<BaseObject>> xobjectsByClass = new HashMap<>();
        for (FilteredObject o : currentObjects) {
            // We ignore PARAMETER_NUMBER and PARAMETER_GUID, this code is already complicated enough,
            // and we don't use that in the migrator
            EntityReference classReference = getClassReference(o.name, o.parameters);
            List<BaseObject> xobjectsWithNulls = doc.getXObjects(classReference);
            Queue<BaseObject> xobjects = xobjectsByClass.get(classReference);
            if (xobjects == null) {
                xobjects = new ArrayDeque<>(xobjectsWithNulls.size());
                xobjectsByClass.put(classReference, xobjects);
                for (BaseObject object : xobjectsWithNulls) {
                    if (object != null) {
                        xobjects.offer(object);
                    }
                }
            }
            BaseObject obj = xobjects.poll();
            Set<String> fieldNames;
            if (obj == null) {
                fieldNames = new HashSet<>();
                obj = doc.newXObject(classReference, context);
            } else {
                fieldNames = new HashSet<>(obj.getPropertyList());
            }

            updateFields(o, fieldNames, obj);
        }

        // We remove all the existing objects that we didn't reuse of classes we saw
        for (Queue<BaseObject> remainingObjects : xobjectsByClass.values()) {
            for (BaseObject o : remainingObjects) {
                doc.removeXObject(o);
            }
        }

        try {
            context.getWiki().saveDocument(doc, "Reimport objects", context);
        } catch (XWikiException e) {
            logger.error("Could not save document [{}]", currentEntityReference, e);
        }
    }

    private void updateFields(FilteredObject o, Set<String> fieldNames, BaseObject obj)
    {
        XWikiContext context = contextProvider.get();
        for (FilteredObjectField f : o.fields) {
            fieldNames.remove(f.name);
            obj.set(f.name, f.value, context);
        }

        // We remove fields that are not set
        for (String fieldName : fieldNames) {
            obj.removeField(fieldName);
        }
    }

    private EntityReference getClassReference(String className, FilterEventParameters parameters)
    {
        Object value = parameters.get(WikiObjectFilter.PARAMETER_CLASS_REFERENCE);
        if (value instanceof EntityReference) {
            return (EntityReference) value;
        }

        if (value instanceof String) {
            return documentStringResolver.resolve((String) value, EntityType.DOCUMENT);
        }

        if (StringUtils.isNotEmpty(className)) {
            return documentStringResolver.resolve(className, EntityType.DOCUMENT);
        }

        return new BaseObjectReference(this.currentEntityReference).getXClassReference();
    }


    @Override
    public void beginWikiObject(String name, FilterEventParameters parameters) throws FilterException
    {
        if (name == null) {
            return;
        }

        this.currentEntityReference = new EntityReference(name, EntityType.OBJECT, this.currentEntityReference);
        if (!"XWiki.XWikiGlobalRights".equals(name) && !"XWiki.XWikiRights".equals(name)) {
            // We only import rights
            return;
        }

        if (currentObjects == null) {
            currentObjects = new ArrayList<>();
        }
        currentObject = new FilteredObject(name, parameters);
        currentObjects.add(currentObject);
    }

    @Override
    public void endWikiObject(String name, FilterEventParameters parameters) throws FilterException
    {
        currentObject = null;
        if (this.currentEntityReference.getType() == EntityType.OBJECT) {
            this.currentEntityReference = this.currentEntityReference.getParent();
        }
    }

    @Override
    public void onWikiObjectProperty(String name, Object value, FilterEventParameters parameters) throws FilterException
    {
        if (currentObject != null) {
            currentObject.add(new FilteredObjectField(name, value));
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
        return new ConfluenceRightsOnlyInstanceOutputFilterStreamDescriptor();
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
    public void close()
    {
        // ignore
    }
}
