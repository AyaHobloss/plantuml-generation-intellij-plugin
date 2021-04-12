package com.kn.diagrams.generator.graph

import com.intellij.openapi.project.Project
import com.kn.diagrams.generator.config.serializer

val analysisCache = AnalysisCache()

class AnalysisCache {
    var existingGraph: GraphDefinition? = null
    var restrictionHash: Int = -1

    fun getOrCompute(project: Project, restrictionFilter: GraphRestrictionFilter, searchMode: SearchMode): GraphDefinition{
        return if(restrictionFilter.global.useCaching){
            if(needsCompute(restrictionFilter)){
                existingGraph = GraphDefinition(project, restrictionFilter, searchMode)
                restrictionHash = hash(restrictionFilter)
            }

            existingGraph!!
        }else{
            existingGraph = null
            GraphDefinition(project, restrictionFilter, searchMode)
        }
    }

    private fun needsCompute(restrictionFilter: GraphRestrictionFilter) = existingGraph == null || restrictionHash != hash(restrictionFilter)

    private fun hash(restrictionFilter: GraphRestrictionFilter) = serializer.toJson(restrictionFilter).hashCode()
}

