WinP Developer info
---

## Building

In order to build and test the project, use the normal maven flow. 
In order to build the project, you need MSbuild 15.0 and Microsoft Visual Studio 2019/2022 with BuildTools v142.

* `mvn clean package` - Build and test the release version of the JAR file.
Code will not be signed automatically.
* `mvn clean package -Dnative.configuration=Debug` - Build and test the Debug configuration of the library. 
This version simplifies debugging of the native part of the library (see below).

## Testing

* Right now all tests are implemented in Java part of the library (JUnit Framework).
There is no fully native tests.
* Tests run from Maven, and the selected 32/64-bit mode depends on the Java version, 
which can be passed to maven using the `JAVA_HOME` environment variable.
* Generally you need to run `mvn clean package -Dnative.configuration=Debug` and `mvn clean package -Dnative.configuration=Release` on up to 4 configurations
  * x86 Windows, x86 Java
  * X64 Windows, X64 Java
  * X64 Windows, X86 Java (WoW64 mode)
  * If you have access, ARM64 Windows, ARM64 Java

Note that WinP behavior may differ depending on the Windows version, permissions, run mode (desktop/service), etc.
Ideally, tests should be executed on all target platforms.

## Debugging

### Debugging Java part

Java part of the library can be debugged independently or within a project using standard tools available in the Java Development Kit.
 
### Debugging Native part

In order to debug native parts of the library attach your debugger (e.g. from Microsoft Visual Studio UI) to the `java.exe` process.
Then you can load debug symbols provided within native build directories.
Symbols are available for both Release and Debug configurations, but debug configuration provides more information.

When debugging code in Microsoft Visual Studio, make sure that the selected `Configuration` and `Platform` in UI are similar to the built version and to the Java version (32/64 bit).

