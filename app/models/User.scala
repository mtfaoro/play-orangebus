package models

import reactivemongo.bson.BSONObjectID

case class User(_id: BSONObjectID, name: String, email: String)

object JsonFormats {
  import play.api.libs.json.Json
  import play.api.data._
  import play.api.data.Forms._
  import reactivemongo.play.json._

  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val userFormat = Json.format[User]
}
