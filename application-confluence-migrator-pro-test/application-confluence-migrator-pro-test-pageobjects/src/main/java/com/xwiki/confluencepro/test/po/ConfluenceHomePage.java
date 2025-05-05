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
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
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
    public ConfluenceHomePage()
    {
    }

    public static ConfluenceHomePage goToPage()
    {
        DocumentReference reference = new DocumentReference("xwiki", "ConfluenceMigratorPro", "WebHome");
        getUtil().gotoPage(reference);
        return new ConfluenceHomePage();
    }

    public LiveDataElement getPackageLiveTable()
    {
        return new LiveDataElement("confluencePackages");
    }

    public LiveDataElement getMigrationsLiveTable()
    {
        return new LiveDataElement("confluenceMigrations");
    }

    /**
     *
     * @param migrationIndex the index of the migration in the livedata.
     * @return the status of the migration
     * @since 1.34.3
     */
    public String migrationStatus(int migrationIndex)
    {
        List<WebElement> statuses = getDriver().findElements(
            By.cssSelector("#confluenceMigrations tbody " + "td[data-title=\"Migration status\"]"));
        return statuses.get(migrationIndex).getText().trim();
    }

    public MigrationRunningPage getMigrationRunningPage(int migrationIndex)
    {
        List<WebElement> statuses =
            getDriver().findElements(By.cssSelector("#confluenceMigrations tbody " + "td[data-title=\"Migration\"] a"));
        statuses.get(migrationIndex).click();
        return new MigrationRunningPage();
    }

    public MigrationCreationPage selectPackage(int number)
    {
        getPackageLiveTable().getTableLayout().findElementInRow(number, By.className("startMigration")).click();
        return new MigrationCreationPage();
    }

    public void openHowToMigrateSubsection(String subsectionClass)
    {
        getDriver().setDriverImplicitWait();
        WebElement subsections = getDriver().findElement(By.cssSelector(subsectionClass + " .cfmTitleIcon"));
        subsections.click();
    }

    public void selectMigrationOptions(String selector, String value)
    {
        getDriver().findElement(By.cssSelector(String.format("%s input[value=%s]", selector, value))).click();
    }

    public void openSection(String contentContainer)
    {
        getDriver().findElement(By.cssSelector(String.format("li[data-content-container=%s]", contentContainer)))
            .click();
        loadingSection();
    }

    public boolean checkIfSectionWasLoaded(String sectionClass)
    {
        try {
            getDriver().findElement(By.cssSelector(sectionClass));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public CreateBatchPage createNewBatch()
    {
        getDriver().findElement(By.cssSelector("#createNewBatchButton")).click();
        return new CreateBatchPage();
    }

    public void attachFiles(String testResourcePath, List<String> files)
    {

        WebElement input = getDriver().findElement(By.id("confluenceUploadFile"));
        // Normally js will work in the frontend, but we can't send the keys via selenium.
        if (!input.isDisplayed()) {
            JavascriptExecutor js = getDriver();
            js.executeScript("arguments[0].classList.remove('hidden');", input);
        }
        for (String file : files) {
            input.clear();
            input.sendKeys(getFileToUpload(testResourcePath, file).getAbsolutePath());
            this.waitForNotificationSuccessMessage("Attachment uploaded: " + file);
        }
        getDriver().navigate().refresh();
    }

    public int countBatches()
    {
        return getDriver().findElements(By.cssSelector("#confluenceMigratorProBatches table tbody tr")).size();
    }

    private void loadingSection()
    {
        getDriver().waitUntilCondition(ExpectedConditions.not(
            ExpectedConditions.attributeContains(By.cssSelector(".confluence-pro-section-containers"), "class",
                "loading")));
    }

    private File getFileToUpload(String testResourcePath, String filename)
    {
        return new File(testResourcePath, "ConfluenceMigratorIT/" + filename);
    }
}
