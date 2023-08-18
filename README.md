## Task Manager Application

XWiki Confluence Migrator Pro helps admins to import confluence packages into their XWiki instance.

* Project Lead: [Teo Caras](https://github.com/trrenty)
* [Documentation](https://store.xwiki.com/xwiki/bin/view/Extension/ConfluenceMigratorPro)
* Communication: [Forum and mailing list](http://dev.xwiki.org/xwiki/bin/view/Community/MailingLists), [chat](http://dev.xwiki.org/xwiki/bin/view/Community/IRC)
* [Development Practices](http://dev.xwiki.org)
* License: LGPL 2.1+
* Minimal XWiki version supported: XWiki 13.10
* Translations: N/A
* Sonar Dashboard: N/A
* Continuous Integration Status: [![Build Status](http://ci.xwikisas.
  com/view/All/job/xwikisas/job/application-task/job/master/badge/icon)](http://ci.xwikisas.com/view/All/job/xwikisas/job/application-confluence-migrator-ui/job/master/)

# Release

```
mvn release:prepare -Pintegration-tests -DskipTests -Darguments="-N"
mvn release:perform -Pintegration-tests -DskipTests -Darguments="-DskipTests"
```
