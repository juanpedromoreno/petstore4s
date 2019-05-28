package petstore.runtime

import cats.effect._
import cats.syntax.functor._
import cats.syntax.either._
import petstore.PetstoreClient
import petstore.models.{Error => PetError, _}
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.blaze._
import org.http4s.circe._
import org.http4s.Status.Successful
import io.circe._
import io.circe.generic.semiauto._

import scala.concurrent.ExecutionContext

object PetstoreHttpClient {
  implicit val newPetDecoder: Encoder[NewPet] = deriveEncoder[NewPet]
  implicit val petDecoder: Decoder[Pet]       = deriveDecoder[Pet]

  def apply[F[_]: Effect](client: Client[F], baseUrl: Uri): PetstoreClient[F] = new PetstoreClient[F] {
    implicit val newPetEntity: EntityEncoder[F, NewPet]  = jsonEncoderOf[F, NewPet]
    implicit val petEntity: EntityDecoder[F, Pet]        = jsonOf[F, Pet]
    implicit val petsEntity: EntityDecoder[F, List[Pet]] = jsonOf[F, List[Pet]]
    private val petsUrl                                  = baseUrl / "pets"

    def createPet(pet: NewPet): F[Unit] = client.expect[Unit](
      Request[F](uri = petsUrl, method = Method.POST).withBody(pet)
    )

    def getPets(limit: Option[Int]): F[List[Pet]] =
      client.expect[List[Pet]](limit.fold(petsUrl)(petsUrl +? ("limit", _)))

    def getPet(id: Long): F[Either[PetError, Pet]] =
      client.fetch(Request[F](method = Method.GET, uri = petsUrl / id.toString)) { handleResponse }

    private def handleResponse: Response[F] => F[Either[PetError, Pet]] = {
      case Successful(response) => response.as[Pet].map(_.asRight)
      case default              => default.as[String].map(PetError(default.status.code, _).asLeft)
    }
  }

  def createClient[F[_]: ConcurrentEffect](baseUrl: Uri)(
      implicit executionContext: ExecutionContext): F[PetstoreClient[F]] =
    Http1Client[F](config = BlazeClientConfig.defaultConfig.copy(executionContext = executionContext))
      .map(PetstoreHttpClient(_, baseUrl))
}