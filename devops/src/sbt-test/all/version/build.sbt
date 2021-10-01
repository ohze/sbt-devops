version := "0.1.0"

import com.sandinh.devops.DevopsPlugin.qaVersionTask
TaskKey[Unit]("qaVersion") := qaVersionTask.value
