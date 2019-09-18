/*
 * Copyright (c) 2019-Today N. Justus
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package webmodelica.services

import java.nio.file.{Path, Paths}

import com.twitter.finagle.Service
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Method, Request, Response, Status}
import java.net.{URI, URL}

import com.twitter.util.{Future, Promise}

import scala.concurrent.{Future => SFuture, Promise => SPromise}
import scala.concurrent.ExecutionContext.Implicits.global
import com.twitter.io.Buf
import featherbed._

import scala.reflect.Manifest
import webmodelica.models.errors.{
  MopeServiceError,
  SimulationSetupError,
  SimulationNotFinished
}
import webmodelica.models.mope._
import webmodelica.models.mope.requests._
import webmodelica.models.mope.responses._
import webmodelica.conversions.futures._
import webmodelica.models.JsonSupport._

trait MopeService {
  this: com.typesafe.scalalogging.LazyLogging =>

  def pathMapper: MopeService.PathMapper
  def clientProvider(): featherbed.Client

  private def withClient[A](fn: featherbed.Client => Future[A]): Future[A] = {
    logger.debug("Using new client")
    val cl = clientProvider()
    fn(cl).ensure {
      logger.debug("releasing client")
      cl.close()
    }
  }

  private def transformError[A](msg: => String, ignore:Set[Class[_]]=Set.empty)(f: => Future[A]): Future[A] = {
    import com.twitter.util.{Return, Throw}
    f.transform {
      case Return(r) =>
        logger.debug(s"$msg returned $r")
        Future.value(r)
      case Throw(e@request.ErrorResponse(req,resp)) =>
        logger.error(s"http error while $msg: ${e.getMessage}", e)
        Future.exception(MopeServiceError(s"Error response $resp to request $req"))
      case Throw(e:MopeServiceError) if !ignore(e.getClass) =>
        logger.error(s"mope error while $msg: ${e.getMessage}", e)
        Future.exception(e)
      case Throw(e) if !ignore(e.getClass) =>
        logger.error(s"unknown error while $msg", e)
        Future.exception(e)
      case Throw(e) => Future.exception(e)
    }
  }

  private lazy val projIdPromise: Promise[Int] = new Promise[Int]()
  def projectId: Future[Int] = projIdPromise

  import featherbed.circe._
  import io.circe.generic.auto._

  def connect():Future[Int] = {
    val path = pathMapper.projectDirectory
    val descr = ProjectDescription(path.toString)

    transformError(s"connecting with $descr") {
      withClient { client =>
        val req = client.post("connect")
          .withContent(descr, "application/json")
          .accept("application/json")

        req.send[Int]().map { id =>
          logger.info(s"registered id $id")
          projIdPromise setValue id
          id
        }
      }
    }
  }

  def compile(path:Path): Future[Seq[CompilerError]] = {
    val fp = FilePath(pathMapper.toBindPath(path).toString)
    projectId.flatMap { id =>
      transformError(s"compiling $fp") {
        withClient { client =>
          val req = client.post(s"project/$id/compile")
            .withContent(fp, "application/json")
            .accept("application/json")
          req.send[Seq[CompilerError]]()
            .map { xs =>
              logger.debug(s"compiling returned $xs")
              xs.map { error => error.copy(file = pathMapper.relativize(error.file).toString) }
            }
        }
      }
    }
  }

  def complete(c:Complete): Future[Seq[Suggestion]] = {
    val cNew = c.copy(file=pathMapper.toBindPath(Paths.get(c.file)).toString)
    projectId.flatMap { id =>
      transformError(s"complete $cNew") {
        withClient { client =>
          val req = client.post(s"project/$id/completion")
            .withContent(cNew, "application/json")
            .accept("application/json")
          req.send[Seq[Suggestion]]()
        }
      }
    }
  }


  def simulate(simParam:SimulateRequest): Future[URI] = {
    scala.concurrent.Future.fromTry(simParam.convertStepSize).asTwitter.flatMap { sim =>
      logger.debug(s"converting stepSize returned $sim")
      projectId.flatMap { id =>
        transformError(s"simulating project:$id", Set(classOf[SimulationSetupError])) {
          withClient { client =>
            val req = client.post(s"project/$id/simulate")
              .withContent(sim, "application/json")
            req.send[Response]()
              .map(r => r.headerMap.get("Location"))
              .flatMap {
                case Some(l) => Future.value(new URI(l))
                case None =>
                  logger.error(s"/simulate $req didn't return a Location header!")
                  Future.exception(MopeServiceError(s"POST /simulate didn't return a Location header!"))
              }
              .handle {
                case request.ErrorResponse(req, resp) if resp.status == Status.BadRequest =>
                  throw SimulationSetupError(resp.contentString)
              }
          }
        }
      }
    }
  }

  def simulationResults(addr:URI): Future[SimulationResult] = {
    projectId.flatMap {id =>
      transformError("retrieving simulation results", Set(SimulationNotFinished.getClass, classOf[SimulationSetupError])) {
        withClient { client =>
          val req = client.get(addr.toString)
            .accept("application/json")
          req.send[SimulationResult]()
            .handle {
              case request.ErrorResponse(req, resp) if resp.status == Status.Conflict =>
                throw SimulationNotFinished
              case request.ErrorResponse(req, resp) if resp.status == Status.BadRequest =>
                throw SimulationSetupError(resp.contentString)
            }
        }
      }
    }
  }

  def disconnect(): Future[Unit] = {
    //POST /mope/project/:id/disconnect
    projectId.flatMap { id =>
      withClient{ client =>
        val req = client.post(s"project/$id/disconnect")
          .withContent((), "application/json")
        transformError("disconnecting") {
          req.send[Response]()
            .unit
        }
      }
    }
  }
}

object MopeService {
  /** A PathMapper that converts between 2 root directories. */
  trait PathMapper {
    /** Strip the root directory from this path by relativizing 'p' against hostPath or bindPath depending on which one is 'p's root. */
    def relativize(p:Path): Path
    def relativize(p:String): Path = relativize(Paths.get(p))
    /** Converts the given path to a bind path, that is: to a path inside of MoPE. */
    def toBindPath(p:Path): Path
    /** Converts the given path to a host path, that is: to a path inside of Webmodelica. */
    def toHostPath(p:Path): Path
    /** The project directory (normally the bindPath) as path inside of MoPE. */
    def projectDirectory: Path
  }
  def pathMapper(hostPath:Path, bindPath:Path): PathMapper = new PathMapper() {
    private val stripPath = (from:Path, other:Path) => from.subpath(other.getNameCount, from.getNameCount)
    override def projectDirectory: Path = bindPath
    override def relativize(p:Path): Path =
      if (p.startsWith(hostPath)) hostPath.relativize(p)
      else bindPath.relativize(p)
    override def toBindPath(p:Path): Path = {
      if(p.isAbsolute) bindPath.resolve(stripPath(p, hostPath))
      else bindPath.resolve(p)
    }
    override def toHostPath(p:Path): Path =
      if(p.isAbsolute) hostPath.resolve(stripPath(p, bindPath))
      else hostPath.resolve(p)
    override def toString: String = s"PathMapper(host:$hostPath, bind:$bindPath)"
  }
}
