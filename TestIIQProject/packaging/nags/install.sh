#!/bin/sh

BASEDIR=`dirname "$0"`

for installFile in "$BASEDIR"/*.jar; do
    java -jar "$installFile"
done
