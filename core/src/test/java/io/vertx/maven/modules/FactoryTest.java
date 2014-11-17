package io.vertx.maven.modules;

import com.google.common.io.Files;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.maven.MavenVerticleFactory;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FactoryTest extends VertxTestBase {

  @Override
  public void setUp() throws Exception {
    System.setProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP, assertTestRepo().getAbsolutePath());
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
    vertx.deployVerticle("service:my:module:1.0", onSuccess(res -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testStartsOK() throws Exception {
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
    File testRepo = assertTestRepo();
    File emptyRepo = Files.createTempDir();
    emptyRepo.deleteOnExit();
    testConfiguredRepo(testRepo, emptyRepo);
  }

  @Test
  public void testConfiguredResolveFromRemoteRepository() throws Exception {
    File testRepo = assertTestRepo();
    File emptyRepo = Files.createTempDir();
    emptyRepo.deleteOnExit();
    testConfiguredRepo(emptyRepo, testRepo);
  }

  private void testConfiguredRepo(File localRepo, File remoteRepo) throws Exception {
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
      System.setProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP, localRepo.getAbsolutePath());
      System.setProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP, "http://localhost:8080/");
      vertx.close();
      vertx = Vertx.vertx();
      vertx.deployVerticle("service:my:module:1.0", res -> {
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

  private File assertTestRepo() {
    String fileSep = System.getProperty("file.separator");
    String localRepo = System.getProperty("basedir") + fileSep + ".." + fileSep + "test-module" + fileSep + "target" + fileSep + "repo";
    File localRepoFile = new File(localRepo);
    assertTrue(localRepoFile.exists());
    assertTrue(localRepoFile.isDirectory());
    return localRepoFile;
  }
}
