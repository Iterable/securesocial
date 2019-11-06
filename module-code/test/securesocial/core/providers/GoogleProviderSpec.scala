package securesocial.core.providers

import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json
import play.api.test._
import securesocial.core.services._
import securesocial.core._

import scala.concurrent.{ ExecutionContext, Future, TimeoutException }

@RunWith(classOf[JUnitRunner])
class GoogleProviderSpec extends PlaySpecification with Mockito {
  "GoogleProvider" should {
    "throw an AuthenticationException if Google returns an error" in new WithMocks {
      val response =
        """
          |{
          |  "error": {
          |    "code": 400,
          |    "message": "Invalid personFields mask path: \"metadfafads\". Valid paths are documented at https://developers.google.com/people/api/rest/v1/people/get.",
          |    "status": "INVALID_ARGUMENT"
          |  }
          |}
          |""".stripMargin
      stubResponse(response)

      await(googleProvider.fillProfile(oAuth2Info)) must throwAn[AuthenticationException]
    }

    "throw an AuthenticationException if Google returns an unexpected payload" in new WithMocks {
      val response =
        """
          |{
          |  "foo": "bar"
          |}
          |""".stripMargin
      stubResponse(response)

      await(googleProvider.fillProfile(oAuth2Info)) must throwAn[AuthenticationException]
    }

    "throw an AuthenticationException if Google fails for some other reason" in new WithMocks {
      client.retrieveProfile(any) returns Future.failed(new TimeoutException)

      await(googleProvider.fillProfile(oAuth2Info)) must throwAn[AuthenticationException]
    }

    "Authenticate given valid response" in new WithMocks {
      val userId = "219381629371239128713"
      val firstName = "first"
      val lastName = "last"
      val fullName = "first last"
      val email = "myemail"
      val avatarUrl = "avatar url"
      val response =
        s"""
           |{
           |   "resourceName": "people/$userId",
           |   "metadata": {
           |     "sources": [
           |       {
           |         "type": "PROFILE",
           |         "id": "$userId",
           |         "profileMetadata": {
           |           "objectType": "PERSON",
           |           "userTypes": [
           |             "GOOGLE_USER"
           |           ]
           |         }
           |       }
           |     ],
           |     "objectType": "PERSON"
           |   },
           |   "names": [
           |     {
           |       "metadata": {
           |         "primary": true,
           |         "source": {
           |           "type": "PROFILE",
           |           "id": "$userId"
           |         }
           |       },
           |       "displayName": "$fullName",
           |       "familyName": "$lastName",
           |       "givenName": "$firstName"
           |     }
           |   ],
           |   "photos": [
           |     {
           |       "metadata": {
           |         "primary": true,
           |         "source": {
           |           "type": "PROFILE",
           |           "id": "$userId"
           |         }
           |       },
           |       "url": "$avatarUrl",
           |       "default": true
           |     }
           |   ],
           |   "emailAddresses": [
           |     {
           |       "metadata": {
           |         "primary": true,
           |         "verified": true,
           |         "source": {
           |           "type": "ACCOUNT",
           |           "id": "$userId"
           |         }
           |       },
           |       "value": "$email"
           |     }
           |   ]
           | }""".stripMargin
      stubResponse(response)

      val profile = await(googleProvider.fillProfile(oAuth2Info))
      val expectedProfile = BasicProfile(
        GoogleProvider.Google,
        userId,
        Some(firstName),
        Some(lastName),
        Some(fullName),
        Some(email),
        Some(avatarUrl),
        AuthenticationMethod.OAuth2,
        oAuth2Info = Some(oAuth2Info))
      profile must_== expectedProfile
    }
  }

  trait WithMocks extends Before with Mockito with MustThrownExpectations {
    val routesService = mock[RoutesService]
    val cacheService = mock[CacheService]
    val client = mock[OAuth2Client]
    client.executionContext returns ExecutionContext.global

    val googleProvider = new GoogleProvider(routesService, cacheService, client)

    val oAuth2Info = mock[OAuth2Info]

    def before = {
      oAuth2Info.accessToken returns "some access token"
    }

    def stubResponse(response: String) = {
      client.retrieveProfile(anyString) returns Future.successful(Json.parse(response))
    }
  }

}
