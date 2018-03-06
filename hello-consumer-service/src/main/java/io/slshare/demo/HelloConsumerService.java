package io.slshare.demo;

import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.servicediscovery.ServiceDiscovery;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import lombok.val;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.common.RedisServiceInfo;


public class HelloConsumerService extends AbstractVerticle {

    private static final String serviceDiscoveryKey = "ServiceDiscovery";

    private static final String redisServiceName = "my-redis";

    private static final String helloServiceName = "hello-service";

    private CloudFactory cloudFactory;

    private Single<ServiceDiscovery> serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) {
        cloudFactory = new CloudFactory();

        serviceDiscovery = serviceDiscovery().cache();

        vertx.createHttpServer()
                .requestHandler(router()::accept)
                .rxListen(8080)
                .subscribe(s -> startFuture.complete(), startFuture::fail);
    }

    private void helloHandler(RoutingContext rc) {

        serviceDiscovery
                .flatMap(HelloConsumerService::webClient)
                .flatMap(client -> {
                    val request1 = client.get("/Luke").as(BodyCodec.jsonObject());
                    val request2 = client.get("/Leia").as(BodyCodec.jsonObject());

                    val s1 = request1.rxSend();
                    val s2 = request2.rxSend();

                    return Single.zip(s1, s2, (luke, leia) ->
                            new JsonObject()
                                    .put("luke", luke.body().getString("message"))
                                    .put("leia", leia.body().getString("message")));
                })
                .subscribe(
                        x -> rc.response().end(x.encode()),
                        t -> rc.response().end(t.toString())
                );
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

    private Router router() {
        val router = Router.router(vertx);
        router.get("/").handler(rc -> rc.response().end("ok"));
        router.get("/hello").handler(this::helloHandler);

        return router;
    }

    private static Single<WebClient> webClient(ServiceDiscovery serviceDiscovery) {
        return HttpEndpoint.rxGetWebClient(
                serviceDiscovery,
                record -> record.getName().equalsIgnoreCase(helloServiceName)
        );
    }
}
