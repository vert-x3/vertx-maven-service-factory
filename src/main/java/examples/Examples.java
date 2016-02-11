package examples;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.maven.MavenVerticleFactory;
import io.vertx.maven.Resolver;
import io.vertx.maven.ResolverOptions;

import java.util.List;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Examples {

  public void example1(Vertx vertx, DeploymentOptions options) {
    vertx.deployVerticle("maven:com.mycompany:my-artifact:1.2::my-service", options);
  }

  public void example2(Vertx vertx, DeploymentOptions options) {
    vertx.deployVerticle("maven:com.mycompany:my-artifact:1.2", options);
  }

  public void example3(Vertx vertx) {
    vertx.registerVerticleFactory(new MavenVerticleFactory());
  }

  public void example4(Vertx vertx, String local, List<String> remotes) {
    vertx.registerVerticleFactory(new MavenVerticleFactory(
        new ResolverOptions()
            .setLocalRepository(local)
            .setRemoteRepositories(remotes))
    );
  }

  public void example5() {
    System.setProperty("javax.net.ssl.trustStore", "/my_trust_store.jks");
    System.setProperty("javax.net.ssl.trustStorePassword", "somepassword");
  }

  public void example6(Vertx vertx, String proxy) {
    vertx.registerVerticleFactory(new MavenVerticleFactory(
        new ResolverOptions().setHttpProxy(proxy))
    );
  }

  public void example7(Vertx vertx) {
    vertx.registerVerticleFactory(new MavenVerticleFactory(
        new ResolverOptions().setRemoteSnapshotPolicy("never"))
    );
  }

  public void example8(Vertx vertx, Resolver myResolver) {
    vertx.registerVerticleFactory(new MavenVerticleFactory(myResolver)
    );
  }
}
