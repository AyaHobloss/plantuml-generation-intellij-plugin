package com.kn.diagrams.generator.config

import com.google.gson.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.kn.diagrams.generator.cast
import com.kn.diagrams.generator.graph.simpleSignature
import com.kn.diagrams.generator.inReadAction
import com.kn.diagrams.generator.notifications.notifyErrorClassNotFound
import java.lang.reflect.Type
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

val serializer: Gson = GsonBuilder().setVersion(1.3)
        .serializeNulls()
        .setPrettyPrinting()
        .create()

fun toJsonWithComments(config: Any) = addComments(serializer.toJson(config), config)

fun Any.metaDataSection() = """
        |/' diagram meta data start
        |config=${this.javaClass.simpleName};
        |${toJsonWithComments(this)}
        |diagram meta data end '/
    """.trimMargin("|")

@OptIn(ExperimentalStdlibApi::class)
fun addComments(metadata: String, config: Any): String {
    var newMetadata = metadata
    config::class.memberProperties
            .mapNotNull { it.cast<KProperty1<Any, *>>()?.get(config) }
            .flatMap {
                it::class.java.declaredFields
                        .filter { f -> f.isAnnotationPresent(CommentWithEnumValues::class.java) || f.isAnnotationPresent(CommentWithValue::class.java) }
            }.forEach { field ->
                val comment = if (field.isAnnotationPresent(CommentWithEnumValues::class.java)) {
                    field.type.fields.joinToString(", ") { it.name }
                } else field.getAnnotation(CommentWithValue::class.java).value
                newMetadata = newMetadata.replace("\"${field.name}.*\n".toRegex()) { match -> match.value.substringBefore("\n") + " // $comment\n" }
            }

    return newMetadata
}

fun DiagramConfiguration.Companion.loadFromMetadata(diagramText: String): BaseDiagramConfiguration? {
    val metadata = diagramText
            .substringBefore("diagram meta data end '/")
            .substringAfter("/' diagram meta data start")

    val configJson = metadata.substringAfter(";")
    val configClassName = metadata.substringBefore(";").substringAfter("config=")

    val configType = typeOf(configClassName) ?: return null
    return serializer.fromJson(configJson, configType) as BaseDiagramConfiguration
}

fun typeOf(className: String) = sequenceOf(
            CallConfiguration::class.java,
            StructureConfiguration::class.java,
            FlowConfiguration::class.java,
            ClusterConfiguration::class.java,
            VcsConfiguration::class.java)
        .firstOrNull { it.simpleName == className }

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class CommentWithEnumValues

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class CommentWithValue(val value: String)
