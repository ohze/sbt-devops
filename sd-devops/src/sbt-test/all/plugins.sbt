sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.sandinh" % "sd-devops" % x)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

libraryDependencies += "org.scalameta" %% "munit" % "0.7.29"
