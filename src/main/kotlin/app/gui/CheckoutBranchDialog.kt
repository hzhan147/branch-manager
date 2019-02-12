package app.gui;

import app.ProjectUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.util.ui.JBUI
import git4idea.GitLocalBranch
import git4idea.GitUsagesTriggerCollector
import git4idea.branch.GitBrancher
import git4idea.repo.GitRepository
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.event.PopupMenuEvent
import javax.swing.table.DefaultTableModel

class CheckoutBranchDialog(private val project: Project) : DialogWrapper(project) {

    companion object {
        private val log = Logger.getInstance(CheckoutBranchDialog::class.java)
    }

    private lateinit var mainPanel: JPanel
    private lateinit var reposTable: JTable
    private lateinit var branchComboBox: JComboBox<String>

    private val reposMap: Map<String, GitRepository> =
            ProjectUtil.listRepositories(project).map { it.root.name to it }.toMap()

    private val projectListTableModel: ProjectListTableModel = ProjectListTableModel(reposMap)

    init {
        log.info("Project list dialog initialized for project $project")
        title = "Create branch"

        init()
    }

    override fun init() {
        initBranchComboBox()
        initReposTable()
        super.init()
    }

    private fun initBranchComboBox() {
        branchComboBox.addPopupMenuListener(object: PopupMenuListenerAdapter() {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                branchComboBox.removeAllItems()
                findCommonBranchesInRepos().forEach {
                    branchComboBox.addItem(it.name)
                }
            }
        })
    }

    private fun findCommonBranchesInRepos(): Set<GitLocalBranch> {
        val selectedRepos: Map<GitRepository, MutableCollection<GitLocalBranch>> = projectListTableModel.getSelectRepositories()
                .map { it to it.branches.localBranches }
                .toMap()

        var commonBranches = mutableSetOf<GitLocalBranch>();
        for (value in selectedRepos.values) {
            if (commonBranches.isEmpty()) {
                commonBranches.addAll(value)
            }

            commonBranches = commonBranches.intersect(value).toMutableSet()
        }

        return commonBranches
    }

    private fun initReposTable() {
        val tableModel = projectListTableModel
        reposTable.model = tableModel
        reposTable.rowHeight = JBUI.scale(22)

        val columnModel = reposTable.columnModel
        val selectColumn = columnModel.getColumn(0)
        selectColumn.headerValue = "Select"
        selectColumn.maxWidth = 50

        val repoColumn = columnModel.getColumn(1)
        repoColumn.headerValue = "Repository"
        repoColumn.maxWidth = 200

        val branchColumn = columnModel.getColumn(2)
        branchColumn.headerValue = "Branch"
        branchColumn.maxWidth = 300
    }

    override fun createCenterPanel(): JComponent? {
        return mainPanel
    }

    override fun doOKAction() {
        val branchToCheckout = branchComboBox.selectedItem as String
        val checkoutBranchRes = MessageDialogBuilder.yesNo(
                "Checkout branch",
                "Checkout branch '$branchToCheckout'?"
        ).noText("Cancel").show()

        if (checkoutBranchRes == Messages.YES) {
            checkoutBranch(branchToCheckout)
            // close parent dialog
            super.doOKAction()
        }
    }

    private fun checkoutBranch(branchToCheckout: String) {
        GitUsagesTriggerCollector.reportUsage(project, "git.branch.create.new")
        val gitBrancher = GitBrancher.getInstance(project)
        gitBrancher.checkout(
                branchToCheckout,
                false,
                projectListTableModel.getSelectRepositories(),
                null
        )
    }

    class ProjectListTableModel(private val reposMap: Map<String, GitRepository>) : DefaultTableModel() {

        companion object {
            private val COLUMN_CLASS = arrayOf(java.lang.Boolean::class.java, String::class.java, String::class.java)
            private val COLUMN_NAME = arrayOf("Select", "Repository", "Branch")
        }

        init {
            COLUMN_NAME.forEach { addColumn(it) }

            reposMap.keys
                    .sorted()
                    .forEach { addRow(arrayOf(false, it, reposMap[it]?.currentBranchName)) }
        }

        override fun isCellEditable(row: Int, column: Int): Boolean = column == 0

        override fun getColumnClass(columnIndex: Int): Class<*> = COLUMN_CLASS[columnIndex]

        override fun getColumnCount(): Int = 3

        fun getSelectRepositories(): MutableList<GitRepository> {
            val selectRepos = mutableListOf<GitRepository>()

            for (r in 0 until rowCount) {
                val isRepoSelected = getValueAt(r, 0) as Boolean
                if (isRepoSelected) {
                    val repoName = getValueAt(r, 1) as String
                    selectRepos.add(reposMap[repoName]!!)
                }
            }

            return selectRepos
        }

    }

}