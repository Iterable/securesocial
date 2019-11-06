/**
 * Copyright 2012-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package securesocial.core.providers

import play.api.libs.json.{ JsError, JsObject, JsSuccess, Json, OFormat }
import securesocial.core._
import securesocial.core.services.{ CacheService, RoutesService }

import scala.concurrent.Future

object GooglePeopleApi {
  object PersonFields {
    final val Metadata = "metadata"
    final val Names = "names"
    final val EmailAddresses = "emailAddresses"
    final val Photos = "photos"
  }

  // https://developers.google.com/people/api/rest/v1/people
  case class FieldMetadata(primary: Boolean)
  object FieldMetadata {
    implicit val fmt: OFormat[FieldMetadata] = Json.format[FieldMetadata]
  }

  trait HasFieldMetadata {
    def metadata: FieldMetadata
  }

  case class Source(id: String, `type`: String)
  object Source {
    implicit val fmt: OFormat[Source] = Json.format[Source]
  }

  case class PersonMetadata(sources: Seq[Source])
  object PersonMetadata {
    implicit val fmt: OFormat[PersonMetadata] = Json.format[PersonMetadata]
  }

  case class Name(
    metadata: FieldMetadata,
    displayName: Option[String],
    familyName: Option[String],
    givenName: Option[String]) extends HasFieldMetadata
  object Name {
    implicit val fmt: OFormat[Name] = Json.format[Name]
  }

  case class Photo(metadata: FieldMetadata, url: Option[String]) extends HasFieldMetadata
  object Photo {
    implicit val fmt: OFormat[Photo] = Json.format[Photo]
  }

  case class EmailAddress(metadata: FieldMetadata, value: Option[String]) extends HasFieldMetadata
  object EmailAddress {
    implicit val fmt: OFormat[EmailAddress] = Json.format[EmailAddress]
  }

  case class Person(metadata: PersonMetadata, names: Seq[Name], photos: Seq[Photo], emailAddresses: Seq[EmailAddress])
  object Person {
    implicit val fmt: OFormat[Person] = Json.format[Person]
  }

  // https://cloud.google.com/apis/design/errors
  case class Status(code: Int, message: String, status: String)
  object Status {
    implicit val fmt: OFormat[Status] = Json.format[Status]
  }
  case class Error(error: Status)
  object Error {
    implicit val fmt: OFormat[Error] = Json.format[Error]
  }

  def userInfoApi(personFields: Seq[String], accessToken: String) = s"https://people.googleapis.com/v1/people/me?personFields=${personFields.mkString(",")}&access_token=$accessToken"

}

/**
 * A Google OAuth2 Provider
 */
class GoogleProvider(
  routesService: RoutesService,
  cacheService: CacheService,
  client: OAuth2Client) extends OAuth2Provider(routesService, client, cacheService) {

  import GooglePeopleApi.PersonFields._
  import GooglePeopleApi._

  protected val personFields: Seq[String] = Seq(Metadata, Names, EmailAddresses, Photos)

  override val id: String = GoogleProvider.Google

  protected final def primary[T <: HasFieldMetadata](values: Seq[T]): Option[T] = values.find(_.metadata.primary)

  protected def fillSuccessfulProfile(me: Person, info: OAuth2Info): BasicProfile = {
    if (me.metadata.sources.length != 1) {
      logger.warn(s"[securesocial] person has multiple metadata sources: $me")
    }
    val userId = me.metadata.sources.head.id
    val name = primary(me.names)
    val firstName = name.flatMap(_.givenName)
    val lastName = name.flatMap(_.familyName)
    val fullName = name.flatMap(_.displayName)
    val avatarUrl = primary(me.photos).flatMap(_.url)
    val email = primary(me.emailAddresses).flatMap(_.value)
    BasicProfile(id, userId, firstName, lastName, fullName, email, avatarUrl, authMethod, oAuth2Info = Some(info))
  }

  def fillProfile(info: OAuth2Info): Future[BasicProfile] = {
    client.retrieveProfile(userInfoApi(personFields, info.accessToken)).map { me =>
      me.validate[Person] match {
        case JsSuccess(person, _) => fillSuccessfulProfile(person, info)
        case JsError(errors) =>
          me.asOpt[Error] match {
            case Some(error) =>
              val message = error.error.message
              val errorCode = error.error.code
              logger.error(s"[securesocial] error retrieving profile information from Google. Error type = $errorCode, message = $message")
              throw AuthenticationException()
            case _ =>
              logger.error(s"[securesocial] unexpected response from Google People API: $me, unable to parse with errors: $errors")
              throw AuthenticationException()
          }
      }
    } recover {
      case e: AuthenticationException => throw e
      case e =>
        logger.error("[securesocial] error retrieving profile information from Google", e)
        throw AuthenticationException()
    }
  }
}

object GoogleProvider {
  val Google = "google"
}
