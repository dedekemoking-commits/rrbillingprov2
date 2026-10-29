package com.billingps.aptv.tv

enum class HdmiTestKind(val label: String) {
    WEBSOCKET("WebSocket 8080"),
    REMOTE("Remote Key"),
    CLIENT("TV Client"),
}

data class HdmiTestCommand(
    val id: String,
    val label: String,
    val kind: HdmiTestKind,
    val payload: String = "",
    val keys: List<String> = emptyList(),
    val description: String = "",
)

object HdmiCommandCatalog {

    fun webSocketCommands(port: Int): List<HdmiTestCommand> {
        val n = port.coerceIn(1, 4)
        return listOf(
            HdmiTestCommand(
                "ws_key_hdmi$n",
                "JSON action:key",
                HdmiTestKind.WEBSOCKET,
                """{"action":"key","value":"KEY_HDMI$n"}""",
                description = "Payload key sederhana",
            ),
            HdmiTestCommand(
                "ws_send_key_hdmi$n",
                "JSON action:send_key",
                HdmiTestKind.WEBSOCKET,
                """{"action":"send_key","key":"HDMI$n"}""",
                description = "Payload send_key",
            ),
            HdmiTestCommand(
                "ws_keypress_hdmi$n",
                "JSON KEY_PRESS",
                HdmiTestKind.WEBSOCKET,
                """{"type":"KEY_PRESS","keyCode":"KEYCODE_TV_INPUT_HDMI_$n"}""",
                description = "Payload KEY_PRESS Android",
            ),
            HdmiTestCommand(
                "ws_jsonrpc_content_hdmi$n",
                "JSON-RPC setPlayContent",
                HdmiTestKind.WEBSOCKET,
                """{"jsonrpc":"2.0","method":"setPlayContent","params":[{"uri":"extInput:hdmi?port=$n"}],"id":1}""",
                description = "Sony Bravia / JSON-RPC input",
            ),
            HdmiTestCommand(
                "ws_jsonrpc_setinput_hdmi$n",
                "JSON-RPC setInput",
                HdmiTestKind.WEBSOCKET,
                """{"method":"setInput","params":{"source":"HDMI$n"},"id":1}""",
                description = "JSON-RPC setInput",
            ),
            HdmiTestCommand(
                "ws_jsonrpc_switch_hdmi$n",
                "JSON-RPC switchInput",
                HdmiTestKind.WEBSOCKET,
                """{"method":"switchInput","input":"HDMI$n"}""",
                description = "JSON-RPC switchInput",
            ),
            HdmiTestCommand(
                "ws_command_source_hdmi$n",
                "JSON command:set_source",
                HdmiTestKind.WEBSOCKET,
                """{"command":"set_source","value":"hdmi$n"}""",
                description = "Payload command set_source",
            ),
            HdmiTestCommand(
                "ws_plain_set_hdmi$n",
                "String SET_INPUT_HDMI$n",
                HdmiTestKind.WEBSOCKET,
                "SET_INPUT_HDMI$n",
                description = "String command",
            ),
            HdmiTestCommand(
                "ws_plain_input_hdmi$n",
                "String INPUT_HDMI$n",
                HdmiTestKind.WEBSOCKET,
                "INPUT_HDMI$n",
                description = "String command",
            ),
            HdmiTestCommand(
                "ws_plain_hdmi$n",
                "String hdmi$n",
                HdmiTestKind.WEBSOCKET,
                "hdmi$n",
                description = "String command",
            ),
        )
    }

