#!/bin/sh
exec javah -o ../native/java-interface.h -classpath target/*.jar org.jvnet.winp.Native
