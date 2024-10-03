import sbt._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object Formatting extends AutoPlugin {
  override def trigger: PluginTrigger = AllRequirements
  override def requires: Plugins = SbtScalariform

  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] = formatSettings

  lazy val formatSettings = Seq(
    Compile / ScalariformKeys.preferences := formattingPreferences,
    Test / ScalariformKeys.preferences := formattingPreferences
  )

  import scalariform.formatter.preferences._

  def formattingPreferences: FormattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(UseUnicodeArrows, false)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DanglingCloseParenthesis, Preserve)
      .setPreference(DoubleIndentConstructorArguments, true)
      .setPreference(SpacesAroundMultiImports, true)
}
