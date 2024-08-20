/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.internal.async.SingleResultCallback;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DefaultConnectionPoolInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.internal.connection.DefaultConnectionPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // instrumentation needed since 4.3.0-beta3
    transformer.applyAdviceToMethod(
        named("getAsync")
            .and(takesArgument(0, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$SingleResultCallbackAdvice");

    // instrumentation needed since 4.10.x
    transformer.applyAdviceToMethod(
        named("getAsync")
            .and(takesArgument(0, named("com.mongodb.internal.connection.OperationContext")))
            .and(takesArgument(1, named("com.mongodb.internal.async.SingleResultCallback"))),
        this.getClass().getName() + "$OperationContextWithSingleResultCallbackAdvice");
  }

  @SuppressWarnings("unused")
  public static class SingleResultCallbackAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 0, readOnly = false) SingleResultCallback<Object> callback) {
      callback = new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }

  @SuppressWarnings("unused")
  public static class OperationContextWithSingleResultCallbackAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapCallback(
        @Advice.Argument(value = 1, readOnly = false) SingleResultCallback<Object> callback) {
      callback = new SingleResultCallbackWrapper(Java8BytecodeBridge.currentContext(), callback);
    }
  }
}
