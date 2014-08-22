package io.vertx.maven.modules;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.jboss.shrinkwrap.resolver.api.ResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.jar.JarFile;
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
        ZipEntry descriptorEntry = jar.getEntry("/mod.json");
        if (descriptorEntry != null) {
          InputStream in = jar.getInputStream(descriptorEntry);
          try (Scanner scanner = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            String s = scanner.next();
            JsonObject obj = new JsonObject(s);
            String main = obj.getField("main");
            List<String> classpath = artifacts.stream().map(dep -> dep.asFile().getAbsolutePath()).collect(Collectors.toList());
            DeploymentOptions options = DeploymentOptions.options();
            options.setExtraClasspath(classpath);
            options.setIsolationGroup(gav);
            vertx.deployVerticleWithOptions(main, options, result -> {
              if (result.succeeded()) {
                startFuture.setResult(null);
              } else {
                startFuture.setFailure(result.cause());
              }
            });
            return;
          }
        }
      }
    }
    throw new Exception("No module found");
  }
}
