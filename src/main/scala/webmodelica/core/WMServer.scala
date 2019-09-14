/*
 * Copyright (c) 2019-Today N. Justus
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package webmodelica.core

import webmodelica.controllers._
import webmodelica.models._
import com.typesafe.scalalogging.LazyLogging
import com.softwaremill.macwire._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.Http

object WMServerMain extends WMServer

class WMServer extends LazyLogging
    with de.heikoseeberger.akkahttpcirce.FailFastCirceSupport {
  def main(mainArgs: Array[String]) = {
    val module = new WebmodelicaModule {
      override def arguments:Seq[String] = mainArgs
    }
    println(module.config)
    bootstrap(module)
  }

  val exceptionMapper: ExceptionHandler = ExceptionHandler {
    case e:IllegalArgumentException => complete(StatusCodes.BadRequest -> Seq(e.getMessage))
    case e:com.twitter.finatra.http.exceptions.HttpException =>
      val code = StatusCodes.getForKey(e.statusCode.code).getOrElse(throw new RuntimeException(s"there is no StatusCode for $e available!"))
      complete(code -> e.errors)
    case e:errors.WMException =>
      val code = StatusCodes.getForKey(e.status.code).getOrElse(throw new RuntimeException(s"there is no StatusCode for $e available!"))
      complete(code -> Seq(e.getMessage))
  }

  def bootstrap(module:WebmodelicaModule): Unit = {
    import module._
    // val ctrl = AkkaController.combineAll(Seq(wire[AkkaProjectController], AkkaInfoController))
    val ctrl = wire[AkkaProjectController] || wire[AkkaSessionController] || AkkaInfoController
    val routes:Route = handleExceptions(exceptionMapper) {
      pathPrefix("api"/"v1"/"webmodelica") { ctrl.routes }
    }
    val bindingFuture = Http().bindAndHandle(routes, args.interface(), args.port())

    val token = tokenGenerator.newToken(User("nico", "nico@xample.org", None, None, hashedPassword="abcdef12345"))
    logger.debug(s"test token is $token")

    bindingFuture onComplete {
      case scala.util.Success(_) =>
        logger.info("Server running at {}:{}", args.interface(), args.port())
      case scala.util.Failure(ex) =>
        logger.error("Failed to start server at {}:{} - {}", args.interface(), args.port(), ex.getMessage)
        actorSystem.terminate()
    }

    scala.sys.addShutdownHook {
      bindingFuture
        .flatMap(_.unbind())
        .onComplete { _ =>
          actorSystem.terminate()
        }
      Await.ready(actorSystem.whenTerminated, 60.seconds)
    }
  }
}
