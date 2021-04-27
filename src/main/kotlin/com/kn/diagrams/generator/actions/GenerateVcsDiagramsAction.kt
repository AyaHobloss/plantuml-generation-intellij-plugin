package com.kn.diagrams.generator.actions

import com.intellij.psi.PsiClass
import com.kn.diagrams.generator.config.VcsConfiguration
import com.kn.diagrams.generator.config.VcsDiagramDetails
import com.kn.diagrams.generator.generator.createVcsContent
import com.kn.diagrams.generator.settings.ConfigurationDefaults


class GenerateVcsDiagramsAction : AbstractDiagramAction<VcsConfiguration>() {

    override fun createDiagramContent(configuration: VcsConfiguration) = createVcsContent(configuration)

    override fun defaultConfiguration(rootClass: PsiClass): VcsConfiguration {
        val defaults = ConfigurationDefaults.clusterDiagram()
        return VcsConfiguration(rootClass,
                ConfigurationDefaults.classification(),
                defaults.graphRestriction,
                defaults.graphTraversal,
                VcsDiagramDetails()
        )
    }

}
