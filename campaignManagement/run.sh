#!/bin/bash

mvn clean install -DskipTests assembly:single -q
java -jar target/lld.jar sample_input/input1.txt