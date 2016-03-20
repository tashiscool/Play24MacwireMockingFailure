package com.softwaremill.play24.controllers

import com.softwaremill.play24.dao.CoffeeDao
import play.api.i18n.Lang
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{Future}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Results._

class CoffeeController(
  coffeeDao: CoffeeDao
)(implicit ec: SomeContextBuilder) extends AnotherController {

  def fetchAll() = DecoratedAction() { request =>
    coffeeDao.all.map { coffees =>
      Ok(Json.toJson(coffees))
    }
  }

  def priced(price: Double) = ResolvedDecoratedAction() { request =>
    coffeeDao.byPriceWithSuppliers(price).map { result =>
      Ok(Json.toJson(result.toMap))
    }
  }
}

trait ContextBuilder[U <: TraitLike] {
  def build(request: Request[AnyContent]): Future[Either[Result, RequestWithContext[U]]]
  def buildAuthenticated(request: Request[AnyContent]): Future[Either[Result, RequestWithResolvedContext[U]]]
}

trait TraitLike {
  def id: String
}

trait WithSessionId {
  self: RequestHeader =>
  lazy val sessionId = self.session.get("auth").getOrElse(java.util.UUID.randomUUID().toString)
}

case class RequestWithContext[U <: TraitLike](request: Request[AnyContent], lang: Lang, anything: Option[U]) extends WrappedRequest(request) with WithSessionId
case class RequestWithResolvedContext[U <: TraitLike](request: Request[AnyContent], lang: Lang, anything: U, rememberMe: Boolean = false) extends WrappedRequest(request) with WithSessionId
case class Trait(val id: String) extends TraitLike

class AnotherController[U <: TraitLike](implicit ctxBuilder: ContextBuilder[U]) extends Controller  {
  def DecoratedAction(bodyParser: BodyParser[AnyContent] = parse.anyContent)(f: RequestWithContext[U] => Future[Result]) = Action.async {
    implicit request =>
      ctxBuilder.build(request) flatMap {
        case Left(r) =>
          Future.successful(r)
        case Right(requestContext) =>
          f(requestContext).map(_.addingToSession( ("auth" , requestContext.sessionId) ))
      }
  }

  def ResolvedDecoratedAction(bodyParser: BodyParser[AnyContent] = parse.anyContent)(f: RequestWithResolvedContext[U] => Future[Result]) = Action.async {
    implicit request =>
      ctxBuilder.buildAuthenticated(request) flatMap {
        case Left(r) =>
          Future.successful(r)
        case Right(requestContext) =>
          f(requestContext).map(_.addingToSession( ("auth", requestContext.sessionId) ))
      }
  }
}

class SomeContextBuilder extends ContextBuilder[TraitLike] {
  override def build(request: Request[AnyContent]): Future[Either[Result, RequestWithContext[TraitLike]]] = Future.successful(Right(RequestWithContext(request, Lang("en-us"),None)))

  override def buildAuthenticated(request: Request[AnyContent]): Future[Either[Result, RequestWithResolvedContext[TraitLike]]] = Future.successful(Right(RequestWithResolvedContext(request, Lang("en-us"),Trait("id"),false)))
}



