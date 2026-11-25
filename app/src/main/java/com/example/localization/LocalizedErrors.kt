package com.example.localization

import android.content.Context
import com.example.R

fun Throwable.localizedMessage(context: Context, fallbackRes: Int): String {
    val raw = message.orEmpty()
    val localized = when {
        raw.startsWith("Invalid target package name") ->
            context.getString(R.string.error_invalid_target_package)
        raw.startsWith("Script '") && raw.contains("contains no target asset paths") ->
            context.getString(R.string.error_no_target_paths)
        raw.startsWith("Staged bundle files are missing") ->
            context.getString(R.string.error_staged_missing)
        raw.startsWith("Invalid target relative path") ->
            context.getString(R.string.error_invalid_target_path)
        raw.startsWith("Staged asset missing") ->
            context.getString(R.string.error_staged_asset_missing)
        raw.startsWith("No backup manifest found") ->
            context.getString(R.string.error_no_backup_manifest)
        raw.startsWith("Backup file missing") ->
            context.getString(R.string.error_backup_file_missing)
        raw.startsWith("Shizuku returned null FD") ->
            context.getString(R.string.error_shizuku_null_fd)
        raw.startsWith("Shizuku UserService is not connected") ->
            context.getString(R.string.error_shizuku_not_connected)
        raw.startsWith("Selected script not found") ->
            context.getString(R.string.error_selected_script_not_found)
        raw.startsWith("Built-in catalog scripts cannot be deleted") ->
            context.getString(R.string.error_builtin_delete)
        raw.startsWith("Restore original files before deleting") ->
            context.getString(R.string.error_restore_before_delete)
        raw.startsWith("Restore original files first before clearing") ->
            context.getString(R.string.error_restore_before_clear)
        raw.startsWith("No download URL configured") ->
            context.getString(R.string.error_no_download_url)
        raw.startsWith("Download failed (HTTP") -> {
            val code = Regex("HTTP (\\d+)").find(raw)?.groupValues?.getOrNull(1)
            if (code != null) context.getString(R.string.error_download_http, code)
            else context.getString(R.string.msg_download_failed_generic)
        }
        raw.startsWith("Host returned a web page") ->
            context.getString(R.string.error_web_page)
        raw.startsWith("Downloaded file was empty") ->
            context.getString(R.string.error_empty_file)
        raw.startsWith("Host returned an HTML page") ->
            context.getString(R.string.error_html_file)
        raw.startsWith("Could not resolve a direct download stream") ->
            context.getString(R.string.error_no_direct_stream)
        raw.startsWith("Downloaded archive contained no extractable") ->
            context.getString(R.string.error_no_extractable_files)
        else -> raw.ifBlank { context.getString(fallbackRes) }
    }
    return localized
}

fun String.localizedShizukuReason(context: Context): String = when (this) {
    "Binder interface returned null" -> context.getString(R.string.shizuku_reason_binder_null)
    "UserService process disconnected" -> context.getString(R.string.shizuku_reason_process_disconnected)
    "Shizuku binder stopped" -> context.getString(R.string.shizuku_reason_binder_stopped)
    "Failed to bind UserService" -> context.getString(R.string.shizuku_reason_bind_failed)
    "Service unbound" -> context.getString(R.string.shizuku_reason_unbound)
    "UserService not connected" -> context.getString(R.string.shizuku_reason_not_connected)
    else -> this
}
