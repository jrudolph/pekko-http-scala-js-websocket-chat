package example.akkawschat.cli

import akka.actor.ActorSystem
import akka.stream.stage.{ InHandler, GraphStageLogic, GraphStage }
import akka.stream._
import akka.stream.scaladsl.{ Sink, FlowGraph, Source, Flow }

import scala.collection.immutable
import scala.concurrent.ExecutionContext

trait ConsoleDSL[T] {
  def handler(implicit ec: ExecutionContext): Flow[Command, T, Unit] = {
    val characters = Source.fromGraph(new ConsoleInput)

    val graph =
      FlowGraph.create() { implicit b ⇒
        import FlowGraph.Implicits._

        val prompt = b.add(ConsoleStage)
        characters ~> prompt.characterInput

        FlowShape(prompt.commandIn, prompt.output)
      }

    Flow.fromGraph(graph)
  }

  case class ConsoleStageShape(characterInput: Inlet[Char], commandIn: Inlet[Command], output: Outlet[T]) extends Shape {
    def inlets = Vector(characterInput, commandIn)
    def outlets = Vector(output)
    def deepCopy(): Shape = ConsoleStageShape(characterInput.carbonCopy(), commandIn.carbonCopy(), output.carbonCopy())
    def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape =
      ConsoleStageShape(inlets(0).asInstanceOf[Inlet[Char]], inlets(1).asInstanceOf[Inlet[Command]], outlets(0).asInstanceOf[Outlet[T]])
  }
  object ConsoleStage extends GraphStage[ConsoleStageShape] {
    import TTY._

    val shape: ConsoleStageShape = ConsoleStageShape(Inlet[Char]("characterInput"), Inlet[Command]("commandIn"), Outlet[T]("output"))
    import shape.{ characterInput, commandIn, output }

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        var inputHandler: PartialFunction[Char, Command] = PartialFunction.empty
        var promptLine: String = ""

        setHandler(characterInput, new InHandler {
          def onPush(): Unit = {
            val input = grab(characterInput)
            val cmd = inputHandler.applyOrElse[Char, Command](input, _ ⇒ Command.Empty)
            runCommand(cmd)

            pull(characterInput)
          }
        })
        setHandler(commandIn, new InHandler {
          def onPush(): Unit = {
            runCommand(grab(commandIn))
            pull(commandIn)
          }
        })
        setHandler(output, eagerTerminateOutput)

        import Command._
        def runCommand(command: Command): Unit = command match {
          case Empty           ⇒
          case Multiple(cmds)  ⇒ cmds foreach runCommand
          case PrintLine(line) ⇒ print(s"$RESTORE$ERASE_LINE$line\n$SAVE$promptLine")
          case ChangePrompt(newPrompt) ⇒
            promptLine = newPrompt
            print(s"$RESTORE$ERASE_LINE$SAVE$promptLine")
          case ChangeInputHandler(newHandler) ⇒ inputHandler = newHandler
          case Emit(element)                  ⇒ push(output, element)
          case Complete                       ⇒ completeStage()
        }

        override def preStart(): Unit = {
          pull(commandIn)
          pull(characterInput)
        }
      }
  }

  sealed trait Command {
    def ~(other: Command): Command = Command.Multiple(Seq(this, other))
  }
  object Command {
    val Empty = Multiple(Nil)

    case class PrintLine(line: String) extends Command
    case class ChangePrompt(prompt: String) extends Command
    case class ChangeInputHandler(handler: PartialFunction[Char, Command]) extends Command
    case class Emit(element: T) extends Command
    case object Complete extends Command
    case class Multiple(commands: Seq[Command]) extends Command {
      override def ~(other: Command): Command = Multiple(commands :+ other) // don't nest
    }
  }
}

object ConsoleDSLTestApp extends App {
  object MyApp extends ConsoleDSL[String] {
    def run(): Unit = {
      implicit val system = ActorSystem()
      import system.dispatcher
      implicit val materializer = ActorMaterializer()

      val input =
        Source.single(hello) ++
          Source.maybe[Command]

      input.via(handler).runWith(Sink.ignore)

      import scala.concurrent.duration._
      system.scheduler.scheduleOnce(10.seconds)(system.shutdown())
    }

    import Command._

    def hello: Command =
      PrintLine("Hello World!") ~ promptAndPrint

    def promptAndPrint: Command =
      simplePrompt("") { input ⇒
        {
          if (input.trim.nonEmpty) PrintLine(s"Collected '$input'")
          else Empty
        } ~ promptAndPrint
      }
    def simplePrompt(currentInput: String)(andThen: String ⇒ Command): Command =
      ChangePrompt(s"> $currentInput") ~
        ChangeInputHandler {
          case 4 /* Ctrl-D */             ⇒ PrintLine("Goodbye") ~ Complete
          case '\r'                       ⇒ andThen(currentInput)
          case x if x >= 0x20 && x < 0x7e ⇒ simplePrompt(currentInput + x)(andThen)
          case 127 /* backspace */        ⇒ simplePrompt(currentInput.dropRight(1))(andThen)
        }
  }
  MyApp.run()
}