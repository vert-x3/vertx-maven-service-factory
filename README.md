# Maven verticle factory

This `VerticleFactory` implementation behaves very similarly to the [`ServiceVerticleFactory`](https://github.com/vert-x3/vertx-service-factory) implementation.

The main difference is that this implementation looks in configured Maven repositories to find the artifact
corresponding to the service identifier.

Please see the [`ServiceVerticleFactory`](https://github.com/vert-x3/vertx-service-factory) for documentation on
 services.

## Usage

This `VerticleFactory` is used in the same way as the `ServiceVerticleFactory`, using the same prefix `service:`

## Making it available    
    
Vert.x picks up `VerticleFactory` implementations from the classpath, so you just need to make sure the`ServiceVerticleFactory`
 jar is on the classpath.
    
It will already be on the classpath if you are running `vertx` on the command using the full distribution.

If you are running embedded you can declare a Maven dependency to it in your pom.xml (or Gradle config):

    <dependency>
      <groupId>io.vertx</groupId>
      <artifactId>vertx-maven-modules</artifactId>
      <version>3.0.0</version>
    </dependency>
    
You can also register `VerticleFactory` instances programmatically on your `Vertx` instance using the `registerVerticleFactory`
method.

## Configuring repositories

### Using system properties

The Maven local repository location can be configured using the `vertx.maven.localRepo` system property - this should
point to the local repository directory on disc.

The default value is: `{user.home}/.m2/repository`

The list of remote repositories can be configured using the `vertx.maven.remoteRepos` system property - this should
contain a space separated list of urls to the remote repositories.

The default valus is `http://central.maven.org/maven2/ http://oss.sonatype.org/content/repositories/snapshots/`

### Programmatically

You can also configure the repositories programmatically using the `setLocalMavenRepo(String localMavenRepo)` and 
`setRemoteMavenRepos(List<String> remoteMavenRepos)` methods on the factory.

## TODO

* Configuring auth
