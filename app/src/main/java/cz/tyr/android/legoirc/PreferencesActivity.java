package cz.tyr.android.legoirc;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

public class PreferencesActivity extends PreferenceActivity {
    // General
    public static final String HOST = "host";

    // Control Server
    public static final int DEFAULT_HOST = cz.tyr.android.legoirc.R.string.preferences_host_default;
    public static final String PORT = "port";
    public static final int DEFAULT_PORT = cz.tyr.android.legoirc.R.string.preferences_port_default;
    public static final String CONNECT_AT_START = "connect_at_start";
    public static final int DEFAULT_CONNECT_AT_START = cz.tyr.android.legoirc.R.string.preferences_connect_at_start_default;

    // Camera
    public static final String CAM_ENABLE = "cam_enable";
    public static final int DEFAULT_CAM_ENABLE = cz.tyr.android.legoirc.R.string.preferences_cam_enable_default;
    public static final String CAM_PORT = "cam_port";
    public static final int DEFAULT_CAM_PORT = cz.tyr.android.legoirc.R.string.preferences_cam_port_default;
    public static final String CAM_AT_START = "cam_at_start";
    public static final int DEFAULT_CAM_AT_START = cz.tyr.android.legoirc.R.string.preferences_cam_at_start_default;

    // Joystick
    public static final String JOYSTICK_SIZE = "joystick_size";
    public static final int DEFAULT_JOYSTICK_SIZE = cz.tyr.android.legoirc.R.string.preferences_joystick_size_default;
    public static final String JOYSTICK_DEADZONE = "joystick_deadzone";
    public static final int DEFAULT_JOYSTICK_DEADZONE = cz.tyr.android.legoirc.R.string.preferences_joystick_deadzone_default;

    // Other
    public static final String DEBUG = "debug";
    public static final int DEFAULT_DEBUG = cz.tyr.android.legoirc.R.string.preferences_debug_default;

    // Key under which we store the information about the playing video in the Main activity
    public static final String IS_PLAYING = "is_playing";
    public static final boolean DEFAULT_IS_PLAYING = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();
    }

    public static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(cz.tyr.android.legoirc.R.xml.preferences);
        }
    }
}
