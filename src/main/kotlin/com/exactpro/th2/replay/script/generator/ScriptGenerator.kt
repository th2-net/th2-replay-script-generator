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
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.replay.script.generator.api.IAction
import com.exactpro.th2.replay.script.generator.api.IActionFactory
import com.exactpro.th2.replay.script.generator.api.IScriptBlock
import com.exactpro.th2.replay.script.generator.api.IScriptContext
import mu.KotlinLogging

class ScriptGenerator(
    private val scriptContext: IScriptContext,
    private val actionFactories: List<IActionFactory>,
    private val messageTransformer: MessageTransformer,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val actions = mutableListOf<IAction>()

    fun onMessage(message: Message) {
        logger.info { "Processing message: ${message.toJson()}" }

        messageTransformer.transform(message).process()
    }

    override fun close() = actions.asSequence()
        .flatMap(IAction::complete)
        .sortedBy(IScriptBlock::timestamp)
        .forEach { it.writeTo(scriptContext) }

    private fun Message.process() {
        logger.info { "Transformed message: ${toJson()}" }

        val added = actions.addAll(actionFactories.mapNotNull { factory ->
            factory.from(this)?.also { action ->
                logger.info { "Created action: $action" }
            }
        })

        var updated = false

        actions.removeIf { action ->
            updated = runCatching(action::update).getOrElse {
                logger.error(it) { "Failed action: $action" }
                return@removeIf true
            } || updated

            false
        }

        if (!added && !updated) {
            logger.warn { "Skipping message: ${toJson()}" }
        }
    }
}