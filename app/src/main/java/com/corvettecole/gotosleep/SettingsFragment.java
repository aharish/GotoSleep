package com.corvettecole.gotosleep;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;


import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;


public class SettingsFragment extends BasePreferenceFragmentCompat implements BillingProcessor.IBillingHandler {

    final static String NOTIF_DELAY_KEY = "pref_notificationDelay";
    final static String NOTIF_AMOUNT_KEY = "pref_numNotifications";
    final static String NOTIF_ENABLE_KEY = "pref_notificationsEnabled";
    final static String BEDTIME_KEY = "pref_bedtime";
    final static String DND_KEY = "pref_autoDoNotDisturb";
    final static String BUTTON_HIDE_KEY = "pref_buttonHide";
    final static String NOTIFICATION_1_KEY = "pref_notification1";
    final static String NOTIFICATION_2_KEY = "pref_notification2";
    final static String NOTIFICATION_3_KEY = "pref_notification3";
    final static String NOTIFICATION_4_KEY = "pref_notification4";
    final static String NOTIFICATION_5_KEY = "pref_notification5";
    final static String CUSTOM_NOTIFICATIONS_KEY = "pref_customNotifications_category";
    final static String SMART_NOTIFICATIONS_KEY = "pref_smartNotifications";
    final static String ADS_ENABLED_KEY = "pref_adsEnabled";
    final static String ADVANCED_PURCHASED_KEY = "advanced_options_purchased";
    final static String INACTIVITY_TIMER_KEY = "pref_activityMargin";
    private boolean advancedOptionsPurchased;
    private boolean enableAdvancedOptions;
    private boolean adsEnabled;
    private BillingProcessor bp;
    private NotificationManager notificationManager;
    private UsageStatsManager usageStatsManager;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        sharedPreferences = getPreferenceManager().getSharedPreferences();
        notificationManager = (NotificationManager) getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        usageStatsManager = (UsageStatsManager) getActivity().getSystemService(Context.USAGE_STATS_SERVICE);


