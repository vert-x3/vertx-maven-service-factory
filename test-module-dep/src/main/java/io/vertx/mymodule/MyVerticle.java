package io.vertx.mymodule;

import com.google.common.collect.BiMap;
import io.vertx.core.AbstractVerticle;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MyVerticle extends AbstractVerticle {

  BiMap map = null;

  @Override
  public void start() throws Exception {
    if (BiMap.class.getClassLoader() != MyVerticle.class.getClassLoader()) {
      throw new Exception("Dependency not loaded by the correct loader");
    }
  }

  @Override
  public void stop() throws Exception {
  }
}
