package controllers

import java.util.concurrent.TimeoutException

import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.math.signum

import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{ Action, Controller }
import play.api.data.Form
import play.api.data.Forms.{ date, ignored, mapping, nonEmptyText }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json, Json.toJsFieldJsValueWrapper
import play.api.Play.current

import play.modules.reactivemongo.{
  MongoController, ReactiveMongoApi, ReactiveMongoComponents
}
import reactivemongo.play.json._, collection.JSONCollection
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID
import reactivemongo.api.Cursor

import models.{ User, JsonFormats}, JsonFormats.userFormat
import views.html

class Application @Inject() (
  val reactiveMongoApi: ReactiveMongoApi,
  val messagesApi: MessagesApi)
    extends Controller with MongoController with ReactiveMongoComponents {

  /**
   * Describe the form that will be Used in HTML file
   */
  val userForm = Form(
    mapping(
      "id" -> ignored(BSONObjectID.generate: BSONObjectID),
      "name" -> nonEmptyText,
      "email" -> nonEmptyText)(User.apply)(User.unapply))

  def collection: Future[JSONCollection] =
    database.map(_.collection[JSONCollection]("users"))

  // ------------------------------------------ //
  // Using case classes + Json Writes and Reads //
  // ------------------------------------------ //
  import play.api.data.Form
  import models._
  import models.JsonFormats._

  /**
   * Handle default path requests, redirect to user list
   */
  def index = Action { Home }
  val Home = Redirect(routes.Application.list())


  /**
   * Display Users
   *
   * @param filter Filter applied on user names
   */
  def list(filter: String) = Action.async { implicit request =>
     val mongoFilter = {
      if (filter.length > 0) Json.obj("name" -> filter)
      else Json.obj()
    }
    
    val filtered = collection.flatMap(
      _.find(mongoFilter).cursor[User]().collect[List](Int.MaxValue, Cursor.FailOnError[List[User]]()))
    
    filtered.map{
    	users =>  {
         implicit val msg = messagesApi.preferred(request)
         Ok(html.list(users, filter))}
    }
  }


  /**
   * Display the user form
   */
  def create = Action { request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(html.createForm(userForm))
  }

  /**
   * Save a User in the database.
   */
  def save = Action.async { implicit request =>
    userForm.bindFromRequest.fold(
      { formWithErrors =>
        implicit val msg = messagesApi.preferred(request)
        Future.successful(BadRequest(html.createForm(formWithErrors)))
      },
      user => {
        val futureUpdateEmp = collection.flatMap(_.insert(user.copy(_id = BSONObjectID.generate)))

        futureUpdateEmp.map { result =>
          Home.flashing("success" -> s"User ${user.name} has been created")
        }
      })
  }
}
