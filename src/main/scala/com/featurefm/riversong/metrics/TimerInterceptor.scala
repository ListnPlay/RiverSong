package com.featurefm.riversong.metrics


import java.lang.reflect.Modifier

import akka.actor.ActorSystem
import com.featurefm.riversong.health.HealthCheck
import com.softwaremill.macwire.aop.{InvocationContext, ProxyingInterceptor}

import scala.concurrent.Future

/**
 * Created by yardena on 1/4/16.
 */
class TimerInterceptor(implicit val system: ActorSystem) extends ProxyingInterceptor with Instrumented {

  override def handle(ctx: InvocationContext): AnyRef = {
    def name = s"${ctx.method.getDeclaringClass.getSimpleName}.${ctx.method.getName}"

    import system.dispatcher

    if (Modifier.toString(ctx.method.getModifiers).indexOf("public") >= 0 &&
        !classOf[HealthCheck].getMethods.map(_.getName).contains(ctx.method.getName)) { //exclude HealthCheck methods
      // ctx.method.getName != "getHealth" && ctx.method.getName != "healthCheckName"
      if (ctx.method.getReturnType == classOf[Future[_]])
        timeEventually(name) { ctx.proceed().asInstanceOf[Future[_]] }
      else
        time(name) { ctx.proceed() }
    } else ctx.proceed()
  }
}
