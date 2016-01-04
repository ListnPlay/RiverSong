package com.featurefm.riversong.metrics


import java.lang.reflect.Modifier

import akka.actor.ActorSystem
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
        ctx.method.getName != "getHealth") {
      if (ctx.method.getReturnType == classOf[Future[_]])
        timeEventually(name) { ctx.proceed().asInstanceOf[Future[_]] }
      else
        time(name) { ctx.proceed() }
    } else ctx.proceed()
  }
}
