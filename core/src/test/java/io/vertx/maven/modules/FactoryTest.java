package io.vertx.maven.modules;

import com.google.common.io.Files;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.maven.MavenVerticleFactory;
import io.vertx.test.core.VertxTestBase;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FactoryTest extends VertxTestBase {

  private static final String FILE_SEP = System.getProperty("file.separator");

  @Override
  public void setUp() throws Exception {
    super.setUp();
    System.clearProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP);
    System.clearProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP);
    MavenVerticleFactory.RESOLVE_CALLED = false;
  }

  @Override
  public void tearDown() throws Exception {
    // Sanity check to make sure the module was resolved with the MavenVerticleFactory not delegated
    // to the ServiceVerticleFactory
    assertTrue(MavenVerticleFactory.RESOLVE_CALLED);
    super.tearDown();
  }

  @Test
  public void testDeploy() throws Exception {
    File testRepo = createMyModuleRepository("testDeploy");
    configureRepo(testRepo, null);
    vertx.deployVerticle("service:my:module:1.0", onSuccess(res -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testStartsOK() throws Exception {
    File testRepo = createMyModuleRepository("testStartsOK");
    configureRepo(testRepo, null);
    vertx.eventBus().localConsumer("mymodule").handler(message -> testComplete());
    vertx.deployVerticle("service:my:module:1.0");
    await();
  }

  @Test
  public void testNonExistent() throws Exception {
    vertx.deployVerticle("service:module:doesnt:exist", res -> {
      assertFalse(res.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testInvalidName() throws Exception {
    vertx.deployVerticle("service:uhqeduhqewdhuquhd", res -> {
      assertFalse(res.succeeded());
      testComplete();
    });
    await();
  }

  // Exists in Maven but not deployable
  @Test
  public void testNotDeployable() throws Exception {
    vertx.deployVerticle("service:io.vertx:vertx-core:2.1.2", res -> {
      assertFalse(res.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testConfiguredResolveFromLocalRepository() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredResolveFromLocalRepository");
    File emptyRepo = Files.createTempDir();
    emptyRepo.deleteOnExit();
    testConfiguredRepo(testRepo, emptyRepo, new JsonObject());
  }

  @Test
  public void testConfiguredResolveFromRemoteRepository() throws Exception {
    File testRepo = createMyModuleRepository("testConfiguredResolveFromRemoteRepository");
    File emptyRepo = Files.createTempDir();
    emptyRepo.deleteOnExit();
    testConfiguredRepo(emptyRepo, testRepo, new JsonObject());
  }

  @Test
  public void testLoadSystemDependencyFromVerticleLoaderWhenAbsent() throws Exception {
    File testRepo = createMyModuleRepositoryWithSystemDep("testLoadSystemDependencyFromVerticleLoaderWhenAbsent");
    File emptyRepo = Files.createTempDir();
    emptyRepo.deleteOnExit();
    testConfiguredRepo(emptyRepo, testRepo, new JsonObject().put("loaded_globally", false));
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
      File emptyRepo = Files.createTempDir();
      emptyRepo.deleteOnExit();
      testConfiguredRepo(emptyRepo, testRepo, new JsonObject().put("loaded_globally", true));
    } finally {
      Thread.currentThread().setContextClassLoader(prev);
    }
  }

  @Test
  public void testLoadDependencyFromVerticleLoaderWhenAbsent() throws Exception {
    File testRepo = createMyModuleRepositoryWithDep("testLoadDependencyFromVerticleLoaderWhenAbsent");
    File emptyRepo = Files.createTempDir();
    emptyRepo.deleteOnExit();
    testConfiguredRepo(emptyRepo, testRepo, new JsonObject());
  }

  @Test
  public void testLoadDependencyFromVerticleLoaderWhenPresent() throws Exception {
    String localRepository = System.getProperty("localRepository");
    AetherHelper localHelper = new AetherHelper(localRepository);
    ArtifactResult result = localHelper.resolveArtifact("com.google.guava", "guava", "jar", "17.0");
    URLClassLoader loader = new URLClassLoader(new URL[]{result.getArtifact().getFile().toURI().toURL()}, FactoryTest.class.getClassLoader());
    loader.loadClass("com.google.common.collect.BiMap");
    assertTrue(result.isResolved());
    ClassLoader prev = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(loader);
    try {
      File testRepo = createMyModuleRepositoryWithDep("testLoadDependencyFromVerticleLoaderWhenPresent");
      File emptyRepo = Files.createTempDir();
      emptyRepo.deleteOnExit();
      testConfiguredRepo(emptyRepo, testRepo, new JsonObject());
    } finally {
      Thread.currentThread().setContextClassLoader(prev);
    }
  }

  private void testConfiguredRepo(File localRepo, File remoteRepo, JsonObject config) throws Exception {
    CountDownLatch listenLatch = new CountDownLatch(1);
    Vertx vertx2 = Vertx.vertx();
    try {
      Thread t = new Thread() {
        @Override
        public void run() {
          HttpServer server = vertx2.createHttpServer(new HttpServerOptions().setPort(8080).setHost("localhost"));
          server.requestStream().handler(req -> {
            String file = req.path().equals("/") ? "index.html" : req.path();
            File f = new File(remoteRepo, file);
            if (f.exists()) {
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              try {
                Files.copy(f, baos);
                byte[] data = baos.toByteArray();
                req.response().setChunked(true).write(Buffer.buffer(data)).end();
              } catch (IOException e) {
                req.response().setStatusCode(500).end();
              }
            } else {
              req.response().setStatusCode(404).end();
            }
          });
          server.listen(ar -> {
            assertTrue(ar.succeeded());
            listenLatch.countDown();
          });
        }
      };
      t.start();
      listenLatch.await();
      configureRepo(localRepo, "http://localhost:8080/");
      vertx.deployVerticle("service:my:module:1.0", new DeploymentOptions().setConfig(config), res -> {
        if (res.failed()) {
          res.cause().printStackTrace();
        }
        assertTrue(res.succeeded());
        testComplete();
      });
      await();
    } finally {
      CountDownLatch closeLatch = new CountDownLatch(1);
      vertx2.close(v -> {
        closeLatch.countDown();
      });
      closeLatch.await();
    }
  }

  @Test
  public void testSysPropsBad() throws Exception {
    System.setProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP, "qiwhdiqowd");
    System.setProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP, "yqgwduyqwd");
    vertx.close();
    vertx = Vertx.vertx();
    vertx.deployVerticle("service:my:module:1.0", res -> {
      assertTrue(res.failed());
      testComplete();
    });
    await();
  }

  @Test
  public void testUndeploy() throws Exception {
    File testRepo = createMyModuleRepository("testUndeploy");
    configureRepo(testRepo, null);
    CountDownLatch latch = new CountDownLatch(2);
    vertx.eventBus().localConsumer("mymoduleStopped").handler(message -> latch.countDown());
    vertx.deployVerticle("service:my:module:1.0", res -> {
      assertTrue(res.succeeded());
      vertx.undeployVerticle(res.result(), res2 -> {
        assertTrue(res2.succeeded());
        latch.countDown();
      });
    });
    awaitLatch(latch);
  }

  @Test
  public void testInvalidServiceIDNoVersion() throws Exception {
    // Must always have version for Maven service factory
    vertx.deployVerticle("service:my:module", res -> {
      assertTrue(res.failed());
      assertTrue(res.cause() instanceof IllegalArgumentException);
      assertTrue(res.cause().getMessage().startsWith("Invalid service identifier"));
      testComplete();
    });
    await();
  }

  @Test
  public void testInvalidServiceID() throws Exception {
    // Must always have version for Maven service factory
    vertx.deployVerticle("service:uqwhdiuqwhdq", res -> {
      assertTrue(res.failed());
      assertTrue(res.cause() instanceof IllegalArgumentException);
      assertTrue(res.cause().getMessage().startsWith("Invalid service identifier"));
      testComplete();
    });
    await();
  }

  private File createMyModuleRepository(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File(".." + FILE_SEP + "test-module" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("src" + FILE_SEP + "test" + FILE_SEP + "poms" + FILE_SEP + "test-module.xml")
    );
  }

  private File createMyModuleRepositoryWithSystemDep(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File(".." + FILE_SEP + "test-module-system-dep" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("src" + FILE_SEP + "test" + FILE_SEP + "poms" + FILE_SEP + "test-module-system-dep.xml")
    );
  }

  private File createMyModuleRepositoryWithDep(String repoPath) throws Exception {
    return createMyModuleRepository(
        repoPath,
        new File(".." + FILE_SEP + "test-module-dep" + FILE_SEP + "target" + FILE_SEP + "mymodule.jar"),
        new File("src" + FILE_SEP + "test" + FILE_SEP + "poms" + FILE_SEP + "test-module-dep.xml")
    );
  }

  private File createMyModuleRepository(String repoPath, File jarFile, File pomFile) throws Exception {

    // Create our test repo
    File testRepo = new File("target" + FILE_SEP + repoPath);
    assertFalse("Repository " + testRepo.getAbsolutePath() + " should not exists", testRepo.exists());
    AetherHelper testHelper = new AetherHelper(testRepo.getAbsolutePath());

    // Install my:module:jar:1.0
    testHelper.installArtifact("my", "module", "1.0", jarFile, pomFile);

    // Resolve all the dependencies of vertx-core we need from the local repository
    // and install them in the test repo
    String localRepository = System.getProperty("localRepository");
    AetherHelper localHelper = new AetherHelper(localRepository);
    for (Artifact dependency : localHelper.getDependencies("io.vertx", "vertx-core", "jar", "3.0.0-SNAPSHOT")) {
      String path = dependency.getFile().getPath();
      testHelper.installArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
          dependency.getFile(), new File(path.substring(0, path.length() - 3) + "pom"));
    }

    // Make sure we have something
    assertTrue(testRepo.exists());
    assertTrue(testRepo.isDirectory());
    return testRepo;
  }

  private void configureRepo(File localRepo, String remoteRepo) {
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
}
