package org.jobrunr.tests.e2e;

import org.jobrunr.storage.StorageProvider;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticSearchGsonE2ETest extends AbstractE2EGsonTest {

    private static final Network network = Network.newNetwork();

    @Container
    private static final ElasticsearchContainer elasticSearchContainer = new ElasticsearchContainer("elasticsearch:7.9.1")
            .withNetwork(network)
            .withNetworkAliases("elasticsearch")
            .withExposedPorts(9200);

    @Container
    private static final ElasticSearchGsonBackgroundJobContainer backgroundJobServer = new ElasticSearchGsonBackgroundJobContainer(elasticSearchContainer, network);

    @Override
    protected StorageProvider getStorageProviderForClient() {
        return backgroundJobServer.getStorageProviderForClient();
    }

    @Override
    protected AbstractBackgroundJobContainer backgroundJobServer() {
        return backgroundJobServer;
    }
}
