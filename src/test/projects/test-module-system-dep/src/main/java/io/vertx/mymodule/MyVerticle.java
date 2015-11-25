package io.vertx.mymodule;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

import javax.portlet.Portlet;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MyVerticle extends AbstractVerticle {

  Portlet portlet = null;

  @Override
  public void start() throws Exception {
    if (Vertx.currentContext().config().getBoolean("loaded_globally")) {
      if (Portlet.class.getClassLoader() == MyVerticle.class.getClassLoader()) {
        throw new Exception("System dependency not loaded by the correct loader");
      }
    } else {
      if (Portlet.class.getClassLoader() != MyVerticle.class.getClassLoader()) {
        throw new Exception("System dependency not loaded by the correct loader");
      }
    }
  }

  @Override
  public void stop() throws Exception {
  }
}
