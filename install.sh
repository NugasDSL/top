#!/usr/bin/env bash
mvn install:install-file -Dfile=../toy/lib/toy-core-1.0.jar -DgroupId=toy -DartifactId=toy-core -Dversion=1.0 -Dpackaging=jar
mvn package