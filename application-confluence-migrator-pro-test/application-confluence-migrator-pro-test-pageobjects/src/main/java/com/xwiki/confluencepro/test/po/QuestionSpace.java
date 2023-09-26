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
package com.xwiki.confluencepro.test.po;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Represents actions that can be done on the running view of a migration page.
 *
 * @version $Id$
 * @since 1.0
 */
public class QuestionSpace extends BaseElement
{
    private static final String QUESTION_SELECTOR = ".available-spaces .available-space:nth-child(%d)";

    private final int index;

    public QuestionSpace(int index)
    {
        this.index = index;
    }

    public WebElement getCheckbox()
    {
        return getDriver().findElement(
            By.cssSelector(String.format(".available-spaces .available-space:nth-child(%d) input", index)));
    }

    public String getSpaceName()
    {
        return getDriver().findElement(
            By.cssSelector(String.format(".available-spaces .available-space:nth-child(%d) label", index))).getText();
    }
}
