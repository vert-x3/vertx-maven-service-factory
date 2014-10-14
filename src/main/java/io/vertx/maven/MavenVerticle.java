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

package io.vertx.maven;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Starter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MavenVerticle extends AbstractVerticle {

  public static final String LOCAL_REPO_SYS_PROP = "vertx.maven.localRepo";
  public static final String REMOTE_REPOS_SYS_PROP = "vertx.maven.remoteRepos";

  private static final String USER_HOME = System.getProperty("user.home");
  private static final String FILE_SEP = System.getProperty("file.separator");
  private static final String DEFAULT_MAVEN_LOCAL = USER_HOME + FILE_SEP + ".m2" + FILE_SEP + "repository";
  private static final String DEFAULT_MAVEN_REMOTES =
    "http://central.maven.org/maven2/ http://oss.sonatype.org/content/repositories/snapshots/";

  private static final Set<String> systemJars;

  static {
    systemJars = new HashSet<>();
    ClassLoader cl = MavenVerticle.class.getClassLoader();
    if (cl instanceof URLClassLoader) {
      URLClassLoader urlc = (URLClassLoader)cl;
      for (URL url: urlc.getURLs()) {
        try {
          File f = new File(url.toURI());
          String name = f.getName();
          if (name.endsWith(".jar")) {
            systemJars.add(name);
          }
        } catch (URISyntaxException ignore) {
        }
      }
    }
  }

  private final String verticleName;
  private final Vertx vertx;

  MavenVerticle(String verticleName, Vertx vertx) {
    this.verticleName = verticleName;
    this.vertx = vertx;
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    ResolveWorker worker = new ResolveWorker();
    vertx.deployVerticle(worker, new DeploymentOptions().setWorker(true), res -> {
      if (res.succeeded()) {
        vertx.deployVerticle(worker.main, worker.options, res2 -> {
          if (res2.succeeded()) {
            startFuture.complete();
          } else {
            startFuture.fail(res2.cause());
          }
        });
      } else {
        startFuture.fail(res.cause());
      }
    });
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    super.stop(stopFuture);
  }

  // Resolution is blocking so we do it in a worker
  private class ResolveWorker extends AbstractVerticle {

    private String main;
    private DeploymentOptions options;

    @Override
    public void start() throws Exception {

      DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
      locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
      locator.addService(TransporterFactory.class, FileTransporterFactory.class);
      locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
      locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
        @Override
        public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
          exception.printStackTrace();
        }
      });
      RepositorySystem system = locator.getService(RepositorySystem.class);
      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

      String local = System.getProperty(LOCAL_REPO_SYS_PROP, DEFAULT_MAVEN_LOCAL);
      LocalRepository localRepo = new LocalRepository(local);
      session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

      String remoteString = System.getProperty(REMOTE_REPOS_SYS_PROP, DEFAULT_MAVEN_REMOTES);
      // They are space delimited (space is illegal char in urls)
      String[] remotes = remoteString.split(" ");
      List<RemoteRepository> remoteList = new ArrayList<>();
      int count = 0;
      for (String remote: remotes) {
        RemoteRepository remoteRepo = new RemoteRepository.Builder("repo" + (count++), "default", remote).build();
        remoteList.add(remoteRepo);
      }

      Artifact artifact = new DefaultArtifact(verticleName);
      DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
      CollectRequest collectRequest = new CollectRequest();
      collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
      collectRequest.setRepositories(remoteList);

      DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFlter);

      List<ArtifactResult> artifactResults;
      try {
        artifactResults =
          system.resolveDependencies(session, dependencyRequest).getArtifactResults();
      } catch (DependencyResolutionException e) {
        throw new VertxException("Cannot find module " + verticleName + " in maven repositories");
      } catch (NullPointerException e) {
        // Sucks, but aether throws a NPE if repository name is invalid....
        throw new VertxException("Cannot find module " + verticleName + ". Maybe repository URL is invalid?");
      }

      // Generate the classpath - if the jar is already on the Vert.x classpath (e.g. the Vert.x dependencies, netty etc)
      // then we don't add it to the classpath for the module
      List<String> classpath = new ArrayList<>();
      for (ArtifactResult res: artifactResults) {
        File f = res.getArtifact().getFile();
        if (!systemJars.contains(f.getName())) {
          classpath.add(f.getAbsolutePath());
        }
      }

      for (ArtifactResult artifactResult : artifactResults) {
        Artifact art = artifactResult.getArtifact();
        String theGav = art.getGroupId() + ":" + art.getArtifactId() + ":" + art.getVersion();
        if (verticleName.equals(theGav)) {
          File file = art.getFile();

          try {
            try (ZipFile jar = new ZipFile(file)) {
              ZipEntry descriptorEntry = jar.getEntry("META-INF/MANIFEST.MF");
              if (descriptorEntry != null) {
                try (InputStream in = jar.getInputStream(descriptorEntry)) {
                  Manifest manifest = new Manifest(in);
                  Attributes attributes = manifest.getMainAttributes();
                  if (Starter.class.getName().equals(attributes.getValue("Main-Class"))) {
                    main = attributes.getValue("Main-Verticle");
                    if (main != null) {
                      options = new DeploymentOptions();
                      options.setExtraClasspath(classpath);
                      options.setIsolationGroup(verticleName);
                      return;
                    }
                  }
                }
              }
            }
          } catch (Exception e) {
            throw new VertxException(e);
          }
        }
      }
      throw new VertxException("Module " + verticleName + " is not deployable");
    }
  }

  // NOTE
  // No need to override stop and explicitly undeploy as the indirected deployment will be a child
  // deployment of the service deployment so will be automatically undeployed when the parent is
  // undeployed


}
