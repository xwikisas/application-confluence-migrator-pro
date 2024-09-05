package com.xwiki.confluencepro.converters.internal;

import java.io.StringReader;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.confluence.filter.input.ConfluenceInputContext;
import org.xwiki.contrib.confluence.filter.input.ConfluenceInputProperties;
import org.xwiki.contrib.confluence.filter.internal.macros.AbstractMacroConverter;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;

@Component
@Singleton
@Named("roundrect")
public class RoundRectMacroConvertor extends AbstractMacroConverter
{
    @Inject
    private ConfluenceInputContext context;
    @Inject
    private ComponentManager componentManager;
    @Override
    public String toXWikiId(String confluenceId, Map<String, String> confluenceParameters, String confluenceContent,
        boolean inline)
    {
        return "panel";
    }


}
