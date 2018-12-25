#!/usr/bin/env bash

cd $(dirname "$0")
find . -type f -name 'currentView' -delete
java -jar top-server-main.jar ${1} ${2}

