package io.vertx.mymodule;

import org.junit.jupiter.api.Test;
import io.vertx.core.AbstractVerticle;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MyVerticleA extends AbstractVerticle {

  Test map = null;

  @Override
  public void start() throws Exception {
    if (Test.class.getClassLoader() != MyVerticleA.class.getClassLoader().getParent()) {
      throw new Exception("Dependency not loaded by the correct loader");
    }
  }

  @Override
  public void stop() throws Exception {
  }
}
