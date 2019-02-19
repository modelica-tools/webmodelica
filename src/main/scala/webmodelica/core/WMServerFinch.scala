package webmodelica.core

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

  val self = this
  val module = new WMModule() {
    def environment:String = self.env()
    def configFile:String = self.configFile()
    override lazy val log = LoggerFactory.getLogger(self.getClass)
  }

  def main(): Unit = {
    import module.log

    import io.finch._
    import io.finch.circe._
    import io.finch.syntax._
    import io.circe.generic.auto._
    val api: Endpoint[String] = get("hello") { Ok("Hello, World!") }

    module.startup()
    val server = Http.server
      .serve(httpPort(), api.toServiceAs[Text.Plain])

    log.info(s"Serving in ${env()} on ${server.boundAddress}")

    onExit {
      server.close()
      module.shutdown()
    }

    Await.ready(adminHttpServer)
  }
}
