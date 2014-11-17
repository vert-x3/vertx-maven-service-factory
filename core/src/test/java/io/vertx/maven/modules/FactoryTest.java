package io.vertx.maven.modules;

import io.vertx.core.Vertx;
import io.vertx.maven.MavenVerticleFactory;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FactoryTest extends VertxTestBase {

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
  public void testSysPropsGood() throws Exception {
    String fileSep = System.getProperty("file.separator");
    System.setProperty(MavenVerticleFactory.LOCAL_REPO_SYS_PROP, System.getProperty("user.home") + fileSep + ".m2" + fileSep + "repository");
    System.setProperty(MavenVerticleFactory.REMOTE_REPOS_SYS_PROP, "http://central.maven.org/maven2/ http://oss.sonatype.org/content/repositories/snapshots/");
    vertx.close();
    vertx = Vertx.vertx();
    vertx.deployVerticle("service:my:module:1.0", res -> {
      assertTrue(res.succeeded());
      testComplete();
    });
    await();
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

}
