package webmodelica.controllers

import webmodelica.models.JsonCodecs
import org.slf4j.LoggerFactory;

trait FinchApi extends JsonCodecs {
  lazy val log = LoggerFactory.getLogger(this.getClass)
  // def routes[L<:shapeless.HList]: Endpoint[L]
}
