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

import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.descriptor.DefaultComponentDependency;
import org.xwiki.component.descriptor.DefaultComponentDescriptor;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.manager.ComponentRepositoryException;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.extension.repository.CoreExtensionRepository;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.filter.FilterDescriptorManager;
import org.xwiki.filter.instance.output.DocumentInstanceOutputProperties;
import org.xwiki.filter.output.BeanOutputFilterStream;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.internal.filter.output.DocumentInstanceOutputFilterStreamFactory;
import com.xpn.xwiki.internal.filter.output.EntityOutputFilterStream;

/**
 * Listener that will replace the {@link com.xpn.xwiki.internal.filter.output.DocumentInstanceOutputFilterStream} with a
 * copy of it from version 15.10.6+. The purpose of this listener is to work around the issue XWIKI-21801: Duplicate
 * versions in history of documents not based on JRCS.
 *
 * @version $Id$
 * @since 1.8.3
 */
// TODO: Remove this class once the parent of the app will be >= 15.10.6
@Component
@Named(DocumentFilterOverrideListener.ROLE_HINT)
@Singleton
public class DocumentFilterOverrideListener extends AbstractEventListener implements Initializable
{
    /**
     * The role hint.
     */
    public static final String ROLE_HINT = "com.xwiki.confluencepro.internal.DocumentFilterOverrideListener";

    @Inject
    private ComponentManager componentManager;

    @Inject
    private CoreExtensionRepository coreExtensionRepository;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public DocumentFilterOverrideListener()
    {
        super(ROLE_HINT, Collections.emptyList());
    }

    @Override
    public void initialize() throws InitializationException
    {
        // When this component is initialized (on instance start up / new version install) it should replace the
        // DocumentInstanceOutputFilterStream from the component manager.
        maybeReplaceDocumentFilter();
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        // Do nothing as the purpose of this class is fulfilled at initialization time.
    }

    private void maybeReplaceDocumentFilter()
    {
        if (shouldOverrideDocumentFilter()) {

            DefaultComponentDescriptor<BeanOutputFilterStream<DocumentInstanceOutputProperties>> newDescriptor =
                new DefaultComponentDescriptor<>();

            newDescriptor.setImplementation(DocumentInstanceOutputFilterStream.class);
            newDescriptor.setInstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP);
            newDescriptor.setRoleHint(DocumentInstanceOutputFilterStreamFactory.ROLEHINT);
            newDescriptor.setRoleType(new DefaultParameterizedType(null, BeanOutputFilterStream.class,
                DocumentInstanceOutputProperties.class));

            // Register dependencies
            DefaultComponentDependency<Provider<XWikiContext>> contextProviderDependency =
                new DefaultComponentDependency<>();
            contextProviderDependency.setRoleType(
                new DefaultParameterizedType(null, Provider.class, XWikiContext.class));
            contextProviderDependency.setName("xcontextProvider");

            DefaultComponentDependency<EntityOutputFilterStream<XWikiDocument>> documentListenerDependency =
                new DefaultComponentDependency<>();
            documentListenerDependency.setRoleType(
                new DefaultParameterizedType(null, EntityOutputFilterStream.class, XWikiDocument.class));
            documentListenerDependency.setName("documentListener");

            DefaultComponentDependency<UserReferenceResolver<DocumentReference>> userResolverDependency =
                new DefaultComponentDependency<>();
            userResolverDependency.setRoleType(
                new DefaultParameterizedType(null, UserReferenceResolver.class, DocumentReference.class));
            userResolverDependency.setRoleHint("document");
            userResolverDependency.setName("documentReferenceUserReferenceResolver");

            DefaultComponentDependency<Logger> loggerDependency = new DefaultComponentDependency<>();
            loggerDependency.setRoleType(Logger.class);
            loggerDependency.setName("logger");

            DefaultComponentDependency<FilterDescriptorManager> filterDescriptorDependency =
                new DefaultComponentDependency<>();
            filterDescriptorDependency.setRoleType(FilterDescriptorManager.class);
            filterDescriptorDependency.setName("filterManager");

            newDescriptor.addComponentDependency(filterDescriptorDependency);
            newDescriptor.addComponentDependency(loggerDependency);
            newDescriptor.addComponentDependency(contextProviderDependency);
            newDescriptor.addComponentDependency(documentListenerDependency);
            newDescriptor.addComponentDependency(userResolverDependency);

            try {
                componentManager.registerComponent(newDescriptor);
            } catch (ComponentRepositoryException e) {
                logger.warn("Failed to replace the DocumentInstanceOutputFilterStream class from the component "
                    + "manager. Cause: [{}].", ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }

    private boolean shouldOverrideDocumentFilter()
    {
        return coreExtensionRepository.getEnvironmentExtension().getId().getVersion()
            .compareTo(new DefaultVersion("15.10.6")) <= 0;
    }
}
