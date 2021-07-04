/*
 * Copyright (c) 2021-2021, Exactpro Systems LLC
 * www.exactpro.com
 * Build Software to Test Software
 *
 * All rights reserved.
 * This is unpublished, licensed software, confidential and proprietary
 * information which is the property of Exactpro Systems LLC or its licensors.
 */

package com.exactpro.th2.replay.script.generator.impl

import java.io.File
import java.time.Instant

@Suppress("MemberVisibilityCanBePrivate")
abstract class AbstractPythonScript : AbstractScript() {

    protected val scriptImports: ScriptSection = ScriptSection("imports")
    protected val scriptVariables: ScriptSection = ScriptSection("variables")
    protected val scriptMethods: ScriptSection = ScriptSection("methods")
    protected val scriptMain: ScriptSection = ScriptSection("main")
    protected val scriptBody: ScriptSection = ScriptSection("body")
    protected val scriptEnd: ScriptSection = ScriptSection("body")

    protected val requirements: ScriptSection = ScriptSection("requirements")

    init {
        scriptImports
            .after(scriptVariables)
            .after(scriptMethods)
            .after(scriptMain)
            .after(scriptBody)
            .after(scriptEnd)
    }

    override fun init(scriptDirectory: File) {
        super.init(scriptDirectory)

        initScriptSections()
        initRequirementsSections()
    }

    private fun initScriptSections() {
        scriptImports.after { write("""
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
            """) }
        scriptVariables.after { write("""
                |messages_dir = join(dirname(abspath(__file__)), "messages")
                |
                |
            """) }
        scriptMethods.after { write("""
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
                |
            """) }
        scriptMain.after { write("""
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
            """) }
        scriptEnd.after { write("""
                |    factory.close()
                |
                |
            """) }
    }

    private fun initRequirementsSections() {
        requirements.after { write(
                """
                |th2-common==3.2.0
                |th2-grpc-common==3.1.2
                |th2_grpc_check1==3.1.4
                |
                |grpcio
                |grpcio-tools
                |google-api-core
                |
            """) }
    }

    override fun write(text: String) {
        scriptBody.after { write(text) }
    }

    override fun close() {
        File(scriptDirectory, "script-${Instant.now().toString().replace(':', '-')}.py").bufferedWriter().use { writer ->
            scriptImports.print(writer)
        }

        File(scriptDirectory, "requirements.txt").bufferedWriter().use {

        }
    }
}
