/*
 * Copyright 2021-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.replay.script.generator

import com.exactpro.th2.replay.script.generator.Action.CHECK_AND_REPLACE
import com.exactpro.th2.replay.script.generator.Action.REMOVE
import com.exactpro.th2.replay.script.generator.Action.REPLACE
import com.fasterxml.jackson.databind.JsonNode
import java.util.SortedMap

typealias MessageType = String
typealias MessageProtocol = String
typealias MessageTransformations = Map<MessageType, FieldTransformations>
typealias FieldTransformations = SortedMap<FieldPath, FieldTransformation>

data class Configuration(
    val transform: Map<MessageProtocol, MessageTransformations> = emptyMap(),
    val sessionAliases: Map<String, String> = emptyMap(),
    val actions: Map<String, JsonNode> = emptyMap()
) {
    init {
        transform.forEach { (protocol, messages) ->
            messages.forEach { (message, fields) ->
                require(fields.isNotEmpty()) { "Empty transformation for $protocol message: $message" }
            }
        }
    }
}

data class FieldTransformation(val action: Action, val value: String? = null, val expected: String? = null) {
    init {
        when (action) {
            REMOVE -> require(value == null && expected == null) { "Remove transformations cannot have value and expected settings" }
            REPLACE -> require( value != null && expected == null) { "Replace transformations must have value and cannot have expected settings" }
            CHECK_AND_REPLACE -> require(value != null && expected != null) { "Check and replace transformations must have value and expected settings" }
        }
    }
}

class FieldPath(private val path: String): Comparable<FieldPath> {
    private val predicates: List<(String) -> Boolean> = path.split('.').run {
        require(isNotEmpty()) { "Empty field path" }

        forEach { element ->
            require(element.isNotBlank()) { "Empty element in field path: $path" }
        }

        map { element ->
            when (element) {
                "*" -> { { true } }
                else -> element::equals
           }
        }
    }

    fun matches(elements: List<String>): Boolean = when {
        elements.size != predicates.size -> false
        else -> predicates.zip(elements).all { (predicate, element) -> predicate(element) }
    }

    override fun compareTo(other: FieldPath): Int = compareValuesBy(
        this,
        other,
        { it.path.length },
        FieldPath::path
    )

    override fun toString(): String = path
}

enum class Action {
    REPLACE,
    CHECK_AND_REPLACE,
    REMOVE
}