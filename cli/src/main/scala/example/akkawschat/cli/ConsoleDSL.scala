package example.akkawschat.cli

import akka.stream.stage.{ InHandler, GraphStageLogic, GraphStage }
import akka.stream._
import akka.stream.scaladsl.{ GraphDSL, Source, Flow }

import scala.collection.immutable
import scala.concurrent.ExecutionContext

/** Infrastructure for a small DSL that allows to write stateful concurrent console apps of a certain kind */
trait ConsoleDSL[T] {
  type State <: AnyRef
  def initialState: State

  /** Returns a Flow that implements the console logic. */
  def consoleHandler(implicit ec: ExecutionContext): Flow[Command, T, Any] = {
    val characters = Source.fromGraph(new ConsoleInput)

    val graph =
      GraphDSL.create() { implicit b ⇒
        import GraphDSL.Implicits._

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
        var inputHandler: State ⇒ PartialFunction[Char, Command] = (_ ⇒ PartialFunction.empty)
        var promptLine: State ⇒ String = (_ ⇒ "")
        var state: State = initialState

        setHandler(characterInput, new InHandler {
          def onPush(): Unit = {
            val input = grab(characterInput)
            if (input == 4) {
              outputLine("Goodbye!")
              completeStage()
            } else {
              val cmd = inputHandler(state).applyOrElse[Char, Command](input, _ ⇒ Command.Empty)
              runCommand(cmd)

              pull(characterInput)
            }
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
          case Empty          ⇒
          case Multiple(cmds) ⇒ cmds foreach runCommand
          case PrintLine(line) ⇒
            outputLine(line)
            updatePrompt()
          case StatefulPrompt(newPrompt) ⇒
            promptLine = newPrompt
            updatePrompt()
          case SetStatefulInputHandler(newHandler) ⇒ inputHandler = newHandler
          case UpdateState(modify) ⇒
            state = modify(state)
            updatePrompt()

          case Emit(element) ⇒ push(output, element)
          case Complete      ⇒ completeStage()
        }

        def outputLine(line: String): Unit = print(s"$RESTORE$ERASE_LINE$line\n$SAVE")
        def updatePrompt(): Unit = print(s"$RESTORE$ERASE_LINE$SAVE${promptLine(state)}")

        override def preStart(): Unit = {
          pull(commandIn)
          pull(characterInput)
          print(SAVE) // to prevent jumping before the current output
        }
      }
  }

  sealed trait Command {
    def ~(other: Command): Command = Command.Multiple(Seq(this, other))
  }
  object Command {
    val Empty = Multiple(Nil)

    case class PrintLine(line: String) extends Command
    def SetPrompt(prompt: String): Command = StatefulPrompt(_ ⇒ prompt)
    case class StatefulPrompt(prompt: State ⇒ String) extends Command
    def SetState(state: State): Command = UpdateState(_ ⇒ state)
    case class UpdateState(modify: State ⇒ State) extends Command
    def SetInputHandler(handler: PartialFunction[Char, Command]): Command = SetStatefulInputHandler(_ ⇒ handler)
    case class SetStatefulInputHandler(handler: State ⇒ PartialFunction[Char, Command]) extends Command
    case class Emit(element: T) extends Command
    case object Complete extends Command
    case class Multiple(commands: Seq[Command]) extends Command {
      override def ~(other: Command): Command = Multiple(commands :+ other) // don't nest
    }
  }

  import Command._
  def readLineStatefulPrompt(prompt: State ⇒ String, currentInput: String = "")(andThen: String ⇒ Command): Command =
    StatefulPrompt(state ⇒ s"${prompt(state)}$currentInput") ~
      SetInputHandler {
        case '\r'                       ⇒ andThen(currentInput)
        case x if x >= 0x20 && x < 0x7e ⇒ readLineStatefulPrompt(prompt, currentInput + x)(andThen)
        case 127 /* backspace */        ⇒ readLineStatefulPrompt(prompt, currentInput.dropRight(1))(andThen)
      }
  def readLine(prompt: String = "> ")(andThen: String ⇒ Command): Command =
    readLineStatefulPrompt(_ ⇒ prompt)(andThen)
}
