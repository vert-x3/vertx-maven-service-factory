# Maven verticle factory

[![Build Status](https://travis-ci.org/vert-x3/vertx-maven-service-factory.svg?branch=master)](https://travis-ci.org/vert-x3/vertx-maven-service-factory)

This is a `VerticleFactory` implementation which deploys a verticle given a service name and load it from a Maven
repository.

The documentation is available [here](src/main/asciidoc/java/index.adoc), or on the the
[vert.x web site](http://vertx.io/docs/vertx-maven-service-factory/java).

## Build instructions

First you need to build the project from Maven:

`mvn clean package`

Once done, you can run the tests from your IDE.

Notice that the tests are using "projects" (actually, artifacts generated by theses projects) built during the Maven
build.
