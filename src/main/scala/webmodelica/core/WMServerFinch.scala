package webmodelica.core

import io.finch._
import io.finch.circe._
import io.finch.syntax._
import io.circe.generic.auto._

import com.twitter.finagle.param.Stats
import com.twitter.server.TwitterServer
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Await
import webmodelica.constants.confDefault
import org.slf4j.LoggerFactory;

object WMServerFinchMain extends WMServerFinch

class WMServerFinch
    extends TwitterServer {
  val env = flag(name="env", default="development", help="environment to use")
  val configFile = flag(name="configFile", default=confDefault, help="the config file to use")
  val httpPort = flag(name="http.port", default=":8888", help="binding port to use")

  val api: Endpoint[String] = get("hello") { Ok("Hello, World!") }

  def main(): Unit = {
    val log = LoggerFactory.getLogger(this.getClass)
    val server = Http.server
      .serve(httpPort(), api.toServiceAs[Text.Plain])

    log.info(s"Serving in ${env()} on ${server.boundAddress}")

    onExit { server.close() }

    Await.ready(adminHttpServer)
  }
}
