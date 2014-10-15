package io.vertx.maven;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxException;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.service.ServiceVerticleFactory;
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
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MavenVerticleFactory extends ServiceVerticleFactory {

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
    ClassLoader cl = MavenVerticleFactory.class.getClassLoader();
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

  private String localMavenRepo;
  private List<String> remoteMavenRepos;

  public MavenVerticleFactory() {
    localMavenRepo = System.getProperty(LOCAL_REPO_SYS_PROP, DEFAULT_MAVEN_LOCAL);
    String remoteString = System.getProperty(REMOTE_REPOS_SYS_PROP, DEFAULT_MAVEN_REMOTES);
    // They are space delimited (space is illegal char in urls)
    remoteMavenRepos = Arrays.asList(remoteString.split(" "));
  }

  @Override
  public int order() {
    // Order must be higher than ServiceVerticleFactory so ServiceVerticleFactory gets tried first
    return 1;
  }

  @Override
  public String resolve(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader) throws Exception {
    RESOLVE_CALLED = true;
    String coords = VerticleFactory.removePrefix(identifier);
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

    LocalRepository localRepo = new LocalRepository(localMavenRepo);
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

    int count = 0;
    List<RemoteRepository> remotes = new ArrayList<>();
    for (String remote: remoteMavenRepos) {
      RemoteRepository remoteRepo = new RemoteRepository.Builder("repo" + (count++), "default", remote).build();
      remotes.add(remoteRepo);
    }

    Artifact artifact = new DefaultArtifact(coords);
    DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));
    collectRequest.setRepositories(remotes);

    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFlter);

    List<ArtifactResult> artifactResults;
    try {
      artifactResults =
        system.resolveDependencies(session, dependencyRequest).getArtifactResults();
    } catch (DependencyResolutionException e) {
      throw new VertxException("Cannot find module " + coords + " in maven repositories");
    } catch (NullPointerException e) {
      // Sucks, but aether throws a NPE if repository name is invalid....
      throw new VertxException("Cannot find module " + coords + ". Maybe repository URL is invalid?");
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
    URL[] urls = new URL[classpath.size()];
    int index = 0;
    for (String pathElement: classpath) {
      File file = new File(pathElement);
      try {
        URL url = file.toURI().toURL();
        urls[index++] = url;
      } catch (MalformedURLException e) {
        throw new IllegalStateException(e);
      }
    }
    URLClassLoader urlc = new URLClassLoader(urls, classLoader);
    return super.resolve(identifier, deploymentOptions, urlc);
  }

  public String getLocalMavenRepo() {
    return localMavenRepo;
  }

  public void setLocalMavenRepo(String localMavenRepo) {
    this.localMavenRepo = localMavenRepo;
  }

  public List<String> getRemoteMavenRepos() {
    return remoteMavenRepos;
  }

  public void setRemoteMavenRepos(List<String> remoteMavenRepos) {
    this.remoteMavenRepos = remoteMavenRepos;
  }

  // testing
  public static volatile boolean RESOLVE_CALLED;
}
