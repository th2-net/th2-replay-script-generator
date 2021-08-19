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

import com.exactpro.th2.replay.script.generator.Command
import com.exactpro.th2.replay.script.generator.Command.Condition
import com.exactpro.th2.replay.script.generator.MessageTransformer.Companion.apply
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TestMessageTransformer {
    @Nested
    inner class Positive {
        @Test fun `add value`() {
            val command = Command(addPath = "$.list".toPath(), value = 123)
            val expected = mapOf("list" to listOf(123))
            val initial = mutableMapOf("list" to mutableListOf<Any>())
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `put value`() {
            val command = Command(putPath = "$".toPath(), field = "field", value = 124)
            val expected = mapOf("field" to 124)
            val initial = mutableMapOf<String, Any>()
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `set value`() {
            val command = Command(setPath = "$.field".toPath(), value = 125)
            val expected = mapOf("field" to 125)
            val initial = mutableMapOf("field" to 0)
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `remove value`() {
            val command = Command(removePath = "$.field".toPath())
            val initial = mutableMapOf("field" to 126)
            val expected = mapOf<String, Any>()
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `remove non-existent value`() {
            val command = Command(removePath = "$.field".toPath())
            val initial = mutableMapOf("value" to 0)
            val expected = mapOf("value" to 0)
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `remove value from list`() {
            val command = Command(removePath = "$.list[1]".toPath())
            val expected = mapOf("list" to listOf(1, 3))
            val initial = mutableMapOf("list" to mutableListOf(1, 2, 3))
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `put value from field`() {
            val command = Command(putPath = "$".toPath(), field = "child", valuePath = "$.parent".toPath())
            val expected = mapOf("parent" to 127, "child" to 127)
            val initial = mutableMapOf("parent" to 127)
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `put value from nested field`() {
            val command = Command(putPath = "$.field".toPath(), field = "child", valuePath = "@.parent".toPath())
            val expected = mapOf("field" to mapOf("parent" to 128, "child" to 128))
            val initial = mutableMapOf("field" to mutableMapOf("parent" to 128))
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `remove field if it is == expected value`() {
            val command = Command(removePath = "$.field".toPath(), condition = Condition(expectedValue = 129))
            val expected = mapOf<String, Any>()
            val initial = mutableMapOf("field" to 129)
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `remove field if it is != expected value`() {
            val command = Command(removePath = "$.field".toPath(), condition = Condition(expectedValue = 130))
            val expected = mapOf("field" to 129)
            val initial = mutableMapOf("field" to 129)
            assertEquals<Any>(expected, command.applyTo(initial))
        }

        @Test fun `put value if nested field is == expected value`() {
            val command = Command(putPath = "$.field".toPath(), field = "child", value = 130, condition = Condition(valuePath = "@.parent".toPath(), expectedValue = 131))
            val expected = mapOf("field" to mapOf("child" to 130, "parent" to 131))
            val initial = mutableMapOf("field" to mutableMapOf("parent" to 131))
            assertEquals<Any>(expected, command.applyTo(initial))
        }
    }

    @Nested
    inner class Negative {
        @Test fun `no operation`() {
            assertException<IllegalArgumentException>("No operation is set", ::Command)
        }

        @Test fun `multiple operations`() {
            assertException<IllegalArgumentException>("More than one operation is set") {
                Command(addPath = "$".toPath(), putPath = "$".toPath())
            }
        }

        @Test fun `no key for put operation`() {
            assertException<IllegalArgumentException>("'field' is required for 'put' operations") {
                Command(putPath = "$".toPath())
            }
        }

        @Test fun `key for remove operation`() {
            assertException<IllegalArgumentException>("'field' is forbidden for non-'put' operations") {
                Command(removePath = "$.field".toPath(), field = "field")
            }
        }

        @Test fun `value for remove operation`() {
            assertException<IllegalArgumentException>("'value' / 'value-from' are forbidden for 'remove' operations") {
                Command(removePath = "$.field".toPath(), value = 0)
            }

            assertException<IllegalArgumentException>("'value' / 'value-from' are forbidden for 'remove' operations") {
                Command(removePath = "$.field".toPath(), valuePath = "$.parent".toPath())
            }
        }

        @Test fun `value and value-from are both set`() {
            assertException<IllegalArgumentException>("'value' / 'value-from' are mutually exclusive") {
                Command(addPath = "$.list".toPath(), value = 0, valuePath = "$.map".toPath())
            }
        }
    }

    companion object {
        private fun String.toPath(): JsonPath = JsonPath.compile(this)
        private fun Command.applyTo(map: MutableMap<String, out Any?>) = map.apply(listOf(this))
        private inline fun <reified T : Throwable> assertException(message: String, action: () -> Unit) = assertEquals(message, assertThrows<T>(action).message)
    }
}
