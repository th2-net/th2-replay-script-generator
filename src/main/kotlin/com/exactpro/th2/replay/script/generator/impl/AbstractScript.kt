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

import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.replay.script.generator.api.IScript
import com.google.protobuf.MessageOrBuilder
import java.io.File
import java.io.Writer

abstract class AbstractScript: IScript {
    protected lateinit var scriptDirectory: File

    private lateinit var messagesDirectory: File

    override fun init(scriptDirectory: File) {
        this.scriptDirectory = scriptDirectory.createDir()
        this.messagesDirectory = File(scriptDirectory, "messages").createDir()
    }

    override fun registerResource(uid: String, resource: Any) {
        when(resource) {
            is MessageOrBuilder -> resource.save(messagesDirectory, uid)
            else -> error("Script could not able to register instance of ${resource::class.java}")
        }
    }

    private fun File.createDir() = this.apply {
        if (!exists()) {
            check(mkdirs()) { "Failed to create directory: $canonicalPath" }
        }
    }

    companion object {
        fun MessageOrBuilder.save(directory: File, uid: String) = File(directory, "$uid.json").writeText(toJson(false))

        fun Writer.writeText(text: String) {
            appendLine(text.trimMargin())
        }
    }
}