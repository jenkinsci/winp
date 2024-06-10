# Windows process management library (WinP)

[![Build status](https://ci.jenkins.io/job/jenkinsci-libraries/job/winp/job/master/badge/icon)](https://ci.jenkins.io/job/jenkinsci-libraries/job/winp/job/master/)

This project develops a library that lets you control Windows processes better, beyond what's available in JDK.

Features summary:

* Windows machine management (logoff user, shutdown/restart machine)
* Thread priority management
* Process termination (including the recursive termination)
* Process info acquisition
* etc.

See [the library's Javadoc](https://javadoc.jenkins.io/component/winp/) for more details.

## Maven repository

Starting with 1.29, this library is published at:

```xml
<repository>
  <id>repo.jenkins-ci.org</id>
  <url>https://repo.jenkins-ci.org/releases/</url>
</repository>
```

## Java support

Starting with 1.29, this library requires Java 11 or newer.

WinP Library includes native libraries for all supported platforms, hence it can run on both 32bit and 64bit Java versions without any additional configuration.

## Platform support

The library supports _x86_, _amd64_ and _arm64_ architectures.

:exclamation: It is **not recommended** to use WinP with _32bit_ Java on a _64bit_ operating system.
In such case the library will be running in the WoW64 mode;
and it will be unable to properly work with 64bit processes in the system.
E.g. any process information query call (e.g. Environment variables retrieval) may fail if you run it against 64bit process.

## Supported Windows versions

The current version of WinP is known to work correctly on the following Windows versions:

* Windows 10 and above
* Windows Server 2019 and above

Other Windows product lines are not being actively tested though WinP may work there.

## License

[MIT License][license]

[license]: http://www.opensource.org/licenses/mit-license.php
