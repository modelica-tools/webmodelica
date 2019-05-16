package webmodelica.controllers

import com.google.inject.Inject
import com.twitter.finagle.{Service, SimpleFilter, http}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.util.Future
import webmodelica.constants
import webmodelica.services.{TokenGenerator, TokenValidator}
import webmodelica.stores.UserStore
import webmodelica.models.{User, errors}


class JwtFilter@Inject()(gen:TokenGenerator, store:UserStore)(validator:TokenValidator=gen) extends SimpleFilter[http.Request, http.Response]
  with com.twitter.inject.Logging {

  info(s"jwt filter init with generator: $gen, validator: $validator")

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val headerField = request.headerMap.get(constants.authenticationHeader).orElse(request.headerMap.get(constants.authorizationHeader))
    lazy val cookie = request.cookies.get(constants.authenticationHeader).orElse(request.cookies.get(constants.authorizationHeader)).map(_.value)
    val resultOpt = headerField.orElse(cookie).filter(validator.isValid).map { token =>
      val userF = validator.decode(token).flatMap(t => store.findBy(t.username))
      //explictly set the Authorization header because it could be inside of a cookie
      //which isn't used when extracting user informations
      request.authorization = token
      val respF = service(request)
      for {
        response <- respF
        user <- userF.flatMap(errors.notFoundExc("web-token contains invalid user informations!"))
        token = gen.newToken(user)
      } yield {
        response.authorization = token
        response
      }
    }
    resultOpt.getOrElse {
        warn(s"provided token invalid!")
        val res = Response()
        res.status = Status.Unauthorized
        res.contentString = "Invalid web-token!"
        Future.value(res)
    }
  }
}
