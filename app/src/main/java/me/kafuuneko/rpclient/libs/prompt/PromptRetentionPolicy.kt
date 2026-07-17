package me.kafuuneko.rpclient.libs.prompt

/** 单聊和群聊共享的 Prompt 保留优先级。 */
internal object PromptRetentionPolicy {
    const val EXAMPLE = 10
    const val HISTORY = 100
    const val PINNED_EXAMPLE = 200

    fun examplePriority(behavior: ExampleDialogueBehavior): Int {
        return when (behavior) {
            ExampleDialogueBehavior.Normal -> EXAMPLE
            ExampleDialogueBehavior.Pinned -> PINNED_EXAMPLE
            ExampleDialogueBehavior.Disabled -> error("Disabled examples have no retention priority")
        }
    }
}
