package com.kn.diagrams.generator.generator

import com.kn.diagrams.generator.builder.DotCluster
import com.kn.diagrams.generator.builder.DotNode
import com.kn.diagrams.generator.config.NodeGrouping
import com.kn.diagrams.generator.generator.vcs.layer
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.graph.bySemicolon
import java.awt.Color

data class Grouping(val name: String, val path: String = ""){
    val depth get() = path.takeIf { it != "" }?.split(".")?.size ?: 0

    fun nextSubGroup(): Grouping? {
        if(path == "") return null

        val parts = path.split(".")

        return Grouping(parts.last(), parts.dropLast(1).joinToString("."))
    }
}

fun ClassReference.group(level: NodeGrouping, config: DiagramVisualizationConfiguration): Grouping {
    return when(level){
        NodeGrouping.Layer -> Grouping(layer(config))
        NodeGrouping.Component -> {
            val fullPath = diagramPath(config)
            val lastComponentPart = fullPath.split(".").last()
            val componentPath = fullPath.split(".").dropLast(1).joinToString(".")

            Grouping(lastComponentPart, componentPath)
        }
        NodeGrouping.None -> Grouping("no group", "")
    }
}

fun ClassReference.diagramPath(config: DiagramVisualizationConfiguration): String {
    var diagramPath = path

    config.projectClassification.subComponentRoot.bySemicolon().forEach {
        var rootOnly = it
        config.projectClassification.includedProjects.bySemicolon().forEach { included ->
            rootOnly = rootOnly.substringAfter("$included.")
        }

        if(diagramPath.contains(it)){
            diagramPath = rootOnly + ";" + diagramPath.substringAfter("$it.")
        }
    }

    config.projectClassification.includedProjects.bySemicolon().forEach {
        diagramPath = diagramPath.substringAfter("$it.")
    }
    config.projectClassification.pathEndKeywords.takeUnless { it == "" }?.bySemicolon()?.forEach {
        diagramPath = diagramPath.substringBefore(".$it")
    }

    val basePath = diagramPath.takeIf { it.contains(";") }?.substringBefore(";")?.let { "$it." } ?: ""
    return basePath + diagramPath.substringAfter(";")
        .split(".")
        .let { it.subList(0, Integer.max(0, Integer.min(it.size, config.showPackageLevels))) }
        .joinToString(".")
}

class DotHierarchicalGroupCluster(val groupClusterLayout: (Int, DotCluster, Color, Boolean) -> Unit) : DotCluster("not visible", "not visible") {

    private val groupClusterCache = mutableMapOf<Grouping, DotCluster>()

    override fun create(): String {
        groupClusterCache.forEach { (group, cluster) ->
            if(group.path == ""){
                childs.add(cluster)
            }else{
                groupClusterCache[group.nextSubGroup()]?.childs?.add(cluster)
            }
        }

        // this is only a fake cluster to avoid searching in the main graph and clusters
        return childs.map { it.create() }.sorted().joinToString("\n\n")
    }

    // TODO semi overloading base addNode() - make this fail safe!
    fun addNode(node: DotNode, grouping: Grouping? = null) {
        groupCluster(grouping).addNode(node)
    }

    fun groupCluster(grouping: Grouping? = null): DotCluster {
        if (grouping == null) {
            return this
        }

        var currentGroup = grouping
        var i = grouping.depth
        while (currentGroup != null){
            var existingCluster = groupClusterCache[currentGroup]

            if(existingCluster == null){
                existingCluster = DotCluster(currentGroup.name)
                groupClusterCache[currentGroup] = existingCluster
                groupClusterLayout(i, existingCluster, groupColorLevel[i], currentGroup == grouping)
            }else{
                break
            }

            i--
            currentGroup = currentGroup.nextSubGroup()
        }

        return groupClusterCache[grouping]!!
    }
}

