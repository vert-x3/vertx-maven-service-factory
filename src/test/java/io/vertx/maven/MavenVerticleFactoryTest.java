package io.vertx.maven;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * @author <a href="mailto:john.warner@ef.com">John Warner</a>
 */
public class MavenVerticleFactoryTest {
    @Test
    public void testWithoutSystemProperty() throws Exception {
        // Ensure the property is empty
        System.clearProperty(MavenVerticleFactory.REMOTE_SNAPSHOT_POLICY_SYS_PROP);

        MavenVerticleFactory mavenVerticleFactory = new MavenVerticleFactory();
        RemoteRepository.Builder builder = new RemoteRepository.Builder("test", "default", "http://test.com");

        mavenVerticleFactory.customizeRemoteRepoBuilder(builder);

        assertEquals(RepositoryPolicy.UPDATE_POLICY_DAILY, builder.build().getPolicy(true).getUpdatePolicy());
    }

    @Test
    public void testWithSystemProperty() throws Exception {
        // Set the property to update daily
        System.setProperty(MavenVerticleFactory.REMOTE_SNAPSHOT_POLICY_SYS_PROP, RepositoryPolicy.UPDATE_POLICY_ALWAYS);

        MavenVerticleFactory mavenVerticleFactory = new MavenVerticleFactory();
        RemoteRepository.Builder builder = new RemoteRepository.Builder("test", "default", "http://test.com");

        mavenVerticleFactory.customizeRemoteRepoBuilder(builder);

        assertEquals(RepositoryPolicy.UPDATE_POLICY_ALWAYS, builder.build().getPolicy(true).getUpdatePolicy());
    }
}
