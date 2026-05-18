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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Represents actions that can be done on the raport view of a migration page.
 *
 * @version $Id$
 * @since 1.0
 */
public class MigrationRaportView extends ViewPage
{
    public List<String> getImportedSpaces()
    {
        return getDriver().findElements(By.cssSelector("#cfm-doc-tree summary > a"))
            .stream()
            .map(WebElement::getText)
            .filter(e -> !e.isEmpty())
            .collect(Collectors.toList());
    }

    public boolean hasErrorLogs()
    {
        return !getDriver().findElements(By.cssSelector(".log .log-item-error")).isEmpty();
    }

    public int getPagesCount()
    {
        WebElement span = getDriver().findElement(By.xpath("//span[contains(text(),'imported pages')]"));

        String text = span.getText();
        int index = text.indexOf(" imported pages");
        String before = text.substring(0, index);
        String numberString = before.replaceAll("\\D+", "");

        return Integer.parseInt(numberString);
    }

    public ViewPage clickPageLink(String spaceName, String pageName)
    {
        String spaceXPath = String.format(
            "//div[@id='cfm-doc-tree']//summary/a[normalize-space(text())='%s']/ancestor::details",
            spaceName
        );
        WebElement spaceElement = getDriver().findElement(By.xpath(spaceXPath));

        if (spaceElement.getAttribute("open") == null) {
            WebElement summary = spaceElement.findElement(By.xpath("./summary"));
            summary.click();
            getDriver().waitUntilElementIsVisible(By.cssSelector("li.cfm-doc-tree-leaf a"));
        }

        String pageXPath = String.format(
            ".//li[contains(@class,'cfm-doc-tree-leaf')]//a[normalize-space(text())='%s']",
            pageName
        );
        WebElement pageLink = spaceElement.findElement(By.xpath(pageXPath));

        pageLink.click();

        return new ViewPage();
    }

    public Set<String> getImportedMacros()
    {
        Set<String> r = new HashSet<>();
        List<WebElement> macroElements = getDriver().findElements(By.cssSelector(".imported-macros-list"));
        for (WebElement macroElement : macroElements) {
            for (String macro : StringUtils.split(macroElement.getText(), ',')) {
                r.add(StringUtils.split(macro.trim(), ' ')[0]);
            }
        }

        return r;
    }
}