    fun remoteCommands(port: Int): List<HdmiTestCommand> {
        val n = port.coerceIn(1, 4)
        val down = List((n - 1).coerceAtLeast(0)) { "DPAD_DOWN" }
        val up = List((n - 1).coerceAtLeast(0)) { "DPAD_UP" }
        return buildList {
            add(
                HdmiTestCommand(
                    "remote_hdmi$n",
                    "TV_INPUT_HDMI_$n",
                    HdmiTestKind.REMOTE,
                    keys = listOf("TV_INPUT_HDMI_$n"),
                    description = "Key khusus HDMI $n",
                )
            )
            add(
                HdmiTestCommand(
                    "remote_input_menu_hdmi$n",
                    "TV_INPUT + pilih HDMI $n",
                    HdmiTestKind.REMOTE,
                    keys = listOf("TV_INPUT") + down + listOf("DPAD_CENTER"),
                    description = "Buka menu input lalu navigasi ke bawah",
                )
            )
            add(
                HdmiTestCommand(
                    "remote_input_menu_up_hdmi$n",
                    "TV_INPUT + naik HDMI $n",
                    HdmiTestKind.REMOTE,
                    keys = listOf("TV_INPUT") + up + listOf("DPAD_CENTER"),
                    description = "Buka menu input lalu navigasi ke atas",
                )
            )
            add(
                HdmiTestCommand(
                    "remote_input_only",
                    "TV_INPUT",
                    HdmiTestKind.REMOTE,
                    keys = listOf("TV_INPUT"),
                    description = "Hanya membuka menu input",
                )
            )
        }
    }

    fun clientCommands(): List<HdmiTestCommand> = listOf(
        HdmiTestCommand("tif_view", "TIF ACTION_VIEW", HdmiTestKind.CLIENT, description = "Intent ACTION_VIEW + channelUri"),
        HdmiTestCommand("tif_view_id", "TIF ACTION_VIEW + inputId", HdmiTestKind.CLIENT, description = "ACTION_VIEW + extra inputId"),
        HdmiTestCommand("tif_media_view", "android.media.tv.action.VIEW", HdmiTestKind.CLIENT, description = "Intent media TV + inputId"),
        HdmiTestCommand("tif_switch_input", "android.tv.action.SWITCH_INPUT", HdmiTestKind.CLIENT, description = "Intent switch input + hdmiPort"),
        HdmiTestCommand("vendor_tcl", "Vendor TCL", HdmiTestKind.CLIENT, description = "com.tcl.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_hisense", "Vendor Hisense", HdmiTestKind.CLIENT, description = "com.hisense.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_skyworth", "Vendor Skyworth", HdmiTestKind.CLIENT, description = "com.skyworth.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_mstar", "Vendor MStar", HdmiTestKind.CLIENT, description = "com.mstar.android.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_mediatek", "Vendor MediaTek", HdmiTestKind.CLIENT, description = "com.mediatek.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_philips", "Vendor Philips", HdmiTestKind.CLIENT, description = "com.philips.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_sony", "Vendor Sony", HdmiTestKind.CLIENT, description = "com.sony.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_lg", "Vendor LG", HdmiTestKind.CLIENT, description = "com.lg.app.tv.action.SWITCH_INPUT"),
        HdmiTestCommand("vendor_samsung", "Vendor Samsung", HdmiTestKind.CLIENT, description = "com.samsung.android.tv.action.SWITCH_INPUT"),
    )

    fun commandsFor(kind: HdmiTestKind, port: Int): List<HdmiTestCommand> = when (kind) {
        HdmiTestKind.WEBSOCKET -> webSocketCommands(port)
        HdmiTestKind.REMOTE -> remoteCommands(port)
        HdmiTestKind.CLIENT -> clientCommands()
    }

    fun findRemoteKeys(commandId: String, port: Int): List<String> =
        remoteCommands(port).firstOrNull { it.id == commandId }?.keys ?: emptyList()

    fun labelFor(commandId: String, port: Int): String {
        if (commandId.startsWith("ws:")) return "WebSocket: ${commandId.removePrefix("ws:").take(48)}"
        return commandsFor(HdmiTestKind.CLIENT, port).firstOrNull { it.id == commandId }?.label
            ?: commandsFor(HdmiTestKind.REMOTE, port).firstOrNull { it.id == commandId }?.label
            ?: commandId
    }
}
