package io.vertx.mymodule;

import io.vertx.core.AbstractVerticle;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MyVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    System.out.println("DEPLOYED!!!");
  }
}
