package com.kn.diagrams.generator.generator.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.kn.diagrams.generator.ProgressBar
import com.kn.diagrams.generator.config.CommitFilter
import com.kn.diagrams.generator.config.VcsConfiguration
import com.kn.diagrams.generator.graph.ClassReference
import com.kn.diagrams.generator.throwExceptionIfCanceled
import java.io.File
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.toList

class VcsAnalysis(val config: VcsConfiguration, val project: Project) {
    val rawCommits = mutableListOf<VcsCommit>()
    val filteredCommits = mutableListOf<VcsCommit>()

    val totalFiles: MutableList<ClassReference> = mutableListOf()

    constructor(config: VcsConfiguration, project: Project, commits: VcsAnalysis.() -> Unit, flow: VcsAnalysis.() -> Unit) : this(config, project) {
        commits(this)
        flow(this)
    }

    fun List<VcsCommit>.filterByConfiguration(): List<VcsCommit> {
        val filter = config.restrictionFilter()

        return mapNotNull { commit ->
            val changes = commit.files.filter { filter.acceptClass(it) }

            if (commit.message.matchesFilterPattern()) {
                VcsCommit(commit.time, commit.message, changes)
            } else null
        }
    }

    fun loadAndCacheRawCommits() {
        val cacheFile = config.projectCacheFile()

        if (cacheFile.exists()) {
            ProgressBar.text = "VCS repository is loaded from cache file"

            rawCommits.addAll(cacheFile.readCommits())

            readCommitsFromVcs(rawCommits.latestCommitTimestamp()).let { newChanges ->
                rawCommits.addAll(newChanges)
                cacheFile.appendText(newChanges.toCsv())
            }
        } else {
            ProgressBar.text = "VCS repository is loaded"

            rawCommits += readCommitsFromVcs()
            cacheFile.writeText(rawCommits.toCsv())
        }

        ProgressBar.text = "Commits are processed and filtered"
    }

    fun List<VcsCommit>.squashWithSameTicket(): List<VcsCommit> {
        if (!config.details.squashCommitsContainingOneTicketReference) return this

        return groupBy {
            val jiraTags = "\\[.{3,20}]".toRegex().findAll(it.message).map { it.value }.distinct().toList()

            jiraTags
        }.map { (jiraTags, commits) ->
            if (jiraTags.size == 1 && commits.size > 1) {
                val squashedChanges = commits.flatMap { it.files }.distinct()
                val lastTime = commits.map { it.time }.maxOfOrNull { it } ?: -1

                listOf(VcsCommit(lastTime, jiraTags[0] + " manual squashed commits", squashedChanges))
            } else {
                commits
            }
        }.flatten()
    }

    fun List<VcsCommit>.filterByTimeRange(): List<VcsCommit> {
        val timeRange = (config.details.startDay.toDate()?.time ?: 0L)..(config.details.endDay.toDate()?.time
                ?: Long.MAX_VALUE)
        return filter { commit -> commit.time in timeRange }
    }

    fun List<VcsCommit>.extractUniqueFiles(): List<ClassReference> {
        return parallelStream().flatMap { it.files.stream() }.distinct().toList()
    }


    private fun String.matchesFilterPattern() = when (config.details.commitFilter) {
        CommitFilter.All -> true
        CommitFilter.Matching -> config.details.commitContainsPattern.toRegex().containsMatchIn(this)
        CommitFilter.NotMatching -> !config.details.commitContainsPattern.toRegex().containsMatchIn(this)
    }

    private fun VcsConfiguration.projectCacheFile(): File {
        val branch = details.repositoryBranch
        val includingTests = if (graphRestriction.cutTests) "" else "_includingTests"
        return File(project.guessProjectDir()?.path + File.separator + "vcs_cache_$branch$includingTests.csv")
    }

    private fun readCommitsFromVcs(startTime: Long? = null): List<VcsCommit> {
        val changes = mutableListOf<VcsCommit>()

        VcsProjectLog.getLogProviders(project)
                .map { (root, logProvider) ->
                    val commitsHashes = logProvider.getCommitsMatchingFilter(root, VcsLogFilterObject.collection(VcsLogFilterObject.fromBranch(config.details.repositoryBranch)), Int.MAX_VALUE).parallelStream()
                            .filter { startTime == null || it.timestamp > startTime }
                            .map { it.id.asString() }
                            .toList()

                    logProvider.readFullDetails(root, commitsHashes) { commit ->
                        val changedFiles = commit.changes.parallelStream().filterSourceJavaFiles(config)

                        if (changedFiles.isNotEmpty()) {
                            changes.add(VcsCommit(
                                    commit.commitTime,
                                    commit.fullMessage.replace("\r", " ").replace(",", ";").replace("\n", " "),
                                    changedFiles.map { ClassReference(it) }
                            ))
                        }

                        throwExceptionIfCanceled()
                    }
                }

        return changes.asReversed()
    }

}


fun Stream<Change>.filterSourceJavaFiles(config: VcsConfiguration): List<String> = asSequence()
        .map { it.afterRevision?.file?.path?.substringAfter("/src/") ?: "" }
        .filter { it != "" }
        .filter { !config.graphRestriction.cutTests || !it.startsWith("test") }
        .filter { it.endsWith(".java")}
        .map {
            it
                    .substringAfter("test/")
                    .substringAfter("main/")
                    .substringAfter("java/")
                    .replace("/", ".")
                    .replace(".java", "")
        }
        .toList()

fun List<VcsCommit>.toCsv() = joinToString("\n") { it.toCsv() }
fun List<VcsCommit>.latestCommitTimestamp() = asSequence().map { it.time }.maxOrNull()
fun File.readCommits() = readLines().map { VcsCommit(it) }

fun String?.toDate(): Date? {
    if (this == null || this == "") return null
    val (year, month, day) = this.split("-")
    val calendar = Calendar.getInstance()
    calendar.set(year.toInt(), month.toInt() - 1, day.toInt())

    return calendar.time
}


class UndirectedEdge<T>(val from: T, val to: T) {

    constructor(values: List<T>) : this(values[0], values[1])

    fun isLoop() = from == to

    fun contains(it: T) = from == it || to == it

    fun <E> transform(mapping: T.() -> E) = UndirectedEdge(sortedNodes().map(mapping))

    fun sortedNodes() = sequenceOf(from, to).sortedBy { it.hashCode() }.toList()

    override fun hashCode(): Int {
        return 31 * sortedNodes().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UndirectedEdge<*>) return false

        if (sortedNodes() != other.sortedNodes()) return false

        return true
    }

}

class VcsCommit(
        val time: Long,
        val message: String,
        val files: List<ClassReference>
) {
    constructor(line: List<String>) : this(line[0].toLong(), line[1], line[2].split(";").map { ClassReference(it) })

    constructor(line: String) : this(line.split(","))

    fun toCsv() = "$time,$message," + files.joinToString(";") { it.qualifiedName() }

    fun allCombinations() = files
            .flatMap { first ->
                files.map { second -> UndirectedEdge(first, second) to this }
            }
}
