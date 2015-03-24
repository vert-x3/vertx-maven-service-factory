package io.vertx.maven;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxException;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.service.ServiceIndentifier;
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
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
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
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MavenVerticleFactory extends ServiceVerticleFactory {

  public static final String LOCAL_REPO_SYS_PROP = "vertx.maven.localRepo";
  public static final String REMOTE_REPOS_SYS_PROP = "vertx.maven.remoteRepos";
  public static final String HTTP_PROXY_SYS_PROP = "vertx.maven.httpProxy";
  public static final String HTTPS_PROXY_SYS_PROP = "vertx.maven.httpsProxy";

  private static final String USER_HOME = System.getProperty("user.home");
  private static final String FILE_SEP = System.getProperty("file.separator");
  private static final String DEFAULT_MAVEN_LOCAL = USER_HOME + FILE_SEP + ".m2" + FILE_SEP + "repository";
  private static final String DEFAULT_MAVEN_REMOTES =
    "http://central.maven.org/maven2/ http://oss.sonatype.org/content/repositories/snapshots/";

  private String localMavenRepo;
  private List<String> remoteMavenRepos;
  private String httpProxy;
  private String httpsProxy;

  public MavenVerticleFactory() {
    localMavenRepo = System.getProperty(LOCAL_REPO_SYS_PROP, DEFAULT_MAVEN_LOCAL);
    String remoteString = System.getProperty(REMOTE_REPOS_SYS_PROP, DEFAULT_MAVEN_REMOTES);
    // They are space delimited (space is illegal char in urls)
    remoteMavenRepos = Arrays.asList(remoteString.split(" "));
    httpProxy = System.getProperty(HTTP_PROXY_SYS_PROP);
    httpsProxy = System.getProperty(HTTPS_PROXY_SYS_PROP);
  }

  @Override
  public int order() {
    // Order must be higher than ServiceVerticleFactory so ServiceVerticleFactory gets tried first
    return super.order() + 1;
  }

  @Override
  public String resolve(String identifier, DeploymentOptions deploymentOptions, ClassLoader classLoader) throws Exception {
    RESOLVE_CALLED = true;
    String identifierNoPrefix = VerticleFactory.removePrefix(identifier);
    int pos = identifierNoPrefix.lastIndexOf("::");
    String coords;
    String qualifier = null;
    if (pos != -1) {
      coords = identifierNoPrefix.substring(0, pos);
      qualifier = identifierNoPrefix.substring(pos + 2);
    } else {
      coords = identifierNoPrefix;
    }
    ServiceIndentifier serviceID = new ServiceIndentifier(coords);
    if (serviceID.version() == null) {
      throw new IllegalArgumentException("Invalid service identifier, missing version: " + coords);
    }
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

    Proxy proxy = null;
    if (httpProxy != null) {
      URL url = new URL(httpProxy);
      Authentication authentication = extractAuth(url);
      proxy = new Proxy("http", url.getHost(), url.getPort(), authentication);
    }
    Proxy secureProxy = null;
    if (httpsProxy != null) {
      URL url = new URL(httpsProxy);
      Authentication authentication = extractAuth(url);
      secureProxy = new Proxy("https", url.getHost(), url.getPort(), authentication);
    }

    LocalRepository localRepo = new LocalRepository(localMavenRepo);
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

    int count = 0;
    List<RemoteRepository> remotes = new ArrayList<>();
    for (String remote: remoteMavenRepos) {
      URL url = new URL(remote);
      Authentication auth = extractAuth(url);
      if (auth != null) {
        url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile());
      }
      RemoteRepository.Builder builder = new RemoteRepository.Builder("repo" + (count++), "default", url.toString());
      if (auth != null) {
        builder.setAuthentication(auth);
      }
      switch (url.getProtocol()) {
        case "http":
          if (proxy != null) {
            builder.setProxy(proxy);
          }
          break;
        case "https":
          if (secureProxy != null) {
            builder.setProxy(secureProxy);
          }
          break;
      }
      RemoteRepository remoteRepo = builder.build();
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
    List<String> classpath = artifactResults.stream().
        map(res -> res.getArtifact().getFile().getAbsolutePath()).
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
    deploymentOptions.setIsolationGroup("__vertx_maven_" + coords);
    URLClassLoader urlc = new URLClassLoader(urls, classLoader);
    if (qualifier != null) {
      identifier = "service:" + serviceID.owner() + ":" + qualifier + ":" + serviceID.version();
    }
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
  };

  public String getHttpProxy() {
    return httpProxy;
  }

  public void setHttpProxy(String httpProxy) {
    this.httpProxy = httpProxy;
  }

  public String getHttpsProxy() {
    return httpsProxy;
  }

  public void setHttpsProxy(String httpsProxy) {
    this.httpsProxy = httpsProxy;
  }

  private static Authentication extractAuth(URL url) {
    String userInfo = url.getUserInfo();
    if (userInfo != null) {
      AuthenticationBuilder authBuilder = new AuthenticationBuilder();
      int sep = userInfo.indexOf(':');
      if (sep != -1) {
        authBuilder.addUsername(userInfo.substring(0, sep));
        authBuilder.addPassword(userInfo.substring(sep + 1));
      } else {
        authBuilder.addUsername(userInfo);
      }
      return authBuilder.build();
    }
    return null;
  }

  // testing
  public static volatile boolean RESOLVE_CALLED;
}

