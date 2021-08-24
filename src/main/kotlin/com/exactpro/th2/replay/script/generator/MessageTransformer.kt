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

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.replay.script.generator.Command.Operation.ADD
import com.exactpro.th2.replay.script.generator.Command.Operation.PUT
import com.exactpro.th2.replay.script.generator.Command.Operation.REMOVE
import com.exactpro.th2.replay.script.generator.Command.Operation.SET
import com.exactpro.th2.replay.script.generator.util.logId
import com.exactpro.th2.replay.script.generator.util.toMap
import com.exactpro.th2.replay.script.generator.util.toProtoBuilder
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.AS_PATH_LIST
import com.jayway.jsonpath.PathNotFoundException
import mu.KotlinLogging

class MessageTransformer(
    private val transforms: Map<MessageProtocol, MessageTransform>,
    private val sessionAliases: Map<String, String>
) {
    fun transform(message: Message): Message = message.run {
        LOGGER.info { "Transforming message: $logId" }

        commands?.run {
            return toMap().applyCommands(this).toProtoBuilder().run {
                parentEventId = message.parentEventId
                metadata = message.metadata
                sessionAlias = sessionAliases[sessionAlias] ?: sessionAlias
                build()
            }
        }

        return when (sessionAlias in sessionAliases) {
            false -> this
            else -> toBuilder().apply { sessionAlias = sessionAliases[sessionAlias]!! }.build()
        }
    }

    private val Message.commands
        get() = transforms[metadata.protocol]?.get(messageType)

    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val PATH_CONFIG = Configuration.builder().options(AS_PATH_LIST).build()

        private fun Map<String, Any?>.getMatchingPaths(path: JsonPath): List<JsonPath> = try {
            path.read<List<String>>(this, PATH_CONFIG).map(JsonPath::compile)
        } catch (e: PathNotFoundException) {
            listOf()
        }

        private operator fun DocumentContext.get(path: JsonPath): Any? = try {
            read<Any?>(path)
        } catch (e: PathNotFoundException) {
            null
        }

        private fun JsonPath.resolve(path: JsonPath?): JsonPath = when {
            path == null -> this
            path.path[0] == '@' -> JsonPath.compile(this.path + path.path.drop(1))
            else -> path
        }

        private val Any?.className: String?
            get() = this?.run { this::class.simpleName }

        fun MutableMap<String, out Any?>.applyCommands(commands: List<Command>) = apply {
            val context = JsonPath.parse(this)

            commands.forEach { command ->
                LOGGER.info { "Executing command: $command" }

                val paths = getMatchingPaths(command.path).ifEmpty {
                    LOGGER.info { "No paths matching: ${command.path.path}" }
                    return@forEach
                }

                paths.forEach { path ->
                    LOGGER.info { "Processing path: ${path.path}" }
                    command.applyTo(context, path)
                }
            }
        }

        private fun Command.applyTo(context: DocumentContext, path: JsonPath) = context.run {
            condition?.run {
                val valuePath = path.resolve(valuePath)
                val actualValue = get(valuePath)

                LOGGER.debug { "Expecting that value at '${valuePath.path}' is: $expectedValue (${expectedValue.className})" }

                if (expectedValue != actualValue) {
                    LOGGER.info { "Skipping because it is: $actualValue (${actualValue.className})" }
                    return
                }
            }

            val value = value ?: valuePath?.run {
                val valuePath = path.resolve(this)
                LOGGER.info { "Using value from: ${valuePath.path}" }
                get(valuePath)
            }

            when (operation) {
                ADD -> {
                    add(path, value)
                    LOGGER.info { "Successfully added value '$value' (${value.className}) to: ${path.path}" }
                }
                PUT -> {
                    put(path, field, value)
                    LOGGER.info { "Successfully put field '$field' with value '$value' (${value.className}) at: ${path.path}" }
                }
                SET -> {
                    set(path, value)
                    LOGGER.info { "Successfully set value '$value' (${value.className}) to: ${path.path}" }
                }
                REMOVE -> {
                    delete(path)
                    LOGGER.info { "Successfully removed: ${path.path}" }
                }
            }
        }
    }
}