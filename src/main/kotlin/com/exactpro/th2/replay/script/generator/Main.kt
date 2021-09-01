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

@file:JvmName("Main")

package com.exactpro.th2.replay.script.generator

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.message.fromJson
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.replay.script.generator.api.IActionFactory
import com.exactpro.th2.replay.script.generator.api.IScript
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.file
import jakarta.ws.rs.client.ClientBuilder
import jakarta.ws.rs.sse.SseEventSource
import mu.KotlinLogging
import java.util.ServiceLoader

private val LOGGER = KotlinLogging.logger {}

private val MAPPER = YAMLMapper.builder()
    .addModule(KotlinModule(nullIsSameAsDefault = true))
    .build()

class ReplayScriptGenerator : CliktCommand() {
    private val replayUrl by option(
        metavar = "URL",
        help = "replay URL from th2 report"
    ).required()

    private val outputDir by option(
        help = "directory for a generated script"
    ).file(
        canBeFile = false,
    ).required().validate { file ->
        require(file.exists() || file.mkdirs()) { "Failed to create output directory: ${file.canonicalPath}" }
    }

    private val config by option(
        metavar = "FILE",
        help = "path to the tool config file",
        names = arrayOf("--config-file"),
    ).file(
        mustExist = true,
        canBeDir = false,
        mustBeReadable = true
    ).convert { file ->
        MAPPER.readValue<Configuration?>(file) ?: Configuration()
    }.default(Configuration())

    override fun run() = try {
        val script = loadSingle<IScript>().apply { init(outputDir) }

        val factories = load<IActionFactory>().onEach { factory ->
            val settings = when (val node = config.actions[factory.actionName]) {
                null -> factory.settingsClass.getDeclaredConstructor().newInstance()
                else -> try {
                    MAPPER.treeToValue(node, factory.settingsClass)
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to load '${factory.actionName}' action settings", e)
                }
            }

            factory.init(settings)
        }

        val generator = ScriptGenerator(script, factories, MessageTransformer(config.transform, config.sessionAliases))
        val client = ClientBuilder.newBuilder().build()
        val target = client.target(replayUrl)
        val source = SseEventSource.target(target).build()

        val mapper = JsonMapper.builder()
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .addModule(KotlinModule(nullIsSameAsDefault = true))
            .build()

        source.register { event ->
            if (!source.isOpen) return@register
            LOGGER.debug { "Received event: ${event.name}" }

            when (event.name) {
                "message" -> when (val message = mapper.readValue<MessageEvent>(event.readData()).message) {
                    null -> LOGGER.warn { "Skipping event with null message: $event" }
                    else -> {
                        LOGGER.info { "Received message: ${message.toJson()}" }
                        generator.onMessage(message)
                    }
                }
                "error" -> LOGGER.error { "Received error event: $event" }
                "close" -> {
                    LOGGER.info { "Closing" }
                    generator.shutdown()
                    script.shutdown()
                    source.shutdown()
                    client::close.shutdown("client")
                    LOGGER.info { "Closed" }
                }
            }
        }

        source.open()
        LOGGER.info { "Started" }
        while (source.isOpen) Thread.sleep(100)
        LOGGER.info { "Finished" }
    } catch (e: Exception) {
        LOGGER.error(e) { "Unhandled exception" }
    }
}

fun main(args: Array<String>) = ReplayScriptGenerator().main(args)

data class MessageEvent(val body: JsonNode) {
    @JsonIgnore val message: Message? = when {
        body.isNull -> null
        else -> Message.newBuilder().fromJson(body.toString()).build()
    }
}

private inline fun <reified T> load(): List<T> = ServiceLoader.load(T::class.java).toList().apply {
    check(isNotEmpty()) { "No instances of ${T::class.simpleName}" }
}

private inline fun <reified T> loadSingle(): T = load<T>().run {
    when (size) {
        1 -> first()
        else -> error("More than 1 instance of ${T::class.simpleName} has been found: $this")
    }
}

private fun (() -> Unit).shutdown(name: String) = runCatching(this).getOrElse { LOGGER.error(it) { "Failed to close $name" } }
private fun AutoCloseable.shutdown() = ::close.shutdown(this::class.simpleName!!)