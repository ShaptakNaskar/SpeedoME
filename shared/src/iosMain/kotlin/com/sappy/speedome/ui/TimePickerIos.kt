@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.sappy.speedome.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject
import platform.objc.sel_registerName

private class TimePickerTarget(private val state: TimePickerState) : NSObject() {
    @ObjCAction
    fun changed(picker: UIDatePicker) {
        val time = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, picker.date)
        state.hour = time.hour.toInt()
        state.minute = time.minute.toInt()
    }
}

@Composable
actual fun PlatformTimePicker(state: TimePickerState) {
    // UIControl holds its target weakly; retain it for the lifetime of the picker.
    val target = remember(state) { TimePickerTarget(state) }
    UIKitView(
        factory = {
            UIDatePicker().apply {
                datePickerMode = UIDatePickerMode.UIDatePickerModeTime
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleCompact
                accessibilityLabel = "Arrive by"
                val now = NSDate()
                date = NSCalendar.currentCalendar.dateBySettingHour(state.hour.toLong(), state.minute.toLong(), 0, now, 0u) ?: now
                addTarget(target, sel_registerName("changed:"), UIControlEventValueChanged)
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        properties = UIKitInteropProperties(
            // Keep the dialog behind the transparent picker instead of cutting a hole through it.
            placedAsOverlay = true,
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
    )
}
