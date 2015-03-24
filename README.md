# Maven verticle factory

This `VerticleFactory` implementation behaves very similarly to the [`ServiceVerticleFactory`](https://github.com/vert-x3/vertx-service-factory) implementation.

The main difference is that this implementation looks in configured Maven repositories to find the artifact
corresponding to the service identifier.

Please see the [`ServiceVerticleFactory`](https://github.com/vert-x3/vertx-service-factory) for documentation on
 services.

## Usage

This `VerticleFactory` is used in the same way as the `ServiceVerticleFactory`, using the same prefix `service:`

One difference is that the version number in the service identifier is mandatory as this is needed to resolve the artifact from
Maven properly, e.g.:

The verticle can be deployed programmatically e.g.:
    
    vertx.deployVerticle("service:com.mycompany:clever-db-service:1.0", ...)
        
Or can be deployed on the command line with:
  
    vertx run service:com.mycompany:clever-db-service:1.0

When deploying a service in this way, the Vert.x service factory will use the Aether Maven client to install the Maven
artifact and its dependencies in your local Maven repository (if they're not already installed).

It then constructs a classpath including all those artifacts and creates a classloader with that classpath in order to
load the service.

It will look for a service descriptor file on that classpath using the normal service descriptor naming scheme.

So, to summarise. If you invoked:

    vertx.deployVerticle("service:com.mycompany:clever-db-service:1.0", ...)

It would install the Maven artifact `com.mycompany:clever-db-service:1.0` in your local Maven repository and look
for a service descriptor with name `com.mycompany.clever-db.json`.

### More than one service in an artifact

If you have more than one service in a single Maven artifact, then you can specify which one you want to deploy by adding
a qualifier after the coordinates.

The qualifier is appeneded to the coordinates after two colons: `::`.

For example if the artifact coordinates are `com.mycompany:clever-db-service:1.0`, and that artifact contains two services:

One with a JSON descriptor called `com.mycompany.serviceA.json` and another with `com.mycompany.serviceB.json`, then to
deploy service A you can specify the identifier as `service:com.mycompany:clever-db-service:1.0::serviceA`, e.g.

    vertx.deployVerticle("ervice:com.mycompany:clever-db-service:1.0::serviceA", ...)


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

## Remote repository access through authenticated https

You can specify https URLs for remote repositories, the client will uses the JSSE system properties
configuration. It can be achieved via JVM system properties, you can read more at http://maven.apache.org/guides/mini/guide-repository-ssl.html.

### Using system properties

It can configured when running the JVM: for instance `-Djavax.net.ssl.trustStore=/my_trust_store.jks -Djavax.net.ssl.trustStorePassword=somepassword`

### Programmatically

```
System.setProperty("javax.net.ssl.trustStore", "/my_trust_store.jks");
System.setProperty("javax.net.ssl.trustStorePassword", "somepassword");
```

Note that programmatic configuration must be done before using the underlying Aether client.

## Remote repository access through proxies

### Using system properties

Repositories can be accessed using an http proxy using the `vertx.maven.httpProxy` and `vertx.maven.httpsProxy`.
The former configures a proxy for remote http repositories and the later configures a proxy for remote http
 repositories.

### Programmatically

You can also configure the repositories programmatically using the `setHttpProxy(String httpProxy)` and
`setHttpsProxy(String httpsProxy)` methods on the factory.

## Configuring authentication

Basic authentication can be achieved by adding a username and/or password in the repository or proxy configuration.
For instance `http://julien:secret@myrepository.com/` will configure to use `julien` username and `secret` password if the
remote server needs authentication. Proxies are also supported.
