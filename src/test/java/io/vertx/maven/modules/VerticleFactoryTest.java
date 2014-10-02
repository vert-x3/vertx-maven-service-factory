package io.vertx.maven.modules;

import io.vertx.core.Vertx;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VerticleFactoryTest {

  @Test
  public void testFoo() throws Exception {

    Vertx vertx = Vertx.vertx();
    vertx.registerVerticleFactory(new MavenModuleFactory());
    vertx.deployVerticle("maven:my:module:1.0");
    CountDownLatch latch = new CountDownLatch(1);
    vertx.eventBus().localConsumer("mymodule").handler(message -> latch.countDown());
    latch.await();
  }

}
