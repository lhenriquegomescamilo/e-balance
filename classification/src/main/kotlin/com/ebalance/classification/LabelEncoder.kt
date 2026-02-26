package com.ebalance.classification

import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

class LabelEncoder(labels: List<String>) {

    val labels = labels.distinct().toPersistentList()

    private val labelToIndex = labels.withIndex().associate { it.value to it.index }.toPersistentMap()

    fun encode(label: String) = labelToIndex[label] ?: error("Unknown label $label")

    fun decode(index: Int) = labels[index]
}