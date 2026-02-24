package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

/** Named payment account (e.g. "Chase Checking"). No credentials stored; used only to tag which bills are paid from which account. */
@Serializable
data class BankAccount(
    val id: String = "",
    val name: String = ""
)
