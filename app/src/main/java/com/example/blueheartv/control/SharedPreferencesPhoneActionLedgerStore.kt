package com.example.blueheartv.control

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Durable store used to reject stale command replays after process recreation. */
class SharedPreferencesPhoneActionLedgerStore(
    private val preferences: SharedPreferences,
) : PhoneActionLedgerStore {

    override fun load(): PhoneActionLedgerSnapshot {
        val raw = preferences.getString(KEY, null) ?: return PhoneActionLedgerSnapshot()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return PhoneActionLedgerSnapshot()
        val cancelledRuns = buildSet {
            val values = root.optJSONArray("cancelledRuns") ?: JSONArray()
            for (index in 0 until values.length()) {
                values.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        val actions = buildMap {
            val values = root.optJSONArray("actions") ?: JSONArray()
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val runId = item.optString("runId").takeIf { it.isNotBlank() } ?: continue
                val actionId = item.optString("actionId").takeIf { it.isNotBlank() } ?: continue
                val state = runCatching {
                    PhoneActionState.valueOf(item.optString("state"))
                }.getOrNull() ?: continue
                put(PhoneActionKey(runId, actionId), state)
            }
        }
        return PhoneActionLedgerSnapshot(cancelledRuns, actions)
    }

    override fun save(snapshot: PhoneActionLedgerSnapshot) {
        val root = JSONObject()
            .put("version", VERSION)
            .put("cancelledRuns", JSONArray(snapshot.cancelledRuns.toList()))
            .put(
                "actions",
                JSONArray().apply {
                    snapshot.actions.forEach { (key, state) ->
                        put(
                            JSONObject()
                                .put("runId", key.runId)
                                .put("actionId", key.actionId)
                                .put("state", state.name),
                        )
                    }
                },
            )
        // A queued/running action must survive process death as indeterminate.
        // commit() establishes that safety boundary before an action is admitted.
        preferences.edit().putString(KEY, root.toString()).commit()
    }

    private companion object {
        const val KEY = "phone_action_ledger_v1"
        const val VERSION = 1
    }
}
