package io.vertx.maven.modules;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.maven.MavenVerticleFactory;
import io.vertx.maven.utils.FileUtils;
import io.vertx.test.core.VertxTestBase;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import javax.servlet.DispatcherType;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FactoryTest extends VertxTestBase {

  private static final String FILE_SEP = System.getProperty("file.separator");

  private List<Server> servers = new ArrayList<>();

  @BeforeClass
  public static void loadConstants() throws IOException {
    File file = new File("target/test-classes/constants.json");
    if (! file.exists()) {
      throw new IllegalStateException("Cannot find the constants.json file, please run Maven first");
    }
    String content = new String(Files.readAllBytes(file.toPath()));
    JsonObject node = new JsonObject(content);
    System.setProperty("version", node.getString("version"));
    System.setProperty("localRepository", node.getString("localRepository"));
  }

  @BeforeClass
  public static void clearAllRepositories() {
    File repositories = new File("target" + FILE_SEP + "repositories");
    FileUtils.delete(repositories);
  }

  @Override
  public void setUp() throws Exception {
    System.clearProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP);
    System.clearProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP);
    System.clearProperty(MavenVerticleFactory.HTTP_PROXY_SYS_PROP);
    MavenVerticleFactory.RESOLVE_CALLED = false;
    super.setUp();
    System.setProperty("javax.net.ssl.trustStore", new File(FactoryTest.class.getResource("client-truststore.jks").toURI()).getAbsolutePath());
    System.setProperty("javax.net.ssl.trustStorePassword", "wibble");
  }

  @Override
  public void tearDown() throws Exception {
    for (Server server : servers) {
      server.stop();
    }
    // Sanity check to make sure the module was resolved with the MavenVerticleFactory not delegated
    // to the ServiceVerticleFactory
    assertWaitUntil(() -> MavenVerticleFactory.RESOLVE_CALLED);
    super.tearDown();
  }

  @Test
  public void testDeploy() throws Exception {
    File testRepo = createMyModuleRepository("testDeploy");
    configureRepos(testRepo, null);
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", onSuccess(res -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testStartsOK() throws Exception {
    File testRepo = createMyModuleRepository("testStartsOK");
    configureRepos(testRepo, null);
    vertx.eventBus().localConsumer("mymodule").handler(message -> {
      assertEquals("whatever", message.body());
      testComplete();
    });
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA");
    await();
  }

  @Test
  public void testUseMainVerticleFromManifest() throws Exception {
    File testRepo = createMyModuleMainVerticleRepository("testUseMainVerticleFromManifest");
    configureRepos(testRepo, null);
    vertx.eventBus().localConsumer("mymodule").handler(message -> {
      assertEquals("whatever", message.body());
      testComplete();
    });
    vertx.deployVerticle("maven:my:module:1.0");
    await();
  }

  // Test another service in the same artifact
  @Test
  public void testOtherServiceStartsOK() throws Exception {
    File testRepo = createMyModuleRepository("testOtherServiceStartsOK");
    configureRepos(testRepo, null);
    vertx.eventBus().localConsumer("mymodule").handler(message -> {
      assertEquals("whatever2", message.body());
      testComplete();
    });
    vertx.deployVerticle("maven:my:module:1.0::my.serviceB");
    await();
  }

  @Test
  public void testZipExtension() throws Exception {
    waitFor(2);
    File testRepo = createMyModuleZipRepository("testZipExtension");
    configureRepos(testRepo, null);
    vertx.eventBus().localConsumer("mymodule").handler(message -> {
      assertEquals("whateverZip", message.body());
      complete();
    });
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", onFailure(err -> {
      vertx.deployVerticle("maven:my:module:zip:1.0::my.serviceZip", onSuccess(id -> {
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testClassifier() throws Exception {
    waitFor(2);
    File testRepo = createMyModuleClassifierRepository("testClassifier");
    configureRepos(testRepo, null);
    vertx.eventBus().localConsumer("mymodule").handler(message -> {
      assertEquals("whateverClassifier", message.body());
      complete();
    });
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", onFailure(err -> {
      vertx.deployVerticle("maven:my:module:jar:the_classifier:1.0::my.serviceClassifier", onSuccess(id -> {
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testConfiguredResolveFromLocalRepository() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredResolveFromLocalRepository");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(createRemoteServer(testRepo));
    configureRepos(testRepo, "http://localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testConfiguredResolveFromRemoteRepository() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredResolveFromRemoteRepository");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(createRemoteServer(testRepo));
    configureRepos(emptyRepo, "http://localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  @Ignore
  public void testConfiguredResolveFromSecureRemoteRepository() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredResolveFromSecureRemoteRepository");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(configureTls(createRemoteServer(testRepo)));
    configureRepos(emptyRepo, "https://localhost:8443/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testConfiguredResolveFromRemoteAuthenticatingRepository() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredResolveFromRemoteAuthenticatingRepository");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    Server server = createRemoteServer(testRepo);
    AuthFilter filter = AuthFilter.serverAuthenticator("username_value", "password_value");
    ((ServletContextHandler) server.getHandler()).addFilter(new FilterHolder(filter), "/*", EnumSet.of(DispatcherType.REQUEST));
    server.start();
    servers.add(server);
    configureRepos(emptyRepo, "http://username_value:password_value@localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      assertTrue(filter.authenticated.get());
      testComplete();
    }));
    await();
  }

  @Test
  @Ignore
  public void testConfiguredResolveFromSecureRemoteAuthenticatingRepository() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredResolveFromSecureRemoteAuthenticatingRepository");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    Server server = createRemoteServer(testRepo);
    AuthFilter filter = AuthFilter.serverAuthenticator("username_value", "password_value");
    ((ServletContextHandler) server.getHandler()).addFilter(new FilterHolder(filter), "/*", EnumSet.of(DispatcherType.REQUEST));
    startRemoteServer(configureTls(server));
    configureRepos(emptyRepo, "https://username_value:password_value@localhost:8443/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testConfiguredHttpProxy() throws Exception {
    System.setProperty(MavenVerticleFactory.HTTP_PROXY_SYS_PROP, "http://localhost:8081");
    File testRepo = createMyModuleRepository("testConfiguredHttpProxy");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(createRemoteServer(testRepo));
    Server server = new Server(8081);
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);
    handler.addServletWithMapping(ProxyServlet.class, "/*").setInitParameter("maxThreads", "10");
    URL expectedHost = new URL("http://localhost:8080/");
    UrlCollectorFilter urlCollector = new UrlCollectorFilter();
    handler.addFilterWithMapping(new FilterHolder(urlCollector), "/*", 0);
    server.start();
    servers.add(server);
    configureRepos(emptyRepo, "http://localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      assertTrue("Was expecting " + urlCollector.requestedHosts + " to contain " + expectedHost, urlCollector.requestedHosts.contains(expectedHost));
      testComplete();
    }));
    await();
  }

  // @Test
  // Cannot pass since the ProxyServlet does not support CONNECT and tunneling
  public void testConfiguredHttpsProxy() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredHttpsProxy");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(configureTls(createRemoteServer(testRepo)));
    Server server = new Server(8081);
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);
    handler.addServletWithMapping(ProxyServlet.class, "/").setInitParameter("maxThreads", "10");
    UrlCollectorFilter urlCollector = new UrlCollectorFilter();
    handler.addFilterWithMapping(new FilterHolder(urlCollector), "/*", 0);
    server.start();
    servers.add(server);
    System.setProperty(MavenVerticleFactory.HTTP_PROXY_SYS_PROP, "http://localhost:8081");
    configureRepos(emptyRepo, "https://localhost:8443/");
    URL expectedHost = new URL("http://localhost:8443/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      assertTrue("Was expecting " + urlCollector.requestedHosts + " to contain " + expectedHost, urlCollector.requestedHosts.contains(expectedHost));
      testComplete();
    }));
    await();
  }

  @Test
  public void testConfiguredAuthenticatingHttpProxy() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredAuthenticatingHttpProxy");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(createRemoteServer(testRepo));
    System.setProperty(MavenVerticleFactory.HTTP_PROXY_SYS_PROP, "http://username_value:password_value@localhost:8081");
    Server server = new Server(8081);
    ServletHandler handler = new ServletHandler();
    server.setHandler(handler);
    AuthFilter filter = AuthFilter.proxyAuthenticator("username_value", "password_value");
    handler.addFilterWithMapping(new FilterHolder(filter), "/*", 0);
    handler.addServletWithMapping(ProxyServlet.class, "/*").setInitParameter("maxThreads", "10");
    URL expectedHost = new URL("http://localhost:8080/");
    UrlCollectorFilter urlCollector = new UrlCollectorFilter();
    handler.addFilterWithMapping(new FilterHolder(urlCollector), "/*", 0);
    server.start();
    servers.add(server);
    configureRepos(emptyRepo, "http://localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
      assertTrue("Was expecting " + urlCollector.requestedHosts + " to contain " + expectedHost, urlCollector.requestedHosts.contains(expectedHost));
      assertTrue(filter.authenticated.get());
      testComplete();
    }));
    await();
  }

  @Test
  public void testConfiguredHttpProxyFailure() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredHttpProxyFailure");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(createRemoteServer(testRepo));
    System.setProperty(MavenVerticleFactory.HTTP_PROXY_SYS_PROP, "http://localhost:8081");
    configureRepos(emptyRepo, "http://localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onFailure(err -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testLoadSystemDependencyFromVerticleLoaderWhenAbsent() throws Exception {
    File testRepo = createMyModuleRepositoryWithSystemDep("testLoadSystemDependencyFromVerticleLoaderWhenAbsent");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(createRemoteServer(testRepo));
    configureRepos(emptyRepo, "http://localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions().setConfig(new JsonObject().put("loaded_globally", false)), onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testLoadSystemDependencyFromParentLoaderWhenPresent() throws Exception {
    String localRepository = System.getProperty("localRepository");
    AetherHelper localHelper = new AetherHelper(localRepository);
    ArtifactResult result = localHelper.resolveArtifact("javax.portlet", "portlet-api", "jar", "2.0");
    URLClassLoader loader = new URLClassLoader(new URL[]{result.getArtifact().getFile().toURI().toURL()}, FactoryTest.class.getClassLoader());
    loader.loadClass("javax.portlet.Portlet");
    assertTrue(result.isResolved());
    ClassLoader prev = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(loader);
    try {
      File testRepo = createMyModuleRepositoryWithSystemDep("testLoadSystemDependencyFromParentLoaderWhenPresent");
      File emptyRepo = Files.createTempDirectory("vertx").toFile();
      emptyRepo.deleteOnExit();
      startRemoteServer(createRemoteServer(testRepo));
      configureRepos(emptyRepo, "http://localhost:8080/");
      vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions().setConfig(new JsonObject().put("loaded_globally", true)), onSuccess(id -> {
        testComplete();
      }));
      await();
    } finally {
      Thread.currentThread().setContextClassLoader(prev);
    }
  }

  @Test
  public void testLoadDependencyFromVerticleLoaderWhenAbsent() throws Exception {
    File testRepo = createMyModuleRepositoryWithDep("testLoadDependencyFromVerticleLoaderWhenAbsent");
    File emptyRepo = Files.createTempDirectory("vertx").toFile();
    emptyRepo.deleteOnExit();
    startRemoteServer(createRemoteServer(testRepo));
    configureRepos(emptyRepo, "http://localhost:8080/");
    vertx.deployVerticle("maven:my:module:1.0::my.serviceB", new DeploymentOptions(), onSuccess(id -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testLoadDependencyFromVerticleLoaderWhenPresent() throws Exception {
    String localRepository = System.getProperty("localRepository");
    AetherHelper localHelper = new AetherHelper(localRepository);
    ArtifactResult result = localHelper.resolveArtifact("org.junit.jupiter", "junit-jupiter-api", "jar", "5.0.0");
    URLClassLoader loader = new URLClassLoader(new URL[]{result.getArtifact().getFile().toURI().toURL()}, FactoryTest.class.getClassLoader());
    Class<?> clazz = loader.loadClass("org.junit.jupiter.api.Test");
    assertSame(clazz.getClassLoader(), loader);
    assertTrue(result.isResolved());
    ClassLoader prev = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(loader);
    try {
      File testRepo = createMyModuleRepositoryWithDep("testLoadDependencyFromVerticleLoaderWhenPresent");
      File emptyRepo = Files.createTempDirectory("vertx").toFile();
      emptyRepo.deleteOnExit();
      startRemoteServer(createRemoteServer(testRepo));
      configureRepos(emptyRepo, "http://localhost:8080/");
      vertx.deployVerticle("maven:my:module:1.0::my.serviceA", new DeploymentOptions(), onSuccess(id -> {
        testComplete();
      }));
      await();
    } finally {
      Thread.currentThread().setContextClassLoader(prev);
    }
  }

  @Test
  public void testSysPropsBad() throws Exception {
    System.setProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP, "qiwhdiqowd");
    System.setProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP, "yqgwduyqwd");
    vertx.close();
    try {
      vertx = Vertx.vertx();
      fail("Exception expected");
    } catch (ServiceConfigurationError e) {
      // OK
    }
    // The resolver was not created correctly, so resolve was not called.
    // Force it to true.
    MavenVerticleFactory.RESOLVE_CALLED = true;
  }

  @Test
  public void testUndeploy() throws Exception {
    File testRepo = createMyModuleRepository("testUndeploy");
    configureRepos(testRepo, null);
    CountDownLatch latch = new CountDownLatch(2);
    vertx.eventBus().localConsumer("mymoduleStopped").handler(message -> latch.countDown());
    vertx.deployVerticle("maven:my:module:1.0::my.serviceA", onSuccess(id -> {
      vertx.undeploy(id, onSuccess(v -> {
        latch.countDown();
      }));
    }));
    awaitLatch(latch);
  }

  @Test
  public void testInvalidServiceIDNoVersion() throws Exception {
    // Must always have version for Maven service factory
    vertx.deployVerticle("maven:my:module::my.module", onFailure(err -> {
      assertTrue(err instanceof IllegalArgumentException);
      assertTrue(err.getMessage().startsWith("Invalid service identifier"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testNoServiceName() throws Exception {
    File testRepo = createMyModuleRepository("testNoServiceName");
    configureRepos(testRepo, null);
    vertx.deployVerticle("maven:my:module:1.0", onFailure(err -> {
      assertTrue(err instanceof IllegalArgumentException);
      assertTrue(err.getMessage().startsWith("Invalid service identifier"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testNonExistentModule() throws Exception {
    vertx.deployVerticle("maven:foo:module:1.0::my.serviceA", onFailure(err -> {
      assertTrue(err instanceof IllegalArgumentException);
      assertTrue(err.getMessage().startsWith("Cannot resolve artifact"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testMissingTransitiveDependency() throws Exception {
    final File repository = createMyModuleRepository(
        "testMissingTransitiveDependency",
        new File("target" + FILE_SEP + "test-projects" + FILE_SEP + "test-module" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("target" + FILE_SEP + "test-classes" + FILE_SEP + "poms" + FILE_SEP +
            "test-module-with-missing-deps.xml")
    );

    configureRepos(repository, null);

    vertx.deployVerticle("maven:my:module:1.0", onFailure(err -> {
      assertTrue(err instanceof IllegalArgumentException);
      assertTrue(err.getMessage().startsWith("Cannot resolve artifact"));
      assertTrue(err.getMessage().contains("missing:missing"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testNonExistentService() throws Exception {
    File testRepo = createMyModuleRepository("testNonExistentService");
    configureRepos(testRepo, null);
    vertx.deployVerticle("maven:my:module:1.0::not.exists", onFailure(err -> {
      assertTrue(err instanceof IllegalArgumentException);
      assertTrue(err.getMessage().startsWith("Cannot find service descriptor file not.exists.json on classpath"));
      testComplete();
    }));
    await();
  }


  @Test
  public void testBadCoords() throws Exception {
    vertx.deployVerticle("maven:uhiuhuih::my.wibble", onFailure(err -> {
      assertTrue(err instanceof IllegalArgumentException);
      assertTrue(err.getMessage().startsWith("Invalid maven coordinates:"));
      testComplete();
    }));
    await();
  }

  // Exists in Maven but not deployable
  @Test
  public void testNotDeployable() throws Exception {
    vertx.deployVerticle("maven:io.vertx:vertx-core:2.1.2::no-service", onFailure(err -> {
      assertTrue(err instanceof IllegalArgumentException);
      assertTrue(err.getMessage().startsWith("Cannot find service descriptor file no-service.json"));
      testComplete();
    }));
    await();
  }


  private File createMyModuleRepository(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File("target" + FILE_SEP + "test-projects" + FILE_SEP + "test-module" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("target" + FILE_SEP + "test-classes" + FILE_SEP + "poms" + FILE_SEP + "test-module.xml")
    );
  }

  private File createMyModuleZipRepository(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File("target" + FILE_SEP + "test-projects" + FILE_SEP + "test-module" + FILE_SEP + "target" + FILE_SEP + "mymodule.zip"),
        new File("target" + FILE_SEP + "test-classes" + FILE_SEP + "poms" + FILE_SEP + "test-module.xml")
    );
  }

  private File createMyModuleClassifierRepository(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File("target" + FILE_SEP + "test-projects" + FILE_SEP + "test-module" + FILE_SEP + "target" + FILE_SEP + "mymodule-the_classifier.jar"),
        new File("target" + FILE_SEP + "test-classes" + FILE_SEP + "poms" + FILE_SEP + "test-module.xml")
    );
  }

  private File createMyModuleMainVerticleRepository(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File("target" + FILE_SEP + "test-projects" + FILE_SEP + "test-module-main-verticle" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("target" + FILE_SEP + "test-classes" + FILE_SEP + "poms" + FILE_SEP + "test-module-main-verticle.xml")
    );
  }

  private File createMyModuleRepositoryWithSystemDep(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File("target" + FILE_SEP + "test-projects" + FILE_SEP + "test-module-system-dep" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("target" + FILE_SEP + "test-classes" + FILE_SEP + "poms" + FILE_SEP + "test-module-system-dep.xml")
    );
  }

  private File createMyModuleRepositoryWithDep(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File("target" + FILE_SEP + "test-projects" + FILE_SEP + "test-module-dep" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("target" + FILE_SEP + "test-classes" + FILE_SEP + "poms" + FILE_SEP + "test-module-dep.xml")
    );
  }

  private File getWorkDir(String path) {
    return new File("target" + FILE_SEP + "repositories" + FILE_SEP + path);
  }

  private File getLogFile(String path) {
    return new File("target" + FILE_SEP + path);
  }

  private File createMyModuleRepository(String repoPath, File artifactFile, File pomFile) throws Exception {

    // Create our test repo
    File testRepo = getWorkDir(repoPath);
    assertFalse("Repository " + testRepo.getAbsolutePath() + " should not exists", testRepo.exists());
    AetherHelper testHelper = new AetherHelper(testRepo.getAbsolutePath(), new FileWriter(getLogFile(repoPath + ".log")));

    // Install my:module:jar:1.0
    testHelper.installArtifact("my", "module", "1.0", artifactFile, pomFile);

    // Resolve all the dependencies of vertx-core we need from the local repository
    // and install them in the test repo
    String localRepository = System.getProperty("localRepository");
    System.out.println("localRepository = " + localRepository);
    AetherHelper localHelper = new AetherHelper(localRepository);
    testHelper.installArtifacts(localHelper.getDependencies("io.vertx", "vertx-dependencies", "pom", System.getProperty("version")));
    System.out.println("Installing vertx-dependencies!!!");
    testHelper.installArtifacts(localHelper.getDependencies("io.vertx", "vertx-core", "jar", System.getProperty("version")));
    System.out.println("Installing vertx-core!!!");

    // Make sure we have something
    assertTrue(testRepo.exists());
    assertTrue(testRepo.isDirectory());
    return testRepo;
  }

  private void configureRepos(File localRepo, String remoteRepo) {
    System.clearProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP);
    System.clearProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP);
    if (localRepo != null) {
      System.setProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP, localRepo.getAbsolutePath());
    }
    if (remoteRepo != null) {
      System.setProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP, remoteRepo);
    }
    vertx.close();
    vertx = Vertx.vertx();
  }

  private Server configureTls(Server server) throws Exception {
    HttpConfiguration http_config = new HttpConfiguration();
    http_config.setSecureScheme("https");
    http_config.setSecurePort(8443);
    http_config.setOutputBufferSize(32768);
    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
    http.setPort(8080);
    http.setIdleTimeout(30000);
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath(new File(FactoryTest.class.getResource("server-keystore.jks").toURI()).getAbsolutePath());
    sslContextFactory.setKeyStorePassword("wibble");
    HttpConfiguration https_config = new HttpConfiguration(http_config);
    https_config.addCustomizer(new SecureRequestCustomizer());
    ServerConnector https = new ServerConnector(server,
        new SslConnectionFactory(sslContextFactory, "http/1.1"),
        new HttpConnectionFactory(https_config));
    https.setPort(8443);
    https.setIdleTimeout(500000);
    server.setConnectors(new Connector[]{https});
    return server;
  }

  private Server createRemoteServer(File remoteRepo) throws Exception {
    Server server = new Server(8080);
    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    handler.setContextPath("/");
    handler.addServlet(DefaultServlet.class, "/").setInitParameter("resourceBase", remoteRepo.getAbsolutePath());
    server.setHandler(handler);
    return server;
  }

  private void startRemoteServer(Server server) throws Exception {
    server.start();
    servers.add(server);
  }
}
