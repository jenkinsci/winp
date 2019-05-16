Changelog
====

##### 1.28

Release date: Feb 28, 2019

* [#58](https://github.com/kohsuke/winp/issues/58) -
WinP was failing to send Ctrl+C on some platforms due to the missing DLL
* [#60](https://github.com/kohsuke/winp/pull/61) -
Prevent flickering console window from showing up during the process termination
* [#56](https://github.com/kohsuke/winp/issues/56) -
Cache and rethrow initialization errors in the native logic when Ctrl+C signal is invoked  

Internal:

* [PR #61](https://github.com/kohsuke/winp/pull/61) -
Ctrl+C sending logic is now implemented in Java instead of the native code

##### 1.27

Release date: Jul 18, 2018

Changes:

* [PR #49](https://github.com/kohsuke/winp/pull/49) -
Add `WinProcess#sendCtrlC()` method for sending graceful abort signals to processes.
* [PR #49](https://github.com/kohsuke/winp/pull/49) -
Add `WinProcess#isRunning()` method
* [PR #53](https://github.com/kohsuke/winp/pull/53/) - 
Fix handling of process IDs on 64-bit systems.
Before the change there was a risk of incorrect process ID retrieval due to the long => int conversion

Compatibility notes:

* Minimal required Windows version is now Windows XMP instead of Windows 2000 (`_WIN32_WINNT=0x0501`)

##### 1.26

Release date: Apr 18, 2018

* [Issue #46](https://github.com/kohsuke/winp/issues/46) - 
WinP was unable to locate JAR file by URL `zip:` prefix, e.g.
in Jenkins classloaders when running in Weblogic 
([JENKINS-48347](https://issues.jenkins-ci.org/browse/JENKINS-48347)).

##### 1.25

Release date: May 18, 2017

Enhancements:

* [PR #42](https://github.com/kohsuke/winp/pull/42) - 
Update minimal Java requirement to Java 1.6.
* [PR #39](https://github.com/kohsuke/winp/pull/39) -
Improve diagnostics of process reading errors in the Native code.
More info will be available in `WinpException`.
It helps to diagnose root causes of issues like [Issue #29](https://github.com/kohsuke/winp/issues/29).
* [PR #29](https://github.com/kohsuke/winp/issues/29) - 
Performance: Do not retrieve process Environment Variables when reading process command line. 
Also reduces impact of [Issue #29](https://github.com/kohsuke/winp/issues/29).

Fixed issues:

* [Issue #28](https://github.com/kohsuke/winp/issues/28) -
`WinProcess#isCritical()` should not require the `PROCESS_TERMINATE` permission.

Non-code changes:

* [PR #43](https://github.com/kohsuke/winp/pull/43) - 
Explicitly document supported platforms, operating systems and Java versions.
* Deploy Continuous Integration and Pull Request Builder on AppVeyor.
[Project page](https://ci.appveyor.com/project/oleg-nenashev/winp).
* [PR #36](https://github.com/kohsuke/winp/pull/36) - 
Allow building WinP JAR with Debug DLLs locally and on the CI infrastructure.

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
