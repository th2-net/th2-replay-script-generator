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

package com.exactpro.th2.replay.script.generator.api

import com.exactpro.th2.common.grpc.Message

/**
 * Represents an action in a generated script
 */
interface IAction {
    /**
     * Updates state of this action with received [message].
     * Must return `true` if action state was updated
     */
    fun update(message: Message): Boolean

    /**
     * This method is called when a message stream is completed.
     * Returns a list of script blocks produced by this action
     */
    fun complete(): List<IScriptBlock>
}

interface IActionFactory {
    /**
     * Returns name of a produced action
     */
    val actionName: String

    /**
     * Returns class of action settings. Must have a zero-args constructor
     */
    val settingsClass: Class<out IActionSettings>

    /**
     * Initializes this factory. Provided [settings] will be passed to a created action
     */
    fun init(settings: IActionSettings)

    /**
     * Tries to create an action from provided message.
     * Returns `null` if produced action cannot be created from provided [message]
     */
    fun from(message: Message): IAction?
}

interface IActionSettings {}