WinP Developer info
---

## Building

In order to build and test the project, run `mvn clean verify`.
In order to build the project, you need MSBuild 15.0 and Microsoft Visual Studio 2019 and BuildTools v142.
Toolchains for x86 and x64 targets should be installed.
Furthermore, Windows SDK 10.0.16299 should be installed too.

The `JAVA_HOME` environment variable should point to your Java installation (>= 11).

## Testing

* Right now all tests are implemented in Java part of the library (JUnit Framework).
There is no fully native tests.
* All tests are being automatically invoked by `mvn clean verify`.
* Tests run from Maven, and the selected 32/64-bit mode depends on the Java version,
which can be passed to maven using the `JAVA_HOME` environment variable.

Note that WinP behavior may differ depending on the Windows version, permissions, run mode (desktop/service), etc.
Ideally, tests should be executed on all target platforms.

## Continuous Integration

Project has a continuous integration flow being hosted by official Jenkins CI ([project page](https://ci.jenkins.io/job/jenkinsci-libraries/job/winp)).
This CI instance automates testing of Debug and Release configurations,
but it does not provide full coverage of possible system configurations.

## Debugging

### Debugging Java part

Java part of the library can be debugged independently or within a project using standard tools available in the Java Development Kit.

### Debugging Native part

In order to debug native parts of the library attach your debugger (e.g. from Microsoft Visual Studio UI) to the `java.exe` process.
Then you can load debug symbols provided within native build directories (or in AppVeyor).
Symbols are available for both Release and Debug configurations, but debug configuration provides more information.

When debugging code in Microsoft Visual Studio, make sure that the selected `Configuration` and `Platform` in UI are similar to the built version and to the Java version (32/64 bit).

