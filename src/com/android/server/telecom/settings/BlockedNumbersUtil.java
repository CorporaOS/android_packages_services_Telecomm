/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.telecom.settings;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumbersManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextDirectionHeuristics;
import android.widget.Toast;

import com.android.server.telecom.R;
import com.android.server.telecom.SystemSettingsUtil;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.ui.NotificationChannelManager;

import java.util.Locale;

public final class BlockedNumbersUtil {
    private BlockedNumbersUtil() {}

    private static final int EMERGENCY_CALL_NOTIFICATION = 150;

    /**
     * @return locale and default to US if no locale was returned.
     */
    public static String getLocaleDefaultToUS() {
        String countryIso = Locale.getDefault().getCountry();
        if (countryIso == null || countryIso.length() != 2) {
            countryIso = "US";
        }
        return countryIso;
    }

    /**
     * Attempts to format the number, or returns the original number if it is not formattable. Also
     * wraps the returned number as LTR.
     */
    public static String formatNumber(String number){
      String formattedNumber = PhoneNumberUtils.formatNumber(number, getLocaleDefaultToUS());
      return BidiFormatter.getInstance().unicodeWrap(
              formattedNumber == null ? number : formattedNumber,
              TextDirectionHeuristics.LTR);
    }

    /**
     * Formats the number in the string and shows a toast for {@link Toast#LENGTH_SHORT}.
     *
     * <p>Adds the number in a TsSpan so that it reads as a phone number when talk back is on.
     */
    public static void showToastWithFormattedNumber(Context context, int stringId, String number) {
        String formattedNumber = formatNumber(number);
        String message = context.getString(stringId, formattedNumber);
        int startingPosition = message.indexOf(formattedNumber);
        Spannable messageSpannable = new SpannableString(message);
        PhoneNumberUtils.addTtsSpan(messageSpannable, startingPosition,
                startingPosition + formattedNumber.length());
        Toast.makeText(
                context,
                messageSpannable,
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Updates an emergency call notification
     *
     * @param context context to start CallBlockDisabledActivity.
     * @param showNotification if {@code true} show notification, {@code false} cancel notification.
     */
    public static void updateEmergencyCallNotification(Context context, boolean showNotification) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (showNotification) {
            Intent intent = new Intent(context, CallBlockDisabledActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT
                            | PendingIntent.FLAG_IMMUTABLE);

            String title = context.getString(
                    R.string.phone_strings_call_blocking_turned_off_notification_title_txt);
            String message = context.getString(
                    R.string.phone_strings_call_blocking_turned_off_notification_text_txt);
            Notification.Builder builder = new Notification.Builder(context);
            Notification notification = builder.setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setTicker(message)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setContentIntent(pendingIntent)
                    .setShowWhen(true)
                    .setChannelId(NotificationChannelManager.CHANNEL_ID_CALL_BLOCKING)
                    .build();

            notification.flags |= Notification.FLAG_NO_CLEAR;
            notificationManager.notifyAsUser(null /* tag */ , EMERGENCY_CALL_NOTIFICATION,
                    notification, new UserHandle(UserHandle.USER_OWNER));
        } else {
            notificationManager.cancelAsUser(null /* tag */ , EMERGENCY_CALL_NOTIFICATION,
                    new UserHandle(UserHandle.USER_OWNER));
        }
    }

    /**
     * Returns the platform configuration for whether to enable enhanced call blocking feature.
     *
     * @param context the application context
     * @return If {@code true} means enhanced call blocking enabled by platform,
     *            {@code false} otherwise.
     */
    public static boolean isEnhancedCallBlockingEnabledByPlatform(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle carrierConfig = configManager.getConfig();
        if (carrierConfig == null) {
            carrierConfig = configManager.getDefaultConfig();
        }
        return carrierConfig.getBoolean(
                CarrierConfigManager.KEY_SUPPORT_ENHANCED_CALL_BLOCKING_BOOL)
                || new SystemSettingsUtil().isEnhancedCallBlockingEnabled(context);
    }

    /**
     * Get the blocking setting status from {@link BlockedNumberProvider} SharedPreferences.
     *
     * @param context the application context
     * @param key preference key of SharedPreferences.
     * @return If {@code true} means the key enabled in the SharedPreferences,
     *            {@code false} otherwise.
     */
    public static boolean getBlockedNumberSetting(Context context, String key,
            FeatureFlags featureFlags) {
        return featureFlags.telecomMainlineBlockedNumbersManager()
                ? context.getSystemService(BlockedNumbersManager.class).getBlockedNumberSetting(key)
                : BlockedNumberContract.SystemContract.getEnhancedBlockSetting(context, key);
    }

    /**
     * Set the blocking setting status to {@link BlockedNumberProvider} SharedPreferences.
     *
     * @param context the application context
     * @param key preference key of SharedPreferences.
     * @param value the register value to the SharedPreferences.
     */
    public static void setBlockedNumberSetting(Context context, String key, boolean value,
            FeatureFlags featureFlags) {
        if (featureFlags.telecomMainlineBlockedNumbersManager()) {
            context.getSystemService(BlockedNumbersManager.class).setBlockedNumberSetting(key,
                    value);
        } else {
            BlockedNumberContract.SystemContract.setEnhancedBlockSetting(context, key, value);
        }
    }
}
