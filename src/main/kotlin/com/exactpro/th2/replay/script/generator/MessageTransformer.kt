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
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.replay.script.generator.Action.CHECK_AND_REPLACE
import com.exactpro.th2.replay.script.generator.Action.REMOVE
import com.exactpro.th2.replay.script.generator.Action.REPLACE
import com.exactpro.th2.replay.script.generator.util.toMap
import com.exactpro.th2.replay.script.generator.util.toProtoBuilder
import mu.KotlinLogging

class MessageTransformer(
    private val transforms: Map<MessageProtocol, MessageTransformations>,
    private val sessionAliases: Map<String, String>
) {
    fun transform(message: Message): Message = getTransform(message)?.let { fields ->
        LOGGER.info { "Transforming message: ${message.toJson()}" }

        message.toMap().run {
            transform(listOf(), fields)
            toProtoBuilder().run {
                parentEventId = message.parentEventId
                metadata = message.metadata
                sessionAlias = sessionAliases[sessionAlias] ?: sessionAlias
                build()
            }
        }
    } ?: message.run {
        when (sessionAlias in sessionAliases) {
            false -> this
            else -> {
                LOGGER.info { "Transforming message: ${message.toJson()}" }
                toBuilder().apply { sessionAlias = sessionAliases[sessionAlias]!! }.build()
            }
        }
    }

    private fun getTransform(message: Message) = transforms[message.metadata.protocol]?.get(message.messageType)

    companion object {
        private val LOGGER = KotlinLogging.logger {}

        private fun <T> T.transform(path: List<String>, transforms: FieldTransformations) {
            when (this) {
                is MutableList<*> -> (this as MutableList<Any?>).transform(path, transforms)
                is MutableMap<*, *> -> (this as MutableMap<String, Any?>).transform(path, transforms)
            }
        }

        private fun MutableMap<String, Any?>.transform(path: List<String>, fields: FieldTransformations) {
            val iterator = iterator()

            while (iterator.hasNext()) {
                val entry = iterator.next()
                val subPath = path + entry.key

                fields.entries.firstOrNull { (path, _) ->
                    path.matches(subPath)
                }?.let { (_, transform) ->
                    when (transform.action) {
                        REMOVE -> iterator.remove().also {
                            LOGGER.debug { "Removed value '$it', path '$subPath'" }
                        }
                        REPLACE -> entry.setValue(transform.value).also {
                            LOGGER.debug { "Replaced from '$it' to '${transform.value}', path '$subPath'" }
                        }
                        CHECK_AND_REPLACE -> if (entry.value == transform.expected) {
                            entry.setValue(transform.value)
                            LOGGER.debug { "Replaced from '${transform.expected}' to '${transform.value}', path '$subPath'" }
                        } else {
                            LOGGER.debug { "Transformation '$transform' skipped because expected value didn't match actual '${entry.value}', path $subPath" }
                        }
                    }
                } ?: run {
                    entry.value.transform(subPath, fields)
                }
            }
        }

        private fun MutableList<Any?>.transform(path: List<String>, fields: FieldTransformations) {
            for (index in lastIndex downTo 0) {
                val subPath = path + index.toString()

                fields.entries.firstOrNull { (path, _) ->
                    path.matches(subPath)
                }?.let { (_, transform) ->
                    when (transform.action) {
                        REMOVE -> removeAt(index)?.let {
                            LOGGER.debug { "Removed value '$it', path '$subPath'" }
                        }
                        REPLACE -> set(index, transform.value).also {
                            LOGGER.debug { "Replaced from '$it' to '${transform.value}', path '$subPath'" }
                        }
                        CHECK_AND_REPLACE -> if (get(index) == transform.expected) {
                            set(index, transform.value)
                            LOGGER.debug { "Replaced from '${transform.expected}' to '${transform.value}', path '$subPath'" }
                        } else {
                            LOGGER.debug { "Transformation '$transform' skipped because expected value didn't match actual '${get(index)}', path $subPath" }
                        }
                    }
                } ?: run {
                    get(index).transform(subPath, fields)
                }
            }
        }
    }
}