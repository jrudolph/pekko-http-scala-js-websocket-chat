package example.pekkowschat

import org.apache.pekko.actor._
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl._
import shared.Protocol

import scala.util.control.NonFatal

trait Chat {
  def chatFlow(sender: String): Flow[String, Protocol.Message, Any]

  def injectMessage(message: Protocol.ChatMessage): Unit
}

object Chat {
  def create()(implicit system: ActorSystem): Chat = {
    // The chat room implementation
    val ((in, injectionQueue), out) =
      // a dynamic merge hub that collects messages from all current participants
      MergeHub.source[Protocol.Message]
        // small stateful component that keeps track of current members as a convenience to subscribers
        // so that the full set of members can be reported in Joined/Left messages
        .statefulMapConcat[Protocol.Message] { () =>
          var members = Set.empty[String]

          {
            case Protocol.Joined(newMember, _) =>
              members += newMember
              Protocol.Joined(newMember, members.toSeq) :: Nil
            case Protocol.Left(oldMember, _) =>
              members -= oldMember
              Protocol.Left(oldMember, members.toSeq) :: Nil
            case x => x :: Nil
          }
        }
        // add in a source queue to support an imperative API for injecting messages into the chat
        .mergeMat(Source.queue[Protocol.ChatMessage](100, OverflowStrategy.dropNew))(Keep.both)
        // a dynamic broadcast hub that distributes messages to all participants
        .toMat(BroadcastHub.sink[Protocol.Message])(Keep.both)
        .run()

    // The channel in a nice wrapped package for consumption of a participant
    val chatChannel: Flow[Protocol.Message, Protocol.Message, Any] = Flow.fromSinkAndSource(in, out)

    new Chat {
      override def chatFlow(sender: String): Flow[String, Protocol.Message, Any] =
        // incoming data from participant is just plain String messages
        Flow[String]
          // now wrap them in ChatMessages
          .map(Protocol.ChatMessage(sender, _))
          // and enclose them in the stream with Joined and Left messages
          .prepend(Source.single(Protocol.Joined(sender, Nil)))
          .concat(Source.single(Protocol.Left(sender, Nil)))
          .recoverWithRetries(0, {
            case NonFatal(ex) => Source(
              Protocol.ChatMessage(sender, s"Oops, I crashed with $ex") ::
                Protocol.Left(sender, Nil) :: Nil)
          })
          // now send them to the chat
          .via(chatChannel)

      override def injectMessage(message: Protocol.ChatMessage): Unit =
        injectionQueue.offer(message)
    }
  }
}
