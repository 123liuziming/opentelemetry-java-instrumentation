/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheshenyu.v2_4;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.shenyu.common.dto.MetaData;
import org.apache.shenyu.plugin.global.cache.MetaDataCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.netty.http.client.HttpClient;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    properties = {"shenyu.local.enabled=true", "spring.main.allow-bean-definition-overriding=true"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {ShenYuBootstrapApplication.class, ShenYuRouteTest.ForceNettyAutoConfiguration.class})
public class ShenYuRouteTest {

  private static final AttributeKey<String> META_ID_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.id");

  private static final AttributeKey<String> APP_NAME_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.app-name");

  private static final AttributeKey<String> CONTEXT_PATH_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.context-path");

  private static final AttributeKey<String> PATH_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.path");

  private static final AttributeKey<String> RPC_TYPE_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.rpc-type");

  private static final AttributeKey<String> SERVICE_NAME_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.service-name");

  private static final AttributeKey<String> METHOD_NAME_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.method-name");

  private static final AttributeKey<String> PARAMETER_TYPES_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.param-types");

  private static final AttributeKey<String> RPC_EXT_ATTRIBUTE =
      AttributeKey.stringKey("apache-shenyu.meta.rpc-ext");

  private static final AttributeKey<Boolean> META_ENABLED_ATTRIBUTE =
      AttributeKey.booleanKey("apache-shenyu.meta.enabled");

  @Value("${local.server.port}")
  private int port;

  @TestConfiguration
  static class ForceNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void beforeAll() {
    MetaDataCache.getInstance()
        .cache(
            new MetaData(
                "123",
                "test-shenyu",
                "/",
                "/a/b/c",
                "http",
                "shenyu-service",
                "hello",
                "string",
                "test-ext",
                true));
  }

  @Test
  void testUpdateRouter() {
    HttpClient httpClient = HttpClient.create();
    httpClient.get().uri("http://localhost:" + port + "/a/b/c").response().block();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT),
                span ->
                    span.hasName("GET /a/b/c")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(
                            equalTo(AttributeKey.stringKey("http.route"), "/a/b/c"),
                            equalTo(META_ID_ATTRIBUTE, "123"),
                            equalTo(META_ENABLED_ATTRIBUTE, true),
                            equalTo(METHOD_NAME_ATTRIBUTE, "hello"),
                            equalTo(PARAMETER_TYPES_ATTRIBUTE, "string"),
                            equalTo(PATH_ATTRIBUTE, "/a/b/c"),
                            equalTo(RPC_EXT_ATTRIBUTE, "test-ext"),
                            equalTo(RPC_TYPE_ATTRIBUTE, "http"),
                            equalTo(SERVICE_NAME_ATTRIBUTE, "shenyu-service"),
                            equalTo(APP_NAME_ATTRIBUTE, "test-shenyu"),
                            equalTo(CONTEXT_PATH_ATTRIBUTE, "/"))));
  }
}
