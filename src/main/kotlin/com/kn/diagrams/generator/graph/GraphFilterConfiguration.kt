package com.kn.diagrams.generator.graph

import com.kn.diagrams.generator.config.CommentWithEnumValues
import com.kn.diagrams.generator.config.CommentWithValue

class GraphTraversal(
        var forwardDepth: Int = 3,
        var backwardDepth: Int = 3,
        var classPackageExcludeFilter: String = "",
        var classPackageIncludeFilter: String = "",
        var classNameExcludeFilter: String = "",
        var classNameIncludeFilter: String = "",
        var methodNameExcludeFilter: String = "",
        var methodNameIncludeFilter: String = "",
        var includeLayers: List<String> = emptyList(),
        var excludeLayers: List<String> = emptyList(),
        var hideMappings: Boolean = true,
        var hideDataStructures: Boolean = true,
        var hideMethodsInDataStructures: Boolean = true,
        var hidePrivateMethods: Boolean = false,
        @CommentWithValue("indirection: implementation -> interface (is hidden) -> implementation")
        var hideInterfaceCalls: Boolean = true,
        @CommentWithValue("root node is included")
        var onlyShowApplicationEntryPoints: Boolean = false
)

class GraphRestriction(
        var classPackageExcludeFilter: String = "",
        var classPackageIncludeFilter: String = "",
        var classNameExcludeFilter: String = "",
        var classNameIncludeFilter: String = "",
        var methodNameExcludeFilter: String = "",
        var methodNameIncludeFilter: String = "",
        var includeLayers: List<String> = emptyList(),
        var excludeLayers: List<String> = emptyList(),
        @CommentWithValue("inheritance/annotation based filtering is done in a second step")
        var removeByInheritance: String = "",
        var removeByAnnotation: String = "",
        @CommentWithValue("cleanup the graph after inheritance/annotation based filtering is done")
        var removeByClassPackage: String = "",
        var removeByClassName: String = "",
        var cutMappings: Boolean = false,
        var cutEnum: Boolean = true,
        var cutTests: Boolean = true,
        var cutClient: Boolean = true,
        var cutDataAccess: Boolean = true,
        var cutInterfaceStructures: Boolean = true,
        var cutDataStructures: Boolean = true,
        var cutGetterAndSetter: Boolean = true,
        var cutConstructors: Boolean = true
)

enum class SearchMode {
    OpenProject, AllProjects
}

class Layer(override var name: String, override var path: String): Filterable

class ProjectClassification(
        @CommentWithEnumValues
        var searchMode: SearchMode = SearchMode.OpenProject,
        @CommentWithValue("can be used when code base is not changed")
        val useCaching: Boolean = false,
        var includedProjects: String = "",
        @CommentWithValue("sub-components will be shown separately but keep their identifier from the root (e.g. 'interfaces.adapter')")
        var subComponentRoot: String = "",
        var pathEndKeywords: String = "*.impl",
        var isClientPath: String = "",
        var isClientName: String = "",
        var isTestPath: String = "",
        var isTestName: String = "",
        var isMappingPath: String = "",
        var isMappingName: String = "",
        var isDataAccessPath: String = "",
        var isDataAccessName: String = "",
        var isDataStructurePath: String = "",
        var isDataStructureName: String = "",
        var isInterfaceStructuresPath: String = "",
        var isInterfaceStructuresName: String = "",
        var isEntryPointPath: String = "",
        var isEntryPointName: String = "",
        @CommentWithValue("first matching pattern is applied; e.g.: {\"controller\": { \"name\": \"*.controller\", \"path\": \"controller\" }}")
        var customLayers: Map<String, Layer> = mapOf() // TODO restriction / visibility filter options

) {

    fun ClassReference.isEntryPoint() = isEntryPointName.regexBySemicolon().any { it.matches(name) }
            || isEntryPointPath.regexBySemicolon().any { it.matches(path) }

    fun ClassReference.isDataStructure() = isDataStructureName.regexBySemicolon().any { it.matches(name) }
            || isDataStructurePath.regexBySemicolon().any { it.matches(path) }

    fun ClassReference.isDataAccess() = isDataAccessName.regexBySemicolon().any { it.matches(name) }
            || isDataAccessPath.regexBySemicolon().any { it.matches(path) }

    fun ClassReference.isMapping() = isMappingName.regexBySemicolon().any { it.matches(name) }
            || isMappingPath.regexBySemicolon().any { it.matches(path) }

    fun ClassReference.isInterfaceStructure() = isInterfaceStructuresName.regexBySemicolon().any { it.matches(name) }
            || isInterfaceStructuresPath.regexBySemicolon().any { it.matches(path) }

    fun ClassReference.isClient() = isClientName.regexBySemicolon().any { it.matches(name) }
            || isClientPath.regexBySemicolon().any { it.matches(path) }

    fun ClassReference.isTest() = absolutePath?.contains("/test/") == true
            || isTestName.regexBySemicolon().any { it.matches(name) }
            || isTestPath.regexBySemicolon().any { it.matches(path) }
}

fun notEmptyAnd(plainFilter: String, filter: (List<Regex>) -> Boolean): Boolean {
    return plainFilter != "" && filter(plainFilter.regexBySemicolon())
}

inline fun emptyOr(plainFilter: String, filter: (List<Regex>) -> Boolean): Boolean {
    return plainFilter == "" || filter(plainFilter.regexBySemicolon())
}

fun String.bySemicolon(): List<String> = if(this == "") emptyList() else split(";")

fun String.regexBySemicolon(): List<Regex> = takeIf { it != "" }
        ?.split(";")
        ?.map { it.replace("*", ".*").toRegex(option = RegexOption.IGNORE_CASE) }
        ?: emptyList()
