package me.kafuuneko.rpclient.model

/** 应用内共用的 Token 数值快捷预设。 */
enum class TokenPreset(
    val displayName: String,
    val value: Int
) {
    K8("8K", 8192),
    K16("16K", 16384),
    K32("32K", 32768),
    K64("64K", 65536),
    K128("128K", 131072),
    K256("256K", 262144),
    K512("512K", 524288),
    M1("1M", 1048576)
}
