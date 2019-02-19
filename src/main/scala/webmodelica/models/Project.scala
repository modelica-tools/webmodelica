package webmodelica.models

import org.mongodb.scala.bson.BsonObjectId
import io.scalaland.chimney.dsl._

import io.circe.generic.JsonCodec

case class Project(
  _id: BsonObjectId,
  owner: String,
  name: String,
)

@JsonCodec
case class ProjectRequest(
  owner: String,
  name: String)

@JsonCodec
case class JSProject(
  id: String,
  owner: String,
  name: String)

object JSProject {
  def apply(p:Project): JSProject = {
    require(p != null, "project can't be null!")
    p.into[JSProject].withFieldComputed(_.id, _._id.getValue.toHexString).transform
  }
}

object Project {
  def apply(request: ProjectRequest): Project =
    request.into[Project].withFieldComputed(_._id, _ => BsonObjectId()).transform
}
