package webmodelica.controllers

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.google.inject.Inject
import webmodelica.models.config._

case class Infos(
  appName:String,
  version:String,
  license:String,
  commitHash:String,
)

class InfoController @Inject()(config: WMConfig, prefix:webmodelica.ApiPrefix)
    extends Controller {

  import buildinfo.BuildInfo

  prefix(prefix.p) {
    get("/info") { _:Request =>
      Infos(
        BuildInfo.name,
        BuildInfo.version,
        BuildInfo.license,
        BuildInfo.commit
      )
    }
  }
}
