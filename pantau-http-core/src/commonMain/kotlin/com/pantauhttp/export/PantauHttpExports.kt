package com.pantauhttp.export

import com.pantauhttp.HttpTransaction

/** Share/export formats. Headers are already redacted by the store. */
public object PantauHttpExports {
    /** A runnable `curl -v` command. */
    public fun curl(transaction: HttpTransaction): String = CurlExporter.export(transaction)

    /** Human-readable plain-text dump. */
    public fun text(transaction: HttpTransaction): String = TextExporter.export(transaction)

    /** HAR 1.2 JSON for one or more transactions. */
    public fun har(transactions: List<HttpTransaction>): String = HarExporter.export(transactions)

    public fun har(transaction: HttpTransaction): String = HarExporter.export(transaction)
}
