package cz.tyr.android.legoirc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.net.Socket;

public class Main extends Activity implements SurfaceHolder.Callback, IVideoPlayer {
    // Debug variables
    private boolean DEBUG = false;
    private final static String TAG = "LEGOIRC";

    // Reference to the two joysticks
    private Joystick jsLeft, jsRight;

    // Direction from each of the joystick
    private int directionVertical = 5;
    private int directionHorizontal = 5;

    // Reference to the Activity
    private Context context;

    // Reference to the play/pause button
    private ImageButton videoImageButton;

    // Reference to the shared preferences
    private SharedPreferences preferences;

    // Reference to the shared configuration
    private Config config;

    // Reference to the AsyncTask
    private LegoIrcServerTask legoIrcServerTask;

    // VLC Media Player stuff
    private LibVLC libvlc;
    private String videoURL;
    private SurfaceHolder videoHolder;
    private int videoWidth;
    private int videoHeight;
    private final static int videoSizeChanged = -1;

     /************
      * Activity *
      ************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DEBUG)
            Log.d(TAG, "onCreate");

        // Store context
        context = this;

        // Inflate the main layout
        setContentView(cz.tyr.android.legoirc.R.layout.activity_main);

        // Read shared preferences and config
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        config = new Config();

        // Play/pause video button
        videoImageButton = (ImageButton) findViewById(cz.tyr.android.legoirc.R.id.videoImageButton);

        // Setup joysticks
        RelativeLayout joystickLeftLayout = (RelativeLayout) findViewById(cz.tyr.android.legoirc.R.id.layout_joystick_left);
        RelativeLayout joystickRightLayout = (RelativeLayout) findViewById(cz.tyr.android.legoirc.R.id.layout_joystick_right);
        jsLeft = new Joystick(getApplicationContext(), joystickLeftLayout, cz.tyr.android.legoirc.R.drawable.image_button);
        jsRight = new Joystick(getApplicationContext(), joystickRightLayout, cz.tyr.android.legoirc.R.drawable.image_button);
        joystickLeftLayout.setOnTouchListener(new JoystickOnTouchListener(jsLeft, JoystickOnTouchListener.JOYSTICK_TYPE_VERTICAL, config.joystickSize, config.joystickDeadzone));
        joystickRightLayout.setOnTouchListener(new JoystickOnTouchListener(jsRight, JoystickOnTouchListener.JOYSTICK_TYPE_HORIZONTAL, config.joystickSize, config.joystickDeadzone));

        // Async task to maintain the TCP control connection
        legoIrcServerTask = new LegoIrcServerTask(config.host, config.port);
        legoIrcServerTask.execute();

        // Connect to the server at start if set so
        if (config.connect_at_start) {
            legoIrcServerTask.connect();
        }

        // Start video playback it set so
        if (config.cam_at_start) {
            createPlayer(videoURL);
            setIsPlaying(true);
        } else {
            setIsPlaying(false);
        }

        // Configure layout according to shared preferences
        configureLayout();
    }

    private class Config {
        private String host;
        private int joystickSize;
        private int joystickDeadzone;
        boolean connect_at_start;
        boolean cam_enable;
        boolean cam_at_start;
        int port;

        public Config() {
            this.load();
        }

        private void load() {
            host = preferences.getString(PreferencesActivity.HOST, getString(PreferencesActivity.DEFAULT_HOST));
            DEBUG = preferences.getBoolean(PreferencesActivity.DEBUG, Boolean.parseBoolean(getString(PreferencesActivity.DEFAULT_DEBUG)));
            joystickSize = Integer.parseInt(preferences.getString(PreferencesActivity.JOYSTICK_SIZE, getString(PreferencesActivity.DEFAULT_JOYSTICK_SIZE)));
            joystickDeadzone = Integer.parseInt(preferences.getString(PreferencesActivity.JOYSTICK_DEADZONE, getString(PreferencesActivity.DEFAULT_JOYSTICK_DEADZONE)));
            connect_at_start = preferences.getBoolean(PreferencesActivity.CONNECT_AT_START, Boolean.parseBoolean(getString(PreferencesActivity.DEFAULT_CONNECT_AT_START)));
            cam_enable = preferences.getBoolean(PreferencesActivity.CAM_ENABLE, Boolean.parseBoolean(getString(PreferencesActivity.DEFAULT_CAM_ENABLE)));
            cam_at_start = false;
            if (cam_enable) {
                cam_at_start = preferences.getBoolean(PreferencesActivity.CAM_AT_START, Boolean.parseBoolean(getString(PreferencesActivity.DEFAULT_CAM_AT_START)));
            }
            port = 0;
            int videoPort = 0;
            try {
                port = Integer.parseInt(preferences.getString(PreferencesActivity.PORT, getString(PreferencesActivity.DEFAULT_PORT)));
                videoPort = Integer.parseInt(preferences.getString(PreferencesActivity.CAM_PORT, getString(PreferencesActivity.DEFAULT_CAM_PORT)));
            } catch (Exception e) {
                Toast.makeText(context, getString(cz.tyr.android.legoirc.R.string.shared_preferences_error), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

            // URL to play
            videoURL = "http://" + host + ":" + videoPort;
            if (DEBUG)
                Log.d(TAG, "Video URL: " + videoURL);
        }
    }

    private class JoystickOnTouchListener implements View.OnTouchListener  {
        private Joystick mJoystick;
        private int mType;
        private int mSize;
        private int mDeadzone;

        // Type of joystick
        public static final int JOYSTICK_TYPE_VERTICAL = 0;
        public static final int JOYSTICK_TYPE_HORIZONTAL = 1;

        public JoystickOnTouchListener(Joystick joystick, int type, int size, int deadzone) {
            mJoystick = joystick;
            mType = type;
            mSize = size;
            mDeadzone = deadzone;

            // Configure the joystick layout
            mJoystick.setStickSize(150, 150);
            mJoystick.setLayoutAlpha(100);
            mJoystick.setStickAlpha(100);
            mJoystick.setOffset(75);
            changeJoystickSize(mJoystick, mSize, mDeadzone);
        }

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mJoystick.drawStick(motionEvent);

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN
                    || motionEvent.getAction() == MotionEvent.ACTION_MOVE) {

                if (mType == JOYSTICK_TYPE_VERTICAL) {
                    int direction = mJoystick.get2DirectionVertical();

                    if (direction == Joystick.STICK_UP) {
                        directionVertical = 8;
                    } else if (direction == Joystick.STICK_DOWN) {
                        directionVertical = 2;
                    } else if (direction == Joystick.STICK_NONE) {
                        directionVertical = 5;
                    }
                } else {
                    int direction = mJoystick.get2DirectionHorizontal();

                    if (direction == Joystick.STICK_RIGHT) {
                        directionHorizontal = 6;
                    } else if (direction == Joystick.STICK_LEFT) {
                        directionHorizontal = 4;
                    } else if (direction == Joystick.STICK_NONE) {
                        directionHorizontal = 5;
                    }
                }
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                // Send stop if thumb goes up
                if (mType == JOYSTICK_TYPE_VERTICAL) {
                    directionVertical = 5;
                } else {
                    directionHorizontal = 5;
                }
            }

            return true;
        }
    }

    private class LegoIrcServerTask extends AsyncTask<String, Integer, Object> {
        private String host;
        private int port;
        private Socket sock = null;
        private PrintStream out = null;

        // Flag which we set if we are connected
        private boolean connected = false;

        // Signals used in the infinite loop
        private final int CMD_NONE = 0;
        private final int CMD_CONNECT = 1;
        private final int CMD_DISCONNECT = 2;
        private final int CMD_SHUTDOWN = 3;
        private final int CMD_FINISH = 4;
        private int command = CMD_NONE;

        // Directions
        private final int DIR_UP = 8;
        private final int DIR_DOWN = 2;
        private final int DIR_LEFT = 4;
        private final int DIR_RIGHT = 6;
        private final int DIR_STOP = 5;

        LegoIrcServerTask(String host, int port){
            this.host = host;
            this.port = port;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isConnected() {
            return connected;
        }

        public void connect() {
            command = CMD_CONNECT;
        }

        public void disconnect() {
            command = CMD_DISCONNECT;
        }

        public void shutdown() {
            command = CMD_SHUTDOWN;
        }

        public void finish() {
            command = CMD_FINISH;
        }

        @Override
        protected Object doInBackground(String... strings) {
            if (DEBUG)
                Log.d(TAG, "AsyncTask - doInBackground");

            boolean stopSent = true;

            while (true) {
                // Finish the loop if requested
                if (command == CMD_FINISH) {
                    break;
                }

                // Try to connect if requested
                if (command == CMD_CONNECT) {
                    if (DEBUG)
                        Log.d(TAG, "AsyncTask - doInBackground - connecting");

                    // Establish the connection
                    try {
                        establishConnection();
                    } catch (Exception e) {
                        // Show toast about unsuccessful connection in the main UI thread
                        publishProgress(1);
                    }

                    // Signal completed
                    command = CMD_NONE;

                    // Change the connection flag
                    if (sock != null && sock.isConnected())
                        connected = true;
                }

                // Process the input from joysticks
                if (sock != null && out != null) {
                    // Reset the number of sent stops
                    if (directionVertical != 5 || directionHorizontal != 5) {
                        stopSent = false;
                    }

                    // Determine the message
                    String message = "5";
                    if (command == CMD_SHUTDOWN) {
                        message = "X";

                        // Signal completed
                        command = CMD_NONE;
                    } else if (directionVertical == DIR_UP && directionHorizontal == DIR_RIGHT) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Forward Right");

                        message = "9";
                    } else if (directionVertical == DIR_UP && directionHorizontal == DIR_STOP) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Forward");

                        message = "8";
                    } else if (directionVertical == DIR_UP && directionHorizontal == DIR_LEFT) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Forward Left");

                        message = "7";
                    } else if (directionVertical == DIR_STOP && directionHorizontal == DIR_RIGHT) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Right");

                        message = "6";
                    } else if (directionVertical == DIR_STOP && directionHorizontal == DIR_STOP) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Stop");

                        message = "5";
                    } else if (directionVertical == DIR_STOP && directionHorizontal == DIR_LEFT) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Left");

                        message = "4";
                    } else if (directionVertical == DIR_DOWN && directionHorizontal == DIR_RIGHT) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Backward Right");

                        message = "3";
                    } else if (directionVertical == DIR_DOWN && directionHorizontal == DIR_STOP) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Backward");

                        message = "2";
                    } else if (directionVertical == DIR_DOWN && directionHorizontal == DIR_LEFT) {
                        if (DEBUG && ! stopSent)
                            Log.d(TAG, "Direction: Backward Left");

                        message = "1";
                    }

                    // Make sure that we send stop only 1 times
                    if (! stopSent || message.equals("X")) {
                        if (DEBUG)
                            Log.d(TAG, "Sending message: " + message);

                        out.println(message);

                        if (directionVertical == 5 && directionHorizontal == 5) {
                            stopSent = true;
                        }
                    }

                    // Sleep for 0.1 second
                    SystemClock.sleep(100);
                } else {
                    // We are not connected, so sleep longer
                    SystemClock.sleep(1000);
                }

                // Disconnect if requested
                if (command == CMD_DISCONNECT) {
                    if (DEBUG)
                        Log.d(TAG, "AsyncTask - doInBackground - disconnecting");

                    // Close the connection
                    closeConnection();

                    // Signal completed
                    command = CMD_NONE;

                    // Change the connection flag
                    connected = false;
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            if (values[0] == 1) {
                // Show the Toast in the main UI thread
                Toast.makeText(context, getString(cz.tyr.android.legoirc.R.string.connection_error), Toast.LENGTH_SHORT).show();
            }
        }

        private void establishConnection() {
            if (DEBUG)
                Log.d(TAG, "AsyncTask - establishConnection");

            if (sock == null) {
                if (DEBUG)
                    Log.d(TAG, "Trying to connect to " + host + ":" + port);

                // Try to connect
                try {
                    sock = new Socket(host, port);
                } catch (Exception e) {
                    Log.e(TAG, "Can not create socket: " + e.getMessage());
                }
            }

            // Get output stream from the socket
            try {
                out = new PrintStream(sock.getOutputStream());
            } catch (IOException e) {
                Log.e(TAG, "Can not get output stream: " + e.getMessage());
            }
        }

        private void closeConnection() {
            if (DEBUG)
                Log.d(TAG, "AsyncTask - closeConnection");

            if (out != null) {
                // Indicate to the server that we are closing connection
                out.println();
                out.flush();

                // Close stream
                out.close();

                // Free the variable
                out = null;
            }

            if (sock != null) {
                try {
                    sock.shutdownOutput();
                } catch (IOException e) {
                    // Do nothing
                }

                try {
                    sock.close();
                } catch (IOException e) {
                    // Do nothing
                }

                // Free the variable
                sock = null;
            }
        }
    }

    private Joystick changeJoystickSize(Joystick joystick, int size, int deadzone) {
        joystick.setMinimumDistance(dpToPx(deadzone));
        joystick.setLayoutSize(dpToPx(size), dpToPx(size));

        return joystick;
    }

    private void setIsPlaying(boolean value) {
        preferences.edit().putBoolean(PreferencesActivity.IS_PLAYING, value).apply();
    }

    private boolean getIsPlaying() {
        return preferences.getBoolean(PreferencesActivity.IS_PLAYING, PreferencesActivity.DEFAULT_IS_PLAYING);
    }

    private void configureLayout() {
        boolean cam_enable = preferences.getBoolean(PreferencesActivity.CAM_ENABLE, Boolean.parseBoolean(getString(PreferencesActivity.DEFAULT_CAM_ENABLE)));
        int joystickSize = Integer.parseInt(preferences.getString(PreferencesActivity.JOYSTICK_SIZE, getString(PreferencesActivity.DEFAULT_JOYSTICK_SIZE)));
        int joystickDeadzone = Integer.parseInt(preferences.getString(PreferencesActivity.JOYSTICK_DEADZONE, getString(PreferencesActivity.DEFAULT_JOYSTICK_DEADZONE)));
        DEBUG = preferences.getBoolean(PreferencesActivity.DEBUG, Boolean.parseBoolean(getString(PreferencesActivity.DEFAULT_DEBUG)));

        // Show or hide the camera button
        if (cam_enable) {
            videoImageButton.setVisibility(View.VISIBLE);
        } else {
            videoImageButton.setVisibility(View.GONE);
            setIsPlaying(false);
        }

        // Change camera icon according to the playback state
        if (getIsPlaying()) {
            videoImageButton.setImageResource(cz.tyr.android.legoirc.R.drawable.stop);
        } else {
            videoImageButton.setImageResource(cz.tyr.android.legoirc.R.drawable.play);
        }

        // Change size of the joystick and its deadzone
        changeJoystickSize(jsLeft, joystickSize, joystickDeadzone).repaint();
        changeJoystickSize(jsRight, joystickSize, joystickDeadzone).repaint();
    }

    // Called from the layout
    public void playPauseVideo(View view) {
        setIsPlaying(! getIsPlaying());

        if (libvlc != null && libvlc.isPlaying()) {
            releasePlayer();
            setIsPlaying(false);
        } else {
            createPlayer(videoURL);
        }

        configureLayout();
    }

    // Called from the layout
    public void showMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case cz.tyr.android.legoirc.R.id.menu_settings:
                        Intent intent = new Intent(context, PreferencesActivity.class);
                        startActivity(intent);
                        return true;
                    case cz.tyr.android.legoirc.R.id.menu_connect:
                        if (legoIrcServerTask.isConnected()) {
                            Toast.makeText(context, getString(cz.tyr.android.legoirc.R.string.connection_disconnected), Toast.LENGTH_SHORT).show();
                            legoIrcServerTask.disconnect();
                        } else {
                            legoIrcServerTask.connect();
                        }
                        return true;
                    case cz.tyr.android.legoirc.R.id.menu_shutdown:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setMessage(getString(cz.tyr.android.legoirc.R.string.connection_shutdown_dialog))
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Toast.makeText(context, getString(cz.tyr.android.legoirc.R.string.connection_shutdown), Toast.LENGTH_SHORT).show();
                                        legoIrcServerTask.shutdown();
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        // Do nothing
                                    }
                                }).create().show();
                        return true;
                    default:
                        return false;
                }
            }
        });

        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(cz.tyr.android.legoirc.R.menu.menu, popup.getMenu());

        if (legoIrcServerTask.isConnected()) {
            popup.getMenu().findItem(cz.tyr.android.legoirc.R.id.menu_connect).setTitle(getString(cz.tyr.android.legoirc.R.string.menu_connect_disconnect));
            popup.getMenu().findItem(cz.tyr.android.legoirc.R.id.menu_shutdown).setEnabled(true);
        } else {
            popup.getMenu().findItem(cz.tyr.android.legoirc.R.id.menu_connect).setTitle(getString(cz.tyr.android.legoirc.R.string.menu_connect_connect));
            // Can not shutdown if there is no connection
            popup.getMenu().findItem(cz.tyr.android.legoirc.R.id.menu_shutdown).setEnabled(false);
        }

        popup.show();
    }

    // Converts display points to pixels
    private int dpToPx(int dp) {
        float density = getApplicationContext().getResources().getDisplayMetrics().density;
        return Math.round((float)dp * density);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSize(videoWidth, videoHeight);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (DEBUG)
            Log.d(TAG, "onResume");

        if (getIsPlaying()) {
            createPlayer(videoURL);
        }

        // Reload shared config
        config.load();

        // Set host and port if changed
        legoIrcServerTask.setHost(config.host);
        legoIrcServerTask.setPort(config.port);

        // Refresh layout
        configureLayout();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (DEBUG)
            Log.d(TAG, "onPause");

        // Release VLC player
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (DEBUG)
            Log.d(TAG, "onDestroy");

        // Release VLC player
        releasePlayer();

        // Video is not playing anymore
        setIsPlaying(false);

        // Close connection to the server
        legoIrcServerTask.closeConnection();
        legoIrcServerTask.finish();
    }

    /***********
     * Surface *
     ***********/

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        // Do nothing
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceholder, int format, int width, int height) {
        if (libvlc != null && videoHolder != null)
            libvlc.attachSurface(videoHolder.getSurface(), this);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        // Do nothing
    }

    private void setSize(int width, int height) {
        videoWidth = width;
        videoHeight = height;
        if (videoWidth * videoHeight <= 1)
            return;

        // Force surface buffer size
        videoHolder.setFixedSize(videoWidth, videoHeight);
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den) {
        Message msg = Message.obtain(eventHandler, videoSizeChanged, width, height);
        msg.sendToTarget();
    }

    /**********
     * Player *
     **********/

    private void createPlayer(String media) {
        releasePlayer();
        try {
            // Initialize the video player surface
            SurfaceView mSurface = (SurfaceView) findViewById(cz.tyr.android.legoirc.R.id.surface);
            videoHolder = mSurface.getHolder();
            videoHolder.addCallback(this);

            // Create a new media player
            libvlc = LibVLC.getInstance();
            libvlc.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
            libvlc.setSubtitlesEncoding("");
            libvlc.setAout(LibVLC.AOUT_OPENSLES);
            libvlc.setTimeStretching(true);
            libvlc.setChroma("RV32");
            if (DEBUG) {
                libvlc.setVerboseMode(true);
            } else {
                libvlc.setVerboseMode(false);
            }

            // Additional video settings
            libvlc.setFrameSkip(true);
            libvlc.setNetworkCaching(100);

            // This line crashes the app
            LibVLC.restart(this);
            EventHandler.getInstance().addHandler(eventHandler);
            videoHolder.setFormat(PixelFormat.RGBX_8888);
            videoHolder.setKeepScreenOn(true);
            MediaList list = libvlc.getMediaList();
            list.clear();
            list.add(new Media(libvlc, LibVLC.PathToURI(media)), false);
            libvlc.playIndex(0);
        } catch (Exception e) {
            Toast.makeText(this, getString(cz.tyr.android.legoirc.R.string.player_create_error), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void releasePlayer() {
        if (libvlc == null)
            return;

        EventHandler.getInstance().removeHandler(eventHandler);

        libvlc.stop();

        // Clear canvas
        if (videoHolder != null) {
            Canvas canvas = videoHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                videoHolder.unlockCanvasAndPost(canvas);
            }
        }

        libvlc.closeAout();

        videoHolder = null;

        videoWidth = 0;
        videoHeight = 0;
    }

    /**********
     * Events *
     **********/

    private Handler eventHandler = new MyEventHandler(this);

    private static class MyEventHandler extends Handler {
        private WeakReference<Main> mOwner;

        public MyEventHandler(Main owner) {
            mOwner = new WeakReference<Main>(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            Main player = mOwner.get();

            // SamplePlayer events
            if (msg.what == videoSizeChanged) {
                player.setSize(msg.arg1, msg.arg2);
                return;
            }

            // LibVLC events
            Bundle b = msg.getData();
            switch (b.getInt("event")) {
                case EventHandler.MediaPlayerEndReached:
                    player.releasePlayer();
                    break;
                case EventHandler.MediaPlayerPlaying:
                    break;
                case EventHandler.MediaPlayerPaused:
                    break;
                case EventHandler.MediaPlayerStopped:
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    break;
                default:
                    break;
            }
        }
    }
}
