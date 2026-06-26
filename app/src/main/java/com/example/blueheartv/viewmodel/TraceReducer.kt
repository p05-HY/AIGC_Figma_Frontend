package com.example.blueheartv.viewmodel

import com.example.blueheartv.model.AssistantTrace
import com.example.blueheartv.model.TraceEvent
import com.example.blueheartv.model.TraceRunStatus

/**
 * 将安全流中的 Trace 事件规约为单条助手消息的临时执行状态。
 *
 * seq 与 eventId 均为单个请求内的幂等保护；MVP 不承诺断线重放。
 */
fun reduceTrace(current: AssistantTrace?, event: TraceEvent): AssistantTrace {
    val trace = current ?: AssistantTrace(runId = event.runId)
    if (trace.runId != event.runId || event.eventId in trace.seenEventIds || event.seq <= trace.lastSeq) {
        return trace
    }

    val next = when (event) {
        is TraceEvent.RunStarted -> trace.copy(
            threadId = event.threadId ?: trace.threadId,
            summary = event.summary ?: trace.summary,
        )

        is TraceEvent.StepUpsert -> {
            val existingIndex = trace.steps.indexOfFirst { it.id == event.step.id }
            val nextSteps = if (existingIndex >= 0) {
                val existingStep = trace.steps[existingIndex]
                val nextStep = event.step.copy(
                    details = event.step.details.ifEmpty { existingStep.details },
                )
                trace.steps.toMutableList().also { it[existingIndex] = nextStep }
            } else {
                trace.steps + event.step
            }
            trace.copy(steps = nextSteps)
        }

        is TraceEvent.StepDetailAppend -> {
            val stepIndex = trace.steps.indexOfFirst { it.id == event.stepId }
            if (stepIndex < 0) {
                trace
            } else {
                val step = trace.steps[stepIndex]
                if (step.details.any { it.id == event.detail.id }) {
                    trace
                } else {
                    val nextStep = step.copy(details = step.details + event.detail)
                    trace.copy(
                        steps = trace.steps.toMutableList().also { it[stepIndex] = nextStep },
                    )
                }
            }
        }

        is TraceEvent.RunTerminal -> trace.copy(
            runStatus = event.status,
            hasTerminal = true,
        )
    }
    return next.copy(
        lastSeq = event.seq,
        seenEventIds = trace.seenEventIds + event.eventId,
    )
}

/** EOF 不是业务成功；没有服务端 terminal 时才标记为连接中断。 */
fun interruptTrace(trace: AssistantTrace): AssistantTrace =
    if (trace.hasTerminal) trace else trace.copy(runStatus = TraceRunStatus.INTERRUPTED)
