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

import com.exactpro.th2.replay.script.generator.Command.Operation.ADD
import com.exactpro.th2.replay.script.generator.Command.Operation.PUT
import com.exactpro.th2.replay.script.generator.Command.Operation.REMOVE
import com.exactpro.th2.replay.script.generator.Command.Operation.SET
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.util.StdConverter
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.JsonPath

typealias MessageType = String
typealias MessageProtocol = String
typealias MessageTransform = Map<MessageType, List<Command>>

data class Configuration(
    val transform: Map<MessageProtocol, MessageTransform> = emptyMap(),
    val sessionAliases: Map<String, String> = emptyMap(),
    val actions: Map<String, JsonNode> = emptyMap()
) {
    init {
        transform.forEach { (protocol, messages) ->
            messages.forEach { (message, transformations) ->
                require(transformations.isNotEmpty()) { "Empty transformation for $protocol message: $message" }
            }
        }
    }
}

data class Command(
    @JsonDeserialize(converter = JsonPathConverter::class) @JsonProperty("add") val addPath: JsonPath? = null,
    @JsonDeserialize(converter = JsonPathConverter::class) @JsonProperty("put") val putPath: JsonPath? = null,
    @JsonDeserialize(converter = JsonPathConverter::class) @JsonProperty("set") val setPath: JsonPath? = null,
    @JsonDeserialize(converter = JsonPathConverter::class) @JsonProperty("remove") val removePath: JsonPath? = null,
    val field: String? = null,
    val value: Any? = null,
    @JsonDeserialize(converter = JsonPathConverter::class) @JsonProperty("value-from") val valuePath: JsonPath? = null,
    @JsonProperty("only-if") val condition: Condition? = null
) {
    val path: JsonPath by lazy { listOfNotNull(addPath, putPath, setPath, removePath)[0] }

    val operation: Operation = when {
        addPath != null -> ADD
        putPath != null -> PUT
        setPath != null -> SET
        else -> REMOVE
    }

    init {
        val paths = listOfNotNull(addPath, putPath, setPath, removePath)
        require(paths.isNotEmpty()) { "No operation is set" }
        require(paths.size == 1) { "More than one operation is set" }
        require(putPath == null || field != null) { "'field' is required for 'put' operations" }
        require(putPath != null || field == null) { "'field' is forbidden for non-'put' operations" }
        require(removePath != null || value == null || valuePath == null) { "'value' / 'value-from' are mutually exclusive" }
        require(removePath == null || value == null && valuePath == null) { "'value' / 'value-from' are forbidden for 'remove' operations" }
    }

    override fun toString(): String = buildString {
        append(operation.name.lowercase())
        field?.apply { append(" field '$this' with") }
        value?.apply { append(" value '$this'") }
        valuePath?.apply { append(" value from '$path'") }
        append(" at '${path.path}'")
        condition?.apply {
            append(" only if value")
            valuePath?.apply { append(" at '$path'") }
            append(" is equal to '$expectedValue'")
        }
    }

    enum class Operation { ADD, PUT, SET, REMOVE }

    data class Condition(
        @JsonDeserialize(converter = JsonPathConverter::class) @JsonProperty("value-of") val valuePath: JsonPath? = null,
        @JsonProperty("is-equal-to") val expectedValue: Any? = null
    )
}

private class JsonPathConverter : StdConverter<String, JsonPath>() {
    override fun convert(value: String): JsonPath = value.runCatching(JsonPath::compile).getOrElse {
        throw IllegalArgumentException("Invalid path: $value", it)
    }
}
