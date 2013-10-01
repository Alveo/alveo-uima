#!/bin/sh
export CP=$(mvn compile | grep "runtime_classpath:" | perl -p -e 's/^\s*\[echo\] runtime_classpath://') 
echo $CP
