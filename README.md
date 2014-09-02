vertx-repo-modules
========

Proof of concept of a verticle factory deploying Vert.x module from repositories:

~~~~
Vertx vertx = Vertx.vertx();

// We assume the artifact contains a smod.json file.
vertx.deployVerticle("maven:my:module:1.0");
~~~~

## Todo

* Support other kind of repositories
* Maven : use straight Aether API instead of Shrinkwrap wrapper
