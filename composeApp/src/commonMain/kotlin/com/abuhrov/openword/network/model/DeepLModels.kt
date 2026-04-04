package com.abuhrov.openword.network.model

import kotlinx.serialization.Serializable

@Serializable
data class DeepLRequest(
    val text: String
)

@Serializable
data class DeepLBatchRequest(
    val texts: List<String>
)

@Serializable
data class DeepLResponse(
    val translations: List<DeepLTranslation>
)

@Serializable
data class DeepLTranslation(
    val text: String
)
