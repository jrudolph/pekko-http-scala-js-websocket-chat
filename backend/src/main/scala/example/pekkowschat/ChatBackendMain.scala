package example.pekkowschat

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http

import scala.util.{ Failure, Success }

object ChatBackendMain extends App {
  implicit val system = ActorSystem()
  import system.dispatcher

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  val service = new Webservice

  val binding = Http().bindAndHandle(service.route, interface, port)
  binding.onComplete {
    case Success(binding) =>
      val localAddress = binding.localAddress
      println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
    case Failure(e) =>
      println(s"Binding failed with ${e.getMessage}")
      system.terminate()
  }
}
