package webmodelica.controllers

import com.google.inject.Inject
import com.twitter.util.Future
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import org.mongodb.scala.bson.BsonObjectId
import webmodelica.models.{JSProject, Project, ProjectRequest, errors}
import webmodelica.models.config.WMConfig
import webmodelica.models.errors.ResourceUsernameError
import webmodelica.services.{
  TokenGenerator,
  UserToken
}
import webmodelica.stores.{
  ProjectStore,
  UserStore
}

class ProjectController@Inject()(
  store:ProjectStore,
  prefix:webmodelica.ApiPrefix,
  override val userStore: UserStore,
  override val gen: TokenGenerator)
    extends Controller
    with UserExtractor {

  filter[JwtFilter]
    .prefix(prefix.p) {
      post("/projects") { project: ProjectRequest =>
        logger.debug(s"got project $project")
        extractToken(project.request).flatMap {
          case token if token.username == project.owner =>
            val newProj = Project(project)
            store.add(newProj).map(_ => JSProject(newProj))
          case _ => Future.value(response.forbidden.body(ResourceUsernameError("project").getMessage))
        }
      }

      get("/projects/:id") { requ: Request =>
        val id = requ.getParam("id")
          extractToken(requ).flatMap { case UserToken(username,_,_) =>
            store.findBy(BsonObjectId(id), username)
              .flatMap(errors.notFoundExc(s"project with $id not found!"))
              .map(JSProject.apply)
          }
      }

      get("/projects") { requ: Request =>
        extractToken(requ).flatMap { case UserToken(username,_,_) =>
          store.byUsername(username).map(_.map(JSProject.apply))
        }
      }
   }
}
