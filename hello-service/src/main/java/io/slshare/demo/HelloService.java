package io.slshare.demo;

import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import lombok.val;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.common.RedisServiceInfo;

import java.util.List;
import java.util.Optional;

public class HelloService extends AbstractVerticle {

    private static final String serviceDiscoveryKey = "ServiceDiscovery";

    private static final String redisServiceName = "my-redis";

    private static final String serviceName = "hello-service";

    private CloudFactory cloudFactory;

    @Override
    public void start(Future<Void> startFuture) {
        cloudFactory = new CloudFactory();

        val server = vertx.createHttpServer()
                .requestHandler(router()::accept)
                .rxListen(8080);

        Single.zip(
                serviceDiscovery(),
                server,
                (serviceDiscovery, httpServer) -> {
                    val record = serviceUri()
                            .<Record>map(u -> HttpEndpoint.createRecord(serviceName, u, 80, "/"));

                    return record
                            .map(serviceDiscovery::rxPublish)
                            .orElse(Single.error(new Exception("Invalid external service url!")));
                })
                .flatMap(x -> x)
                .subscribe(
                        r -> {
                            System.out.println("Service successfully registered.");
                            startFuture.complete();
                        },
                        startFuture::fail
                );
    }

    private void helloHandler(RoutingContext rc) {
        String message = "Hello";
        if (rc.pathParam("name") != null) {
            message += " " + rc.pathParam("name") + " from the vert.x server!";
        }
        val json = new JsonObject().put("message", message);
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                .end(json.encode());
    }

    private Router router() {
        val router = Router.router(vertx);
        router.get("/").handler(this::helloHandler);
        router.get("/:name").handler(this::helloHandler);

        return router;
    }

    private Single<ServiceDiscovery> serviceDiscovery() {
        val redisInfo = (RedisServiceInfo) cloudFactory.getCloud().getServiceInfo(redisServiceName);

        val serviceDiscoveryOptions = new ServiceDiscoveryOptions()
                .setBackendConfiguration(
                        new JsonObject()
                                .put("host", redisInfo.getHost())
                                .put("port", redisInfo.getPort())
                                .put("auth", redisInfo.getPassword())
                                .put("key", serviceDiscoveryKey)
                );

        return Single.create(emitter ->
                ServiceDiscovery.create(vertx, serviceDiscoveryOptions, emitter::onSuccess)
        );
    }

    @SuppressWarnings("unchecked")
    private Optional<String> serviceUri() {
        return Optional.ofNullable(
                cloudFactory.getCloud()
                        .getApplicationInstanceInfo()
                        .getProperties()
                        .get("application_uris"))
                .filter(u -> u instanceof List && !((List) u).isEmpty())
                .map(u -> ((List<String>)u).get(0));
    }
}

