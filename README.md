vertx-maven-modules
========

Proof of concept of a verticle factory deploying Vert.x module from a Maven repository:

~~~~
Vertx vertx = Vertx.vertx();

// We assume the artifact contains a smod.json file.
vertx.deployVerticle("maven:my:module:1.0");
~~~~

