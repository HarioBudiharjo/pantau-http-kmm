package com.pantauhttp.ui

import com.pantauhttp.HttpTransaction
import com.pantauhttp.TransactionState

internal enum class StatusFilter(val label: String) {
    All("All"),
    Success("2xx"),
    Redirect("3xx"),
    ClientError("4xx"),
    ServerError("5xx"),
    Failed("Failed"),
    ;

    fun matches(transaction: HttpTransaction): Boolean = when (this) {
        All -> true
        Failed -> transaction.state == TransactionState.Failed
        Success -> transaction.statusCode in 200..299
        Redirect -> transaction.statusCode in 300..399
        ClientError -> transaction.statusCode in 400..499
        ServerError -> transaction.statusCode in 500..599
    }
}
