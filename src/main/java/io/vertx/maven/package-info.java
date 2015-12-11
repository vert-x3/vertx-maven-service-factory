/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

/**
 * = Maven verticle factory
 *
 * The Maven verticle factory is an implementation of verticle factory loading a service dynamically from a Maven
 * repository at run-time. It's useful if you don't want to package all your service dependencies at build-time into
 * your application, but would rather install and deploy them dynamically at run-time when they are requested.
 *
 * ## Usage
 *
 * Vert.x picks up `VerticleFactory` implementations from the classpath, so you just need to make sure the
 * ${maven.artifactId} jar file on the classpath. First you need to add the Maven verticle factory to your application's
 * classpath. If you are using a fat jar, you can use the following dependencies:
 *
 * * Maven (in your `pom.xml`):
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <dependency>
 *   <groupId>${maven.groupId}</groupId>
 *   <artifactId>${maven.artifactId}</artifactId>
 *   <version>${maven.version}</version>
 * </dependency>
 * ----
 *
 * * Gradle (in your `build.gradle` file):
 *
 * [source,groovy,subs="+attributes"]
 * ----
 * compile '${maven.groupId}:${maven.artifactId}:${maven.version}'
 * ----
 *
 * This `VerticleFactory` uses the prefix `maven:` to select it when deploying services.
 * The service identifier is made up of the Maven co-ordinates of the artifact that contains the service, e.g.
 * `com.mycompany:main-services:1.2` followed by a double colon `::` followed by the service name. The service name is
 * used to find the service descriptor file inside the artifact which is named by the service name with a `.json`
 * extension. This is explained in the the
 * http://vertx.io/docs/vertx-service-factory/(vert.x service factory documentation).
 *
 * For example, to deploy a service that exists in Maven artifact `com.mycompany:my-artifact:1.2` called `my-service` you
 * would use the string `maven:com.mycompany:main-services:1.2::my-service`. Given this string, the verticle factory
 *
 * 1. uses the Aether client try and locate the artifact `com.mycompany:my-artifact:1.2` and all its dependencies in
 * the configured Maven repositories and download and install it locally if it's not already installed.
 * 2. constructs a classpath including all those artifacts and creates a classloader with that classpath
 * 3. service using the http://vertx.io/docs/vertx-service-factory/(vert.x service factory).
 *
 * Note that if the current Vert.x classpath contains already an artifact, then this dependency will not be
 * overridden and will be effectively used. Vert.x does not attempt to make some class loading magic, conflicts
 * should be resolved by modifying the Vert.x classpath to not contain this dependency.
 *
 * The Service Verticle Factory will look for a descriptor file called `my-service.json` (in the previous example) on
 * the constructed classpath to actually load the service.
 *
 * Given a service identifier the service can be deployed programmatically e.g.:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example1(io.vertx.core.Vertx, io.vertx.core.DeploymentOptions)}
 * ----
 *
 * Or can be deployed on the command line with:
 *
 * ---
 * vertx run maven:com.mycompany:my-artifact:1.2::my-service
 * ---
 *
 * The service name (`my-service`) can be omitted when the _META-INF/MANIFEST_ a the jar containing the
 * service contains a `Main-Verticle` entry that declares the verticle to run:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example2(io.vertx.core.Vertx, io.vertx.core.DeploymentOptions)}
 * ----
 *
 * And the manifest contains:
 *
 * ----
 * Main-Verticle: service:my.service
 * ----
 *
 *
 * ## Declaring the Verticle Factory programmatically
 *
 * You can also declare the verticle factory programmatically:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example3(io.vertx.core.Vertx)}
 * ----
 *
 * ## Configuring repositories
 *
 * The Maven local repository location can be configured using the `vertx.maven.localRepo` system property - this should
 * point to the local repository directory on disc. The default value is: `{user.home}/.m2/repository`
 *
 * The list of remote repositories can be configured using the `vertx.maven.remoteRepos` system property - this should
 * contain a **space** separated list of urls to the remote repositories. The default value is
 * `https://repo.maven.apache.org/maven2/ https://oss.sonatype.org/content/repositories/snapshots/`
 *
 * ### Programmatically
 *
 * You can also configure the repositories programmatically by passing a {@link io.vertx.maven.ResolverOptions}
 * object to the constructor of the {@link io.vertx.maven.MavenVerticleFactory}:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example4(io.vertx.core.Vertx, java.lang.String, java.util.List)}
 * ----
 *
 * ## Remote repository access through authenticated https
 *
 * You can specify https URLs for remote repositories, the client will uses the JSSE system properties
 * configuration. It can be achieved via JVM system properties, you can read more at
 * http://maven.apache.org/guides/mini/guide-repository-ssl.html.
 *
 * ### Using system properties
 *
 * It can configured when running the JVM: for instance:
 *
 * ----
 * java -jar my-fat-jar -Djavax.net.ssl.trustStore=/my_trust_store.jks -Djavax.net.ssl.trustStorePassword=somepassword
 * ----
 *
 * ### Programmatically
 *
 * To configure the HTTPS property, use:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example5()}
 * ----
 *
 * Note that programmatic configuration must be done before using the underlying Aether client (so before
 * instantiating the {@link io.vertx.maven.MavenVerticleFactory}.
 *
 * ## Remote repository access through proxies
 *
 * ### Using system properties
 *
 * Repositories can be accessed using an http proxy using the `vertx.maven.httpProxy` and `vertx.maven.httpsProxy`.
 * The former configures a proxy for remote http repositories and the later configures a proxy for remote http repositories.
 *
 * ### Programmatically
 *
 * You can also configure the repositories programmatically using the
 * {@link io.vertx.maven.ResolverOptions#setHttpProxy(java.lang.String)} and
 * {@link io.vertx.maven.ResolverOptions#setHttpsProxy(java.lang.String)}:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example6(io.vertx.core.Vertx, java.lang.String)}
 * ----
 *
 * ## Configuring authentication
 *
 * Basic authentication can be achieved by adding a username and/or password in the repository or proxy configuration.
 * For instance `http://julien:secret@myrepository.com/` will configure to use `julien` username and `secret`
 * password if the remote server needs authentication. Proxies are also supported.
 *
 * ## Configuring Remote Snapshot Refresh Policy
 *
 * By default _SNAPSHOT_ dependencies are updated once a day. This behavior can be modified using the system property
 * `vertx.maven.remoteSnapshotPolicy`. This can be set to `always` to ensure _SNAPSHOT_ dependencies are updated every
 * time, `daily` to update just once a day, which is the default, or to `never` to ensure they are never updated.
 *
 * It can also be set to `interval:X` where `X` is the number of minutes to allow before updating a _SNAPSHOT_
 * dependency.
 *
 * The refresh policy can also be configured from the {@link io.vertx.maven.ResolverOptions}:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example7(io.vertx.core.Vertx)}
 * ----
 *
 * ## Customizing the resolver
 *
 * You can create an instance of
 * {@link io.vertx.maven.MavenVerticleFactory} using your own {@link io.vertx.maven.Resolver}:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example8(io.vertx.core.Vertx, io.vertx.maven.Resolver)}
 * ----
 */
@Document(fileName = "index.ad")
package io.vertx.maven;

import io.vertx.docgen.Document;