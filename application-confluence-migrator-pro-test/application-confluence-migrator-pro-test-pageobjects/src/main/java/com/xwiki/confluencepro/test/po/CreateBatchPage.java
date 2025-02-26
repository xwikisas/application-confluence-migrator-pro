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

import java.io.File;
import java.sql.SQLOutput;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.ViewPage;


/**
 * Represents actions that can be done on the create batch page.
 *
 * @version $Id$
 * @since 1.31.1
 */
public class CreateBatchPage extends ViewPage
{
    public CreateBatchPage()
    {
    }

    public CreateBatchPage completePath(String testResourcePath){
        WebElement element = getDriver().findElement(By.cssSelector("#path"));
        element.sendKeys(getBatchFolderPath(testResourcePath));
        return this;
    }

    public CreateBatchPage refreshPage()
    {
        WebElement element = getDriver().findElement(By.cssSelector("#refreshButton"));
        element.click();
        return this;
    }

    public CreateBatchPage completeName(String batchName)
    {
        WebElement element = getDriver().findElement(By.cssSelector("#batchName"));
        element.sendKeys(batchName);
        return  this;
    }

    public CreateBatchPage selectAll()
    {
        getDriver().findElement(By.cssSelector("#selectAll")).click();
        return this;
    }


    public CreateBatchPage selectNone()
    {
        getDriver().findElement(By.cssSelector("#selectNone")).click();
        return this;
    }

    public CreateBatchPage inverseSelection()
    {
        getDriver().findElement(By.cssSelector("#inverseSelect")).click();
        return this;
    }

    public int countSelectedPackages(){
        return getDriver().findElements(By.cssSelector(".confluence-export-checkbox:checked")).size();
    }

    public CreateBatchPage createBatch(){
        getDriver().findElement(By.cssSelector(".createBatch")).click();
        return this;
    }

    private String getBatchFolderPath(String testResourcePath){
        return new File("src/test/resources/ConfluenceMigratorIT").getAbsolutePath();
    }
}
