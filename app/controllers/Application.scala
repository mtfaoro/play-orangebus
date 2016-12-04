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
import play.api.libs.json._
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
import reactivemongo.bson.BSONDocument

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
      if (filter.length > 0) Json.obj("name" -> Json.obj("$regex" -> (filter + ".*"))) 
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
        val futureUser = collection.flatMap(_.insert(user.copy(_id = BSONObjectID.generate)))

        futureUser.map { result =>
          Home.flashing("success" -> s"User ${user.name} has been created")
        }
      })
  }

  /**
   * WebService responsible to return users in a json
   */
  def getUsersWebService(name: String ) = Action.async { implicit request => 
    val mongoFilter = {
      if (name.length > 0) Json.obj("name" -> Json.obj("$regex" -> (name + ".*"))) 
      else Json.obj()
    }

    val projection = Json.obj("_id" -> 0)
    val filtered = collection.flatMap(
      _.find(mongoFilter, projection).cursor[BSONDocument]().collect[List](Int.MaxValue, Cursor.FailOnError[List[BSONDocument]]()))
    
    filtered.map{
    	users =>  Ok(Json.toJson(users))
    }
  }


  /**
   * WebService responsible to create a user from a json
   */
  def createFromJson = Action.async(parse.json) { request =>

  	val createJson = request.body
    val userJson = createJson.as[JsObject] + ("_id" -> Json.toJson(BSONObjectID.generate))
  	
    Json.fromJson[User](userJson) match {
      case JsSuccess(user, _) =>
        for {
          users <- collection
          lastError <- users.insert(user)
        } yield {
          Logger.debug(s"Successfully inserted with LastError: $lastError")
          Created("Created 1 User")
        }
      case JsError(errors) =>
        Future.successful(BadRequest("Could not build a User from the json provided. " ))
    }
  }

}
