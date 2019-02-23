WinP Developer info
---

## Building

In order to build and test the project, use the `build.cmd` script available in the repository. 
In order to build the project, you need MSbuild 15.0 and Microsoft Visual Studio 2017 with Windows XP support and BuildTools v140.

* `build.cmd cleanbuild` - Build and test the release version of the JAR file.
Code will not be signed automatically.
* `build.cmd cleanbuild Debug` - Build and test the Debug configuration of the library. 
This version simplifies debugging of the native part of the library (see below).

## Testing

* Right now all tests are implemented in Java part of the library (JUnit Framework).
There is no fully native tests.
* All tests are being automatically invoked by `build.cmd`
* Tests run from Maven, and the selected 32/64-bit mode depends on the Java version, 
which can be passed to maven using the `JAVA_HOME` environment variable.
* Generally you need to run `build.cmd cleanbuild Debug` and `build.cmd cleanbuild Release` on 3 configurations
  * 32-bit Windows, 32-bit Java
  * 64-bit Windows, 64-bit Java
  * 64-bit Windows, 32-bit Java (WoW64 mode)

Note that WinP behavior may differ depending on the Windows version, permissions, run mode (desktop/service), etc.
Ideally, tests should be executed on all target platforms.

## Continuous Integration

Project has a continuous integration flow being hosted by AppVeyor ([project page](https://ci.appveyor.com/project/oleg-nenashev/winp)).
This CI instance automates testing of Debug and Release configurations, 
but it does not provide full coverage of possible system configurations.
See [the appveyor.yml file](./appveyor.yml) for more details.

## Debugging

### Debugging Java part

Java part of the library can be debugged independently or within a project using standard tools available in the Java Development Kit.
 
### Debugging Native part

In order to debug native parts of the library attach your debugger (e.g. from Microsoft Visual Studio UI) to the `java.exe` process.
Then you can load debug symbols provided within native build directories (or in AppVeyor).
Symbols are available for both Release and Debug configurations, but debug configuration provides more information.

When debugging code in Microsoft Visual Studio, make sure that the selected `Configuration` and `Platform` in UI are similar to the built version and to the Java version (32/64 bit).

