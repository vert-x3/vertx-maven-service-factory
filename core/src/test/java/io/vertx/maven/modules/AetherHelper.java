package io.vertx.maven.modules;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AetherHelper {

  final RepositorySystem system;
  final RepositorySystemSession session;

  public AetherHelper(String localRepo) {
    system = createRepositorySystem();
    session = newRepositorySystemSession(system, localRepo);
  }

  public void installArtifact(String groupId, String artifactId, String version, File jarFile, File pomFile) throws Exception {
    Artifact jarArtifact = new DefaultArtifact(groupId, artifactId, "", "jar", version);
    jarArtifact = jarArtifact.setFile(jarFile);
    Artifact pomArtifact = new SubArtifact(jarArtifact, "", "pom");
    pomArtifact = pomArtifact.setFile(pomFile);
    InstallRequest installRequest = new InstallRequest();
    installRequest.addArtifact(jarArtifact ).addArtifact( pomArtifact );
    system.install(session, installRequest);
  }

  public ArtifactResult resolveArtifact(String groupId, String artifactId, String extension, String version) throws Exception {
    Artifact artifact = new DefaultArtifact(groupId, artifactId, extension, version);
    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(artifact);
    request.setRepositories(Collections.emptyList());
    return system.resolveArtifact(session, request);
  }

  public List<Artifact> getDependencies(String groupId, String artifactId, String extension, String version) throws Exception {
    Artifact artifact = new DefaultArtifact(groupId, artifactId, extension, version);
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency( artifact, ""));
    collectRequest.setRepositories(Collections.emptyList());
    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE));
    DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
    List<Artifact> dependencies = new ArrayList<>();
    for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
      if (!artifactResult.isResolved()) {
        throw new Exception("Could not resolve artifact " + artifactResult.getRequest().getArtifact());
      }
      dependencies.add(artifactResult.getArtifact());
    }
    return dependencies;
  }

  public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, String path) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepo = new LocalRepository(path);
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
    return session;
  }

  public static RepositorySystem createRepositorySystem() {
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
    return locator.getService(RepositorySystem.class);
  }

}
