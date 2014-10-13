package io.vertx.maven.modules;

import io.vertx.maven.MavenVerticle;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FactoryTest extends VertxTestBase {

  @Test
  public void testDeploy() throws Exception {
    vertx.deployVerticle("maven:my:module:1.0", res -> {
      assertTrue(res.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testStartsOK() throws Exception {
    vertx.deployVerticle("maven:my:module:1.0");
    vertx.eventBus().localConsumer("mymodule").handler(message -> testComplete());
    await();
  }

  @Test
  public void testNonExistent() throws Exception {
    vertx.deployVerticle("maven:module:doesnt:exist", res -> {
      assertFalse(res.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testInvalidName() throws Exception {
    vertx.deployVerticle("maven:uhqeduhqewdhuquhd", res -> {
      assertFalse(res.succeeded());
      testComplete();
    });
    await();
  }

  // Exists in Maven but not deployable
  @Test
  public void testNotDeployable() throws Exception {
    vertx.deployVerticle("maven:io.vertx:vertx-core:2.1.2", res -> {
      assertFalse(res.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testSysPropsGood() throws Exception {
    String fileSep = System.getProperty("file.separator");
    System.setProperty(MavenVerticle.LOCAL_REPO_SYS_PROP, System.getProperty("user.home") + fileSep + ".m2" + fileSep + "repository");
    System.setProperty(MavenVerticle.REMOTE_REPOS_SYS_PROP, "http://central.maven.org/maven2/ http://oss.sonatype.org/content/repositories/snapshots/");
    vertx.deployVerticle("maven:my:module:1.0", res -> {
      assertTrue(res.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testSysPropsBad() throws Exception {
    System.setProperty(MavenVerticle.LOCAL_REPO_SYS_PROP, "qiwhdiqowd");
    System.setProperty(MavenVerticle.REMOTE_REPOS_SYS_PROP, "yqgwduyqwd");
    vertx.deployVerticle("maven:my:module:1.0", res -> {
      assertTrue(res.failed());
      testComplete();
    });
    await();
  }

}
