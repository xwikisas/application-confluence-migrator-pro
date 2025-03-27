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

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.editor.EditPage;

/**
 * Represents actions that can be done on the create view of a migration page.
 *
 * @version $Id$
 * @since 1.0
 */
public class MigrationCreationPage extends EditPage
{
    public void clickAdvancedMigrationOptions() {
        getDriver().findElement(By.className("advanced-fields")).click();
    }
    public void setTitle(String title) {
        WebElement webElement =  getDriver().findElement(By.id("xwikidoctitleinput"));
        webElement.clear();
        webElement.sendKeys(title);
    }

    public List<WebElement> getAdvancedInputFilterProperties() {
        return getDriver().findElements(By.className("cfm-input-filter-property"));
    }

    public List<WebElement> getAdvancedOutputProperties() {
        return getDriver().findElements(By.className("cfm-output-filter-property"));
    }

    public void fillOption(String optionName, String value)
    {
       WebElement option = getDriver().findElement(By.cssSelector(String.format(".cfm-input-filter-property[data-key"
           + "='%s']", optionName)));
       option.clear();;
       option.sendKeys(value);
    }

    public String getOptionValue(String optionName)
    {
        WebElement option = getDriver().findElement(By.cssSelector(String.format(".cfm-input-filter-property[data-key"
            + "='%s']", optionName)));
        return option.getAttribute("value").trim();
    }
}
