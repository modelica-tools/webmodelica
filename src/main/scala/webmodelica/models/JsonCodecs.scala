package webmodelica.models

import java.nio.file.{
  Path,
  Paths
}
import io.circe.{ Decoder, Encoder }
import io.circe._
import io.circe.generic.auto._
import io.circe.generic.semiauto._

import webmodelica.controllers.Infos

trait JsonCodecs {
  implicit val pathEncoder:Encoder[Path] = Encoder.encodeString.contramap[Path](_.toString)
  implicit val pathDecoder:Decoder[Path] = Decoder.decodeString.map(s => Paths.get(s))
  implicit val infoEncoder:Encoder[Infos] = deriveEncoder
  implicit val infoDecoder:Decoder[Infos] = deriveDecoder
}
