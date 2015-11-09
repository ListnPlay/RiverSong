package com.featurefm.riversong.health

import akka.actor._
import com.featurefm.riversong.Json4sProtocol

import scala.collection.mutable

class HealthCheckExtension(system: ExtendedActorSystem) extends Extension {


  implicit val formats = Json4sProtocol.json4sJacksonFormats

  /** The application wide registry. */
  private val registry: mutable.Buffer[HealthCheck] = mutable.Buffer()
                      //new com.codahale.metrics.health.HealthCheckRegistry()
  /**
   * Get a copy of the registered `HealthCheck` definitions
   * @return
   */
  def getChecks: Seq[HealthCheck] = registry.toSeq

  /**
   * Add a health check to the registry
   * @param check
   */
  def addCheck(check: HealthCheck): Unit = registry.append(check)
}

object Health extends ExtensionId[HealthCheckExtension] with ExtensionIdProvider {

  //The lookup method is required by ExtensionIdProvider,
  // so we return ourselves here, this allows us
  // to configure our extension to be loaded when
  // the ActorSystem starts up
  override def lookup() = Health

  //This method will be called by Akka
  // to instantiate our Extension
  override def createExtension(system: ExtendedActorSystem) = new HealthCheckExtension(system)

  def apply()(implicit system: ActorSystem): HealthCheckExtension =
    system.registerExtension(this)
}
