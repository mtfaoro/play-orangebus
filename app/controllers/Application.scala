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

import models.{ User, JsonFormats, Page }, JsonFormats.userFormat
import views.html

class Application @Inject() (
  val reactiveMongoApi: ReactiveMongoApi,
  val messagesApi: MessagesApi)
    extends Controller with MongoController with ReactiveMongoComponents {

  implicit val timeout = 10.seconds

  lazy val config = current.configuration



  /**
   * Describe the employee form (used in both edit and create screens).
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
   * Handle default path requests, redirect to employee list
   */
  def index = Action { Home }

  /**
   * This result directly redirect to the application home.
   */
  val Home = Redirect(routes.Application.list())

  /**
   * Display the paginated list of employees.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param filter Filter applied on employee names
   */
  def list(page: Int, orderBy: Int, filter: String) = Action.async { implicit request =>
     val mongoFilter = {
      if (filter.length > 0) Json.obj("name" -> filter)
      else Json.obj()
    }
    
    val sortFilter = Json.obj("name" -> signum(orderBy))

    val pageSize = config.getInt("page.size").filter(_ > 0).getOrElse(20)
    val offset = page * pageSize
    val futureTotal = collection.flatMap(_.count(Some(mongoFilter)))
    val filtered = collection.flatMap(
      _.find(mongoFilter).options(QueryOpts(skipN = page * pageSize)).sort(sortFilter).cursor[User]().collect[List](pageSize))

    futureTotal.zip(filtered).map { case (total, users) => {
      implicit val msg = messagesApi.preferred(request)

      Ok(html.list(Page(users, page, offset, total), orderBy, filter))
    }}.recover {
      case t: TimeoutException =>
        Logger.error("Problem found in User list process")
        InternalServerError(t.getMessage)
    }
  }


  /**
   * Display the 'new employee form'.
   */
  def create = Action { request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(html.createForm(userForm))
  }

  /**
   * Handle the 'new employee form' submission.
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
        }.recover {
          case t: TimeoutException =>
            Logger.error("Problem found in user update process")
            InternalServerError(t.getMessage)
        }
      })
  }

  


}
