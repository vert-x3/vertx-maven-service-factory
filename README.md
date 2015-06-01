# Maven verticle factory

[![Build Status](https://vertx.ci.cloudbees.com/buildStatus/icon?job=vert.x3-maven-service-factory)](https://vertx.ci.cloudbees.com/view/vert.x-3/job/vert.x3-maven-service-factory/)

This `VerticleFactory` implementation loads a service dynamically from a Maven repository at run-time.

It's useful if you don't want to package all your service dependencies at build-time into your application, but would
rather install and deploy them dynamically at run-time when they are requested.

## Usage

This `VerticleFactory` uses the prefix `maven:` to select it when deploying services.

The service identifier is made up of the Maven co-ordinates of the artifact that contains the
service, e.g. `com.mycompany:main-services:1.2` followed by a double colon `::` followed by the service name.

The service name is used to find the service descriptor file inside the artifact which is named by the service name with
a `.json` extension. This is explained in the link:https://github.com/vert-x3/vertx-service-factory[Service Verticle Factory]
documentation.

For example, to deploy a service that exists in Maven artifact `com.mycompany:main-services:1.2` called `my-service` you
would use the string `maven:com.mycompany:main-services:1.2::my-service`.

Given this string, the verticle factory will use the Aether client try and locate the artifact `com.mycompany:main-services:1.2`
and all its dependencies in the configured Maven repositories and download and install it locally if it's not already installed.

It then constructs a classpath including all those artifacts and creates a classloader with that classpath in order to
load the service using the standard link:https://github.com/vert-x3/vertx-service-factory[Service Verticle Factory].

The Service Verticle Factory will look for a descriptor file called `my-service.json` on the constructed classpath to
actually load the service.

Given a service identifier the service can be deployed programmatically e.g.:
    
    vertx.deployVerticle("maven:com.mycompany:main-services:1.2::my-service", ...)
        
Or can be deployed on the command line with:
  
    vertx run maven:com.mycompany:main-services:1.2::my-service

The service name can be omitted when the service jar _META-INF/MANIFEST_ contains a `Main-Verticle` entry that
declares the verticle to run

    vertx.deployVerticle("maven:com.mycompany:main-services:1.2", ...)

And the manifest contains:

    Main-Verticle: service:my.service

## Making it available
    
Vert.x picks up `VerticleFactory` implementations from the classpath, so you just need to make sure the`ServiceVerticleFactory`
 jar is on the classpath.
    
It will already be on the classpath if you are running `vertx` on the command using the full distribution.

If you are running embedded you can declare a Maven dependency to it in your pom.xml (or Gradle config):

    <dependency>
      <groupId>io.vertx</groupId>
      <artifactId>vertx-maven-service-factory</artifactId>
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
