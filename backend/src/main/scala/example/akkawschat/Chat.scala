package example.akkawschat

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import shared.Protocol

trait Chat {
  def chatFlow(sender: String): Flow[String, Protocol.Message, Any]

  def injectMessage(message: Protocol.ChatMessage): Unit
}

object Chat {
  def create(system: ActorSystem): Chat = {
    // The implementation uses a single actor per chat to collect and distribute
    // chat messages. It would be nicer if this could be built by stream operations
    // directly.
    val chatActor =
      system.actorOf(Props(new Actor {
        var subscribers = Set.empty[(String, ActorRef)]

        def receive: Receive = {
          case NewParticipant(name, subscriber) ⇒
            context.watch(subscriber)
            subscribers += (name -> subscriber)
            dispatch(Protocol.Joined(name, members))
          case msg: ReceivedMessage      ⇒ dispatch(msg.toChatMessage)
          case msg: Protocol.ChatMessage ⇒ dispatch(msg)
          case ParticipantLeft(person) ⇒
            val entry @ (name, ref) = subscribers.find(_._1 == person).get
            // report downstream of completion, otherwise, there's a risk of leaking the
            // downstream when the TCP connection is only half-closed
            ref ! Status.Success(Unit)
            subscribers -= entry
            dispatch(Protocol.Left(person, members))
          case Terminated(sub) ⇒
            // clean up dead subscribers, but should have been removed when `ParticipantLeft`
            subscribers = subscribers.filterNot(_._2 == sub)
        }
        def sendAdminMessage(msg: String): Unit = dispatch(Protocol.ChatMessage("admin", msg))
        def dispatch(msg: Protocol.Message): Unit = subscribers.foreach(_._2 ! msg)
        def members = subscribers.map(_._1).toSeq
      }))

    // Wraps the chatActor in a sink. When the stream to this sink will be completed
    // it sends the `ParticipantLeft` message to the chatActor.
    // FIXME: here some rate-limiting should be applied to prevent single users flooding the chat
    def chatInSink(sender: String) = Sink.actorRef[ChatEvent](chatActor, ParticipantLeft(sender))

    new Chat {
      def chatFlow(sender: String): Flow[String, Protocol.ChatMessage, Any] = {
        val in =
          Flow[String]
            .map(ReceivedMessage(sender, _))
            .to(chatInSink(sender))

        // The counter-part which is a source that will create a target ActorRef per
        // materialization where the chatActor will send its messages to.
        // This source will only buffer one element and will fail if the client doesn't read
        // messages fast enough.
        val out =
          Source.actorRef[Protocol.ChatMessage](1, OverflowStrategy.fail)
            .mapMaterializedValue(chatActor ! NewParticipant(sender, _))

        Flow.fromSinkAndSource(in, out)
      }
      def injectMessage(message: Protocol.ChatMessage): Unit = chatActor ! message // non-streams interface
    }
  }

  private sealed trait ChatEvent
  private case class NewParticipant(name: String, subscriber: ActorRef) extends ChatEvent
  private case class ParticipantLeft(name: String) extends ChatEvent
  private case class ReceivedMessage(sender: String, message: String) extends ChatEvent {
    def toChatMessage: Protocol.ChatMessage = Protocol.ChatMessage(sender, message)
  }
}
