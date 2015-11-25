package io.vertx.maven.modules;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;

import java.io.PrintWriter;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class RepositoryTracer implements RepositoryListener {
  
  private final PrintWriter out;

  public RepositoryTracer(PrintWriter out) {
    this.out = out;
  }

  @Override
  public void artifactDescriptorInvalid(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactDescriptorMissing(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataInvalid(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactResolving(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactResolved(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataResolving(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataResolved(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactDownloading(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactDownloaded(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataDownloading(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataDownloaded(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactInstalling(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactInstalled(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataInstalling(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataInstalled(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactDeploying(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void artifactDeployed(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataDeploying(RepositoryEvent event) {
    out.println(event);
  }

  @Override
  public void metadataDeployed(RepositoryEvent event) {
    out.println(event);
  }
}
