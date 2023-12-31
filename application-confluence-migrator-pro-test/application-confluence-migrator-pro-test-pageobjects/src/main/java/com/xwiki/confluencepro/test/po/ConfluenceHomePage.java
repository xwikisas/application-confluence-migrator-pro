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

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Represents actions that can be done on the home page of the app.
 *
 * @version $Id$
 * @since 1.0
 */
public class ConfluenceHomePage extends ViewPage
{

    public static ConfluenceHomePage goToPage() {
        DocumentReference reference = new DocumentReference("xwiki", "ConfluenceMigratorPro", "WebHome");
        getUtil().gotoPage(reference);
        return new ConfluenceHomePage();
    }
    public ConfluenceHomePage() {
        getMigrationsLiveTable();
        getPackageLiveTable();
    }
    public LiveDataElement getPackageLiveTable() {
        return new LiveDataElement("confluencePackages");
    }

    public LiveDataElement getMigrationsLiveTable() {
        return new LiveDataElement("confluenceMigrations");
    }

    public MigrationCreationPage selectPackage(int number)
    {
        getPackageLiveTable().getTableLayout().findElementInRow(number, By.className("startMigration")).click();
        return new MigrationCreationPage();
    }

    private File getFileToUpload(String testResourcePath, String filename)
    {
        return new File(testResourcePath, "ConfluenceMigratorIT/" + filename);
    }

    public void attachFile(String testResourcePath, String file)
    {

        WebElement input = getDriver().findElement(By.id("confluenceUploadFile"));
        input.clear();
        input.sendKeys(getFileToUpload(testResourcePath, file).getAbsolutePath());
        this.waitForNotificationSuccessMessage("Attachment uploaded: " + file);
        getDriver().navigate().refresh();
    }
}
