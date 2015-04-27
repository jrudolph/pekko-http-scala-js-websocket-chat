package example.akkawschat

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Sink, Source, Flow }

case class ChatMessage(sender: String, message: String)

trait Chat {
  def chatFlow(sender: String): Flow[String, ChatMessage, Unit] =
    Flow.wrap(messageSink(sender), messageSource(sender))((_, _) ⇒ ())

  def messageSource(listenerName: String): Source[ChatMessage, Unit]
  def messageSink(sender: String): Sink[String, Unit]

  def injectMessage(message: ChatMessage): Unit
}

object Chat {
  def create(system: ActorSystem): Chat = {
    val chatActor =
      system.actorOf(Props(new Actor {
        var subscribers = Set.empty[ActorRef]

        def receive: Receive = {
          case NewParticipant(name, subscriber) ⇒
            sendAdminMessage(s"$name joined!") // don't send to the subscriber until #17322 is fixed
            context.watch(subscriber)
            subscribers += subscriber
          case msg: ChatMessage        ⇒ dispatch(msg)
          case ParticipantLeft(person) ⇒ sendAdminMessage(s"$person left!")
          case NewParticipantComplete  ⇒ // expected but unavoidable (completion message cannot be switched off for Sink.actorRef)
          case Terminated(sub)         ⇒ subscribers -= sub // clean up dead subscribers
        }
        def sendAdminMessage(msg: String): Unit =
          dispatch(ChatMessage("admin", msg))
        def dispatch(msg: ChatMessage): Unit = subscribers.foreach(_ ! msg)
      }))

    new Chat {
      def messageSink(sender: String): Sink[String, Unit] =
        // FIXME: here some rate-limiting should be applied to prevent single users flooding the stream
        Flow[String]
          .map(ChatMessage(sender, _))
          .to(Sink.actorRef(chatActor, ParticipantLeft(sender)))

      def messageSource(listenerName: String): Source[ChatMessage, Unit] =
        // a source that will fail as soon as it isn't able to push messages fast enough
        Source(Source.actorRef[ChatMessage](1, OverflowStrategy.fail)) { implicit b ⇒
          source ⇒
            import akka.stream.scaladsl.FlowGraph.Implicits._

            val s = b.add(Sink.actorRef[NewParticipant](chatActor, NewParticipantComplete))
            b.matValue ~> Flow[ActorRef].map(NewParticipant(listenerName, _)) ~> s

            source.outlet
        }.mapMaterialized(_ ⇒ ())

      def injectMessage(message: ChatMessage): Unit = chatActor ! message
    }
  }

  private case class NewParticipant(name: String, subscriber: ActorRef)
  private case object NewParticipantComplete
  private case class ParticipantLeft(name: String)
}