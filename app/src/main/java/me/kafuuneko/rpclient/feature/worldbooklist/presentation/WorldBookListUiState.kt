package me.kafuuneko.rpclient.feature.worldbooklist.presentation

import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem

/** 世界书列表页状态。 */
sealed class WorldBookListUiState {
    data object None : WorldBookListUiState()

    data class Normal(
        val loadState: WorldBookListLoadState = WorldBookListLoadState.None,
        val lorebooks: List<WorldBookListItem> = emptyList(),
        val dialogState: WorldBookListDialogState = WorldBookListDialogState.None
    ) : WorldBookListUiState()

    data class Finished(val previous: WorldBookListUiState) : WorldBookListUiState()

    companion object {
        fun finished(previous: WorldBookListUiState): WorldBookListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 世界书列表页对话框状态。 */
sealed class WorldBookListDialogState {
    data object None : WorldBookListDialogState()

    data class LowTokenBudgetConfirm(
        val importedTokenBudget: Int
    ) : WorldBookListDialogState()
}

/** 世界书列表读取或导入期间的加载状态。 */
sealed class WorldBookListLoadState {
    data object None : WorldBookListLoadState()
    data object Loading : WorldBookListLoadState()
}
