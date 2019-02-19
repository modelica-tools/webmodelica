package webmodelica.controllers

import webmodelica.models._
import webmodelica.models.config._
import io.finch._
import io.finch.syntax._
import io.finch.circe._
import io.circe._
import io.circe.generic.auto._
import io.circe.generic.semiauto._

class FInfoController(config: WMConfig)
    extends FinchApi {
  import buildinfo.BuildInfo

  val info:Endpoint[Infos] = get("info") {
    Ok(Infos(
      config,
      BuildInfo.name,
      BuildInfo.version,
      BuildInfo.license,
      BuildInfo.commit
    ))
  }

  val api = info
}
