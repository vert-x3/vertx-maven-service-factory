package io.vertx.maven.modules;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Starter;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
  import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ModuleVerticle extends AbstractVerticle {

  final ClassLoader loader;
  final String gav;

  public ModuleVerticle(ClassLoader loader, String gav) {
    this.loader = loader;
    this.gav = gav;
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    MavenResolverSystem resolver = Maven.resolver();
    List<MavenResolvedArtifact> artifacts = Arrays.asList(resolver.resolve(gav).withTransitivity().asResolvedArtifact());
    for (MavenResolvedArtifact artifact : artifacts) {
      MavenCoordinate coord = artifact.getCoordinate();
      String artifactGav = coord.getGroupId() + ":" + coord.getArtifactId() + ":" + coord.getVersion();
      if (artifactGav.equals(gav)) {
        File file = artifact.asFile();
        ZipFile jar = new ZipFile(file);
        ZipEntry descriptorEntry = jar.getEntry("META-INF/MANIFEST.MF");
        if (descriptorEntry != null) {
          InputStream in = jar.getInputStream(descriptorEntry);
          Manifest manifest = new Manifest(in);
          Attributes attributes = manifest.getMainAttributes();
          if (Starter.class.getName().equals(attributes.getValue("Main-Class"))) {
            String main = attributes.getValue("Main-Verticle");
            if (main != null) {
              List<String> classpath = artifacts.stream().map(dep -> dep.asFile().getAbsolutePath()).collect(Collectors.toList());
              DeploymentOptions options = new DeploymentOptions();
              options.setExtraClasspath(classpath);
              options.setIsolationGroup(gav);
              vertx.deployVerticle(main, options, result -> {
                if (result.succeeded()) {
                  startFuture.complete(null);
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
    throw new Exception("No module found");
  }
}
