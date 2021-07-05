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

import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.io.Writer
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class TestPythonScript {

    private lateinit var script: TestPythonScript

    private val defaultScriptText = """
                |import logging
                |
                |from datetime import datetime
                |from os.path import dirname, join, abspath
                |from typing import List
                |from uuid import uuid1
                |
                |from google.protobuf.json_format import Parse
                |from google.protobuf.timestamp_pb2 import Timestamp
                |from th2_common.schema.factory.common_factory import CommonFactory
                |from th2_grpc_check1.check1_service import Check1Service
                |from th2_grpc_common.common_pb2 import EventID, EventBatch, Event, Message, ConnectionID
                |
                |messages_dir = join(dirname(abspath(__file__)), "messages")
                |
                |
                |def create_event_id() -> EventID:
                |    return EventID(id=str(uuid1()))
                |
                |
                |def timestamps():
                |    report_start_time = datetime.now()
                |    seconds = int(report_start_time.timestamp())
                |    nanos = int(report_start_time.microsecond * 1000)
                |    return seconds, nanos
                |
                |def create_event(event_name: str, parent_id: EventID = None) -> EventID:
                |    seconds, nanos = timestamps()
                |    event_id = create_event_id()
                |
                |    event = Event(
                |        id=event_id,
                |        name=event_name,
                |        status='SUCCESS',
                |        body=b"",
                |        start_timestamp=Timestamp(seconds=seconds, nanos=nanos),
                |        parent_id=parent_id
                |    )
                |
                |    event_batch = EventBatch()
                |    event_batch.events.append(event)
                |    event_store.send(event_batch)
                |
                |    return event_id
                |
                |
                |if __name__ == '__main__':
                |    logging.basicConfig(format="%(asctime)s - %(message)s")
                |    logger = logging.getLogger(__name__)
                |    logger.setLevel(logging.INFO)
                |
                |    configs_dir = join(dirname(abspath(__file__)), "configs")
                |
                |    factory = CommonFactory(
                |        grpc_router_config_filepath=join(configs_dir, "grpc.json"),
                |        rabbit_mq_config_filepath=join(configs_dir, "rabbit.json"),
                |        mq_router_config_filepath=join(configs_dir, "mq.json"),
                |        custom_config_filepath=join(configs_dir, "custom.json")
                |    )
                |
                |    event_store = factory.event_batch_router
                |    custom_config = factory.create_custom_configuration()
                |    check1 = factory.grpc_router.get_service(Check1Service)
                |
                |    report_id = create_event(f"Replay script {datetime.now().strftime('%Y%m%d-%H:%M:%S')}")
                |    logger.info(f"Root event was created (id = {report_id.id})")
                |
                |    factory.close()
                |
                |
            """.trimMargin()

    private val defaultReqirmentsTest = """
                |th2-common==3.2.0
                |th2-grpc-common==3.1.2
                |th2_grpc_check1==3.1.4
                |
                |grpcio
                |grpcio-tools
                |google-api-core
                |
                |
            """.trimMargin()

    @BeforeTest
    fun before() {
        script = TestPythonScript()
    }

    @Test
    fun defaultScript() {
        StringWriter().use { writer ->
            script.printScript(writer)

            assertEquals(defaultScriptText, writer.toString())
        }
    }

    @Test
    fun defaultReqirments() {
        StringWriter().use { writer ->
            script.printReqirments(writer)

            assertEquals(defaultReqirmentsTest, writer.toString())
        }
    }

    @Test
    fun registerImport() {
        StringWriter().use { writer ->
            script.registerImportPublic("test")
            script.printScript(writer)

            assertEquals("""
                |import test
                |
            """.trimMargin() + defaultScriptText, writer.toString())
        }
    }

    @Test
    fun registerRequirement() {
        StringWriter().use { writer ->
            val specifier = "test == 1.2.3"
            script.registerRequirementPublic(specifier)
            script.printReqirments(writer)

            assertEquals(defaultReqirmentsTest + """
                |$specifier
                |
            """.trimMargin(), writer.toString())
        }
    }

    private class TestPythonScript: AbstractPythonScript() {
        fun printScript(writer: Writer) {
            scriptImports.print(writer)
        }

        fun printReqirments(writer: Writer) {
            requirements.print(writer)
        }

        fun registerImportPublic(module: String) {
            super.registerImport(module)
        }

        fun registerRequirementPublic(specifier: String) {
            super.registerRequirement(specifier)
        }
    }
}