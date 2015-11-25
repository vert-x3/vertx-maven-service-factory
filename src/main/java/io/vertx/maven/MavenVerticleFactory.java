package io.vertx.maven;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.maven.resolver.ResolutionOptions;
import io.vertx.maven.resolver.Resolver;
import io.vertx.maven.resolver.ResolverOptions;
import io.vertx.service.ServiceVerticleFactory;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MavenVerticleFactory extends ServiceVerticleFactory {

  public static final String LOCAL_REPO_SYS_PROP = "vertx.maven.localRepo";
  public static final String REMOTE_REPOS_SYS_PROP = "vertx.maven.remoteRepos";
  public static final String HTTP_PROXY_SYS_PROP = "vertx.maven.httpProxy";
  public static final String HTTPS_PROXY_SYS_PROP = "vertx.maven.httpsProxy";
  public static final String REMOTE_SNAPSHOT_POLICY_SYS_PROP = "vertx.maven.remoteSnapshotPolicy";

  private static final String USER_HOME = System.getProperty("user.home");
  private static final String FILE_SEP = System.getProperty("file.separator");
  private static final String DEFAULT_MAVEN_LOCAL = USER_HOME + FILE_SEP + ".m2" + FILE_SEP + "repository";
  private static final String DEFAULT_MAVEN_REMOTES =
    "https://repo.maven.apache.org/maven2/ https://oss.sonatype.org/content/repositories/snapshots/";

  private Vertx vertx;
  private final Resolver resolver;

  public MavenVerticleFactory() {
    String remoteString = System.getProperty(REMOTE_REPOS_SYS_PROP, DEFAULT_MAVEN_REMOTES);
    // They are space delimited (space is illegal char in urls)
    List<String> remoteMavenRepos = Arrays.asList(remoteString.split(" "));

    resolver = Resolver.create(new ResolverOptions()
        .setHttpProxy(System.getProperty(HTTP_PROXY_SYS_PROP))
        .setHttpsProxy(System.getProperty(HTTPS_PROXY_SYS_PROP))
        .setLocalRepository(System.getProperty(LOCAL_REPO_SYS_PROP, DEFAULT_MAVEN_LOCAL))
        .setRemoteRepositories(remoteMavenRepos)
    );
  }

  @Override
  public void init(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public String prefix() {
    return "maven";
  }

  @Override
  public void resolve(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader, Future<String> resolution) {
    RESOLVE_CALLED = true;
    vertx.<Void>executeBlocking(fut -> {
      try {
        String identifierNoPrefix = VerticleFactory.removePrefix(identifier);
        String coordsString = identifierNoPrefix;
        String serviceName = null;
        int pos = identifierNoPrefix.lastIndexOf("::");
        if (pos != -1) {
          coordsString = identifierNoPrefix.substring(0, pos);
          serviceName = identifierNoPrefix.substring(pos + 2);
        }
        MavenCoords coords = new MavenCoords(coordsString);
        if (coords.version() == null) {
          throw new IllegalArgumentException("Invalid service identifier, missing version: " + coordsString);
        }

        List<Artifact> artifacts;
        try {
          artifacts =
              resolver.resolve(coordsString, new ResolutionOptions());
        } catch (NullPointerException e) {
          // Sucks, but aether throws a NPE if repository name is invalid....
          throw new IllegalArgumentException("Cannot find module " + coordsString + ". Maybe repository URL is invalid?");
        }

        // When service name is null we look at the Main-Verticle in META-INF/MANIFEST.MF
        String serviceIdentifer = null;
        if (serviceName != null) {
          serviceIdentifer = "service:" + serviceName;
        } else {
          for (Artifact result : artifacts) {
            if (result.getGroupId().equals(coords.owner()) && result.getArtifactId().equals(coords.serviceName())) {
              File file = result.getFile();
              JarFile jarFile = new JarFile(file);
              Manifest manifest = jarFile.getManifest();
              if (manifest != null) {
                serviceIdentifer = (String) manifest.getMainAttributes().get(new Attributes.Name("Main-Verticle"));
              }
            }
          }
          if (serviceIdentifer == null) {
            throw new IllegalArgumentException("Invalid service identifier, missing service name: " + identifierNoPrefix);
          }
        }

        // Generate the classpath - if the jar is already on the Vert.x classpath (e.g. the Vert.x dependencies, netty etc)
        // then we don't add it to the classpath for the module
        List<String> classpath = artifacts.stream().
            map(res -> res.getFile().getAbsolutePath()).
            collect(Collectors.toList());
        URL[] urls = new URL[classpath.size()];
        int index = 0;
        List<String> extraCP = new ArrayList<>(urls.length);
        for (String pathElement: classpath) {
          File file = new File(pathElement);
          extraCP.add(file.getAbsolutePath());
          try {
            URL url = file.toURI().toURL();
            urls[index++] = url;
          } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
          }
        }
        deploymentOptions.setExtraClasspath(extraCP);
        deploymentOptions.setIsolationGroup("__vertx_maven_" + coordsString);
        URLClassLoader urlc = new URLClassLoader(urls, classLoader);

        super.resolve(serviceIdentifer, deploymentOptions, urlc, resolution);
        fut.complete();
      } catch (Exception e) {
        fut.fail(e);
        resolution.fail(e);
      }
    }, ar -> {
    });
  }

  protected void customizeRemoteRepoBuilder(RemoteRepository.Builder builder) {
    String updatePolicy = System.getProperty(REMOTE_SNAPSHOT_POLICY_SYS_PROP);
    if (updatePolicy != null && !updatePolicy.isEmpty()) {
      builder.setSnapshotPolicy(new RepositoryPolicy(true, updatePolicy, RepositoryPolicy.CHECKSUM_POLICY_WARN));
    }
  }

  // for testing purpose.
  public static volatile boolean RESOLVE_CALLED;
}

