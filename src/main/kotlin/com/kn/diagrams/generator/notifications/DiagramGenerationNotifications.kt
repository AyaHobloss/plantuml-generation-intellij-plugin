package com.kn.diagrams.generator.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

fun notifyError(project: Project?, text: String) {
    if (project == null) return

    NotificationGroupManager.getInstance().getNotificationGroup("Diagram Generation plugin")
        .createNotification(text, NotificationType.ERROR)
        .notify(project)
}

fun notifyErrorOccurred(project: Project?) {
    notifyError(project, "Diagram could not be generated.")
}

fun notifyErrorMissingClass(project: Project?) {
    notifyError(project, "No class found for diagram generation. The used Java file seems to have no class inside. ")
}

fun notifyErrorClassNotFound(project: Project?, qualifiedName: String) {
    notifyError(project, "Class not found for qualified name $qualifiedName")
}

fun notifyErrorMissingPublicMethod(project: Project?) {
    notifyError(project, "no public methods found to generate the diagram")
}

