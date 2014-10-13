# Maven verticle factory

This verticle can be used to resolve and deploy Vert.x verticles from Maven repositories at *run-time*

## Usage

When deploying the verticle use the prefix `maven`.

The verticle name should be the Maven coordinates e.g. `com.mycompany:myartifact:v1.0`

The verticle can be deployed programmatically like this:

    vertx.deployVerticle("maven:com.mycompany:myartifact:v1.0", ...)

Or can be deployed on the command line with:

    vertx run maven:com.mycompany:myartifact:v1.0

## Meta-data

The artifact being deployed (e.g. `com.mycompany:myartifact:v1.0`) must be a jar artifact which contains the `Main-Class`
field in the manifest (file: `META-INF/MANIFEST.MF`)

E.g.

    Main-Class: com.mycompany.MyVerticle

## Configuring repositories

The Maven local repository location can be configured using the `vertx.maven.localRepo` system property - this should
point to the local repository directory on disc.

The default value is: `{user.home}/.m2/repository`

The list of remote repositories can be configured using the `vertx.maven.remoteRepos` system property - this should
contain a space separated list of urls to the remote repositories.

The default valus is `http://central.maven.org/maven2/ http://oss.sonatype.org/content/repositories/snapshots/`

## TODO

* Configuring auth
* Support other kind of repositories
