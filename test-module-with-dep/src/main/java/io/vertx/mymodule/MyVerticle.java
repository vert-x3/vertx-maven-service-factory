package io.vertx.mymodule;

import io.vertx.core.AbstractVerticle;

import javax.portlet.Portlet;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MyVerticle extends AbstractVerticle {

  Portlet portlet = null;

  @Override
  public void start() throws Exception {
    vertx.eventBus().publish("mymodule", "whatever");
  }

  @Override
  public void stop() throws Exception {
    vertx.eventBus().publish("mymoduleStopped", "whatever");
  }
}
