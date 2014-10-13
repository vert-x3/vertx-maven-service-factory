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
import io.vertx.core.impl.VertxInternal;
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
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
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

  private final String verticleName;
  private final VertxInternal vertx;

  MavenVerticle(String verticleName, Vertx vertx) {
    this.verticleName = verticleName;
    this.vertx = (VertxInternal)vertx;     // FIXME Naughty!!
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    vertx.executeBlocking(() -> {
      resolve(startFuture);  // This can block for some time so run it on a worker
      return null;
    }, res -> {
      if (res.failed()) {
        startFuture.fail(res.cause());
      }
    });
  }

  private void resolve(Future<Void> startFuture) {
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
                  String main = attributes.getValue("Main-Verticle");
                  if (main != null) {
                    List<String> classpath =
                      artifactResults.stream().map(dep -> dep.getArtifact().getFile().getAbsolutePath()).collect(Collectors.toList());
                    DeploymentOptions options = new DeploymentOptions();
                    options.setExtraClasspath(classpath);
                    options.setIsolationGroup(verticleName);
                    vertx.deployVerticle(main, options, result -> {
                      if (result.succeeded()) {
                        startFuture.complete();
                      } else {
                        startFuture.fail(result.cause());
                      }
                    });
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


  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    super.stop(stopFuture);
  }


}
