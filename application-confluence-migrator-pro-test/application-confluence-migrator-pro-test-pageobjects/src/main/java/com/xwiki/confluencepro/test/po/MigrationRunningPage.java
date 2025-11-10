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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Represents actions that can be done on the running view of a migration page.
 *
 * @version $Id$
 * @since 1.0
 */
public class MigrationRunningPage extends ViewPage
{
    public MigrationRunningPage() {
        getDriver().waitUntilElementIsVisible(By.cssSelector(".available-spaces .available-space:nth-child(2) input"));
    }

    public static MigrationRunningPage goToPage(String migrationName)
    {
        DocumentReference reference =
            new DocumentReference("xwiki", Arrays.asList("ConfluenceMigratorPro", "Migrations"), migrationName);
        getUtil().gotoPage(reference);
        return new MigrationRunningPage();
    }

    public List<QuestionSpace> getSelectableSpaces()
    {
        List<QuestionSpace> spaces = new ArrayList<>();
        List<WebElement> spaceWebElements = getDriver().findElements(By.className("available-space"));
        for (int i = 1; i < spaceWebElements.size(); i++) {
            spaces.add(new QuestionSpace(i + 1));
        }
        return spaces;
    }

    public QuestionSpace getSelectableSpace(int index) {
        return getSelectableSpaces().get(index);
    }

    public void selectSpace(int index) {
        getSelectableSpaces().get(index).getCheckbox().click();
    }

    public MigrationRaportView confirmSpacesToMigrate() {
        getDriver().findElement(By.className("btn-primary")).click();
        getDriver().waitUntilElementIsVisible(By.id("cfm-doc-tree"));
        return new MigrationRaportView();
    }

    public int getNumberOfDocuments(int index)
    {
        List<WebElement> spaces = getDriver().findElements(By.className("available-space"));
        WebElement space = spaces.get(index);

        WebElement properties = space.findElement(By.className("cfmProperties"));
        String documentsText = properties.findElement(By.xpath("./span[1]")).getText();

        return Integer.parseInt(documentsText.split(":")[1].trim());
    }

    public int getSpaceIndexByName(String spaceName)
    {
        List<WebElement> spaces = getDriver().findElements(By.className("available-space"));

        for (int i = 0; i < spaces.size(); i++) {
            WebElement label = spaces.get(i).findElement(By.tagName("label"));
            String labelText = label.getText();
            if (labelText.startsWith(spaceName))
                return i;

        }
        return -1;
    }

    public void selectSpaceByLabel(String spaceLabel)
    {
        List<WebElement> spaces = getDriver().findElements(By.className("available-space"));

        for (WebElement space : spaces) {
            WebElement labelElement = space.findElement(By.tagName("label"));
            String labelText = labelElement.getText();

            if (labelText.equals(spaceLabel)) {
                space.findElement(By.cssSelector("input[type='checkbox']")).click();
                return;
            }
        }
    }
}