        Log.d("PREFERENCES", rootKey + " ");
        if (rootKey == null){

            bp = new BillingProcessor(getContext(), getResources().getString(R.string.license_key), this);
            bp.initialize();

            final Preference adsEnabledPref = this.findPreference(ADS_ENABLED_KEY);
            final Preference customNotificationsPref = this.findPreference(CUSTOM_NOTIFICATIONS_KEY);
            final Preference autoDnDPref = this.findPreference(DND_KEY);
            final Preference smartNotificationsPref = this.findPreference(SMART_NOTIFICATIONS_KEY);
            final Preference inactivityTimerPref = this.findPreference(INACTIVITY_TIMER_KEY);
            final Preference notificationAmount = this.findPreference(NOTIF_AMOUNT_KEY);
            final Preference notificationDelay = this.findPreference(NOTIF_DELAY_KEY);



            advancedOptionsPurchased = sharedPreferences.getBoolean(ADVANCED_PURCHASED_KEY, false);
            adsEnabled = sharedPreferences.getBoolean(ADS_ENABLED_KEY, false);

            if (advancedOptionsPurchased) {
               adsEnabledPref.setEnabled(false);
               adsEnabledPref.setSummary("Ads are disabled, thank you for your support.");

               getPreferenceScreen().findPreference("pref_advanced_options").setEnabled(true);
               getPreferenceManager().getSharedPreferences().edit().putBoolean(ADS_ENABLED_KEY, false).apply();
               getPreferenceScreen().findPreference(CUSTOM_NOTIFICATIONS_KEY).setEnabled(true);
               getPreferenceScreen().findPreference("pref_smartNotifications").setEnabled(true);
               getPreferenceScreen().findPreference("pref_advanced_purchase").setSummary("Thank you for supporting me!");
            } else {

                final Preference advancedPurchasePref = this.findPreference("pref_advanced_purchase");
                advancedPurchasePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        bp.purchase(getActivity(), "go_to_sleep_advanced");




                        return false;
                    }
                });

            }



            enableAdvancedOptions = advancedOptionsPurchased || adsEnabled;



            Preference bedtime = this.findPreference(BEDTIME_KEY);
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
                Date time = simpleDateFormat.parse(sharedPreferences.getString(BEDTIME_KEY, "19:35"));
                bedtime.setSummary("Bedtime is " +  DateFormat.getTimeInstance(DateFormat.SHORT).format(time));
            } catch (ParseException e) {
                e.printStackTrace();
                bedtime.setSummary("Bedtime is " + sharedPreferences.getString(BEDTIME_KEY, "19:35"));
            }



            getPreferenceScreen().findPreference("pref_smartNotifications").setEnabled(enableAdvancedOptions);
            customNotificationsPref.setEnabled(enableAdvancedOptions);


            adsEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
                //if enable ads is switched off, set premium options to false;
                if ((!(boolean) newValue) && (!advancedOptionsPurchased)) {

                    getPreferenceScreen().findPreference("pref_smartNotifications").setEnabled(false);
                    customNotificationsPref.setEnabled(false);
                } else {
                    getPreferenceScreen().findPreference("pref_smartNotifications").setEnabled(true);
                    customNotificationsPref.setEnabled(true);
                }

                return true;
            });


            //#TODO figure out a way to only toggle switch if the notification or usage permission is actually granted.
            // Returning false in onPreferenceChange will not update the preference with the new value, and onClickListeners exist...

            smartNotificationsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((boolean) newValue && !isUsageAccessGranted(getContext())){
                    notificationAmount.setEnabled(false);
                    Intent usageSettings = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivity(usageSettings);
                } else if (!(boolean)newValue){
                    notificationAmount.setEnabled(true);
                }
                return true;
            });

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                this.findPreference(DND_KEY).setEnabled(false);
                this.findPreference(DND_KEY).setSummary("Android 6.0 (Marshmallow) and up required");
                //# TODO add else if to check if user device is an LG G4. If so, disable option with reason
            } else {
                autoDnDPref.setOnPreferenceChangeListener((preference, newValue) -> {

                    // Check if the notification policy access has been granted for the app.
                    if ((boolean) newValue && notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                        startActivity(intent);
                    }
                    return true;
                });
            }

            notificationDelay.setSummary(sharedPreferences.getString(NOTIF_DELAY_KEY, "15") + " minute delay between sleep notifications");
            notificationDelay.setOnPreferenceChangeListener((preference, newValue) -> {
                notificationDelay.setSummary(newValue + " minute delay between sleep notifications");
                return true;
            });

            notificationAmount.setSummary(sharedPreferences.getString(NOTIF_AMOUNT_KEY, "2") + " sleep reminders will be sent");
            notificationAmount.setOnPreferenceChangeListener((preference, newValue) -> {
                notificationAmount.setSummary(newValue + " sleep reminders will be sent");
                return true;
            });

            inactivityTimerPref.setSummary("User must be inactive for " + sharedPreferences.getString(INACTIVITY_TIMER_KEY, "5") + " minutes to be considered inactive");
            inactivityTimerPref.setOnPreferenceChangeListener(((preference, newValue) -> {
                inactivityTimerPref.setSummary("User must be inactive for " + newValue + " minutes to be considered inactive");
                return true;
            }));

        } else {
           startCustomNotificationsScreen();
        }
    }

    public static boolean isUsageAccessGranted(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = 0;
            mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid, applicationInfo.packageName);
            return (mode == AppOpsManager.MODE_ALLOWED);

        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void startCustomNotificationsScreen(){

        final Preference notification1 = this.findPreference(NOTIFICATION_1_KEY);
        final Preference notification2 = this.findPreference(NOTIFICATION_2_KEY);
        final Preference notification3 = this.findPreference(NOTIFICATION_3_KEY);
        final Preference notification4 = this.findPreference(NOTIFICATION_4_KEY);
        final Preference notification5 = this.findPreference(NOTIFICATION_5_KEY);

        notification1.setSummary(sharedPreferences.getString(NOTIFICATION_1_KEY, getString(R.string.notification1)));
        notification2.setSummary(sharedPreferences.getString(NOTIFICATION_2_KEY, getString(R.string.notification2)));
        notification3.setSummary(sharedPreferences.getString(NOTIFICATION_3_KEY, getString(R.string.notification3)));
        notification4.setSummary(sharedPreferences.getString(NOTIFICATION_4_KEY, getString(R.string.notification4)));
        notification5.setSummary(sharedPreferences.getString(NOTIFICATION_5_KEY, getString(R.string.notification5)));

        notification1.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                notification1.setSummary((String) newValue);
                return true;
            }
        });

        notification2.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                notification2.setSummary((String) newValue);
                return true;
            }
        });

        notification3.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                notification3.setSummary((String) newValue);
                return true;
            }
        });

        notification4.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                notification4.setSummary((String) newValue);
                return true;
            }
        });

        notification5.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                notification5.setSummary((String) newValue);
                return true;
            }
        });
    }



    @Override
    public Fragment getCallbackFragment() {
        return this;
        // or you can return the parent fragment if it's handling the screen navigation,
        // however, in that case you need to traverse to the implementing parent fragment
    }

    @Override
    public void onResume(){
        Log.d("settings", "onResume called!");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (sharedPreferences.getBoolean(DND_KEY, false) && !notificationManager.isNotificationPolicyAccessGranted()){
                Toast.makeText(getContext(), "Do not disturb access not granted, toggle option to try again", Toast.LENGTH_LONG).show();
            }
        }

        if (sharedPreferences.getBoolean(SMART_NOTIFICATIONS_KEY, false) && !isUsageAccessGranted(getContext())) {
            Toast.makeText(getContext(), "Usage access not granted, toggle option to try again", Toast.LENGTH_LONG).show();
        }

        super.onResume();
    }

    @Override
    public void onProductPurchased(String productId, TransactionDetails details) {
        if (productId.equals("go_to_sleep_advanced")){
            Log.d("productPurchased", "go to sleep advanced purchased");
            advancedOptionsPurchased = true;
            getPreferenceManager().getSharedPreferences().edit().putBoolean(ADVANCED_PURCHASED_KEY, true).apply();
            getPreferenceScreen().findPreference("pref_adsEnabled").setEnabled(false);
            getPreferenceScreen().findPreference("pref_adsEnabled").setSummary("Ads are disabled, thank you for supporting me!");
            getPreferenceScreen().findPreference("pref_advanced_options").setEnabled(true);
            getPreferenceManager().getSharedPreferences().edit().putBoolean(ADS_ENABLED_KEY, false).apply();
            getPreferenceScreen().findPreference(CUSTOM_NOTIFICATIONS_KEY).setEnabled(true);
            getPreferenceScreen().findPreference("pref_smartNotifications").setEnabled(true);
            getPreferenceScreen().findPreference("pref_advanced_purchase").setSummary("Thank you for supporting me!");


        }

    }

    @Override
    public void onPurchaseHistoryRestored() {

    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {

    }

    @Override
    public void onBillingInitialized() {

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!bp.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onDestroy() {
        if (bp != null) {
            bp.release();
        }
        super.onDestroy();
    }

}

