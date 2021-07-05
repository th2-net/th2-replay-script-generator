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
package com.exactpro.th2.replay.script.generator.impl

import mu.KotlinLogging
import java.io.Writer

class ScriptSection(private val name: String) {
    private val logger = KotlinLogging.logger { }
    private val blocks = mutableListOf<Writer.() -> Unit>()
    private var printed: Boolean = false

    private val before = linkedMapOf<String, ScriptSection>()
    private val after = linkedMapOf<String, ScriptSection>()

    fun before(block: Writer.() -> Unit): ScriptSection = this.also {
        checkState()
        blocks.add(0, block)
    }

    fun after(block: Writer.() -> Unit): ScriptSection = this.also {
        checkState()
        blocks.add(block)
    }

    /**
     * Registers the section which prints before current.
     * The print method of each similar section will be called before printing of the current section.
     * @return regitered section
     */
    fun before(section: ScriptSection) = section.also {
        checkState()
        checkSection(name)
        before.putIfAbsent(section.name, section)
    }

    /**
     * Registers the section which prints after current
     * The print method of each similar section will be called after printing of the current section
     * @return regitered section
     */
    fun after(section: ScriptSection) = section.also {
        checkState()
        checkSection(name)
        after.putIfAbsent(section.name, section)
    }

    fun print(writer: Writer) {
        before.values.reversed().forEach { it.print(writer) }
        if (!printed) {
            logger.debug { "Started printing section: $name" }
            blocks.forEach { writer.it() }
            printed = true
            logger.debug { "Finished printing section: $name" }
        }
        after.values.forEach { it.print(writer) }
    }

    private fun checkSection(name: String) {
        check(name != this.name) {
            "The $name section can't be dependence onto itself"
        }
        check(!before.containsKey(name)) {
            "The $name section is already registred as before section"
        }
        check(!after.containsKey(name)) {
            "Section $name is already registered as after section"
        }
    }

    private fun checkState() {
        check(!printed) {
            "Operation can't be executed because the $name section is printed"
        }
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is ScriptSection -> false
        else -> name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
