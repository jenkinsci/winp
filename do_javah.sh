#!/bin/sh
exec "${JAVA_HOME}/bin/javah" -o native/java-interface.h -classpath target/*.jar org.jvnet.winp.Native
