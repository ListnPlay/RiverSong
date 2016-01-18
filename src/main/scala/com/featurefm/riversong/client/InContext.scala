package com.featurefm.riversong.client

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.language.implicitConversions
import scala.util.Try

/**
  * Created by yardena on 1/18/16.
  */

trait InContext[T] {
  type Context = Map[String, Any]
  type Tuple = (T, Context)
  def context: Context
  def unwrap: T
  def toTuple: (T, Map[String, Any]) = (unwrap, context)
  def with_(key: String, value: Any): InContext[T]
  def without(key: String): InContext[T]
  def get[S](key: String): S = context(key).asInstanceOf[S]
}

object InContext {

  implicit def fromTuple(t: ResponseInContext#Tuple): ResponseInContext = WrappedResponse(t._1, t._2)
  implicit def fromReqTuple(t: RequestInContext#Tuple): RequestInContext = WrappedRequest(t._1, t._2) //probably not needed

  implicit def toTuple[T](x: InContext[T]): (T, Map[String, Any]) = x.toTuple

  abstract sealed class Just[A](r: A) extends InContext[A] {
    override val unwrap: A = r
    override val context: Map[String, Any] = Map()
    override def without(key: String): InContext[A] = this
  }
  implicit class JustRequest(r: HttpRequest) extends Just[HttpRequest](r) {
    override def with_(key: String, value: Any): RequestInContext = wrap(r, key -> value)
  }
  implicit class JustResponse(r: Try[HttpResponse]) extends Just[Try[HttpResponse]](r) {
    override def with_(key: String, value: Any): ResponseInContext = WrappedResponse(r, Map(key -> value))
  }

  case class WrappedRequest(unwrap: HttpRequest, context: Context) extends InContext[HttpRequest] {
    override def with_(key: String, value: Any): RequestInContext = copy(context = context + (key -> value))
    override def without(key: String): RequestInContext = copy(context = context - key)
  }
  case class WrappedResponse(unwrap: Try[HttpResponse], context: Map[String, Any]) extends InContext[Try[HttpResponse]] {
    override def with_(key: String, value: Any): ResponseInContext = copy(context = context + (key -> value))
    override def without(key: String): ResponseInContext = copy(context = context - key)
  }

  def wrap(req: HttpRequest, pairs: (String, Any)*): RequestInContext = WrappedRequest(req, pairs.toMap)
  def wrap(req: HttpRequest, context: Map[String, Any]): RequestInContext = WrappedRequest(req, context)

  implicit def unwrap[T](x: InContext[T]): T = x.unwrap

}
