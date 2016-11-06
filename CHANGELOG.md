Changelog
====

##### 1.24

Release date: Nov 2, 2016

* [Issue #22](https://github.com/kohsuke/winp/issues/22) - 
Winp sometimes kills random processes when using killRecursive.
([PR #23](https://github.com/kohsuke/winp/pull/23))
* [WINP-10](https://java.net/jira/browse/WINP-10) - 
Fix for `getCmdLineAndEnvVars()` which fails on x64 versions of Windows.
([PR #20](https://github.com/kohsuke/winp/pull/20))
* [Issue #24](https://github.com/kohsuke/winp/issues/24) - 
Wrong folder when using the `winp.folder.preferred` system property (parent instead of the actual folder).
([PR #25](https://github.com/kohsuke/winp/pull/25))
* [Issue #26](https://github.com/kohsuke/winp/issues/26), [JENKINS-20913](https://issues.jenkins-ci.org/browse/JENKINS-20913) - 
Native class now tries loading DLLs via the temp location.
([PR #27](https://github.com/kohsuke/winp/pull/27))

##### 1.23

Release date: Fev 16, 2015

* Migrate native components to Visual Studio Community 2013
([PR #14](https://github.com/kohsuke/winp/pull/14))
* Provide a `winp.unpack.dll.to.parent.dir`, which disables DLL unpacking to the parent dir
([PR #14](https://github.com/kohsuke/winp/pull/12))

##### Previous release

Previous releases have no standalone changelogs.
