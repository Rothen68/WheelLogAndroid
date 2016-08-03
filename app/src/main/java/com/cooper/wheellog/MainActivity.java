package com.cooper.wheellog;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Handler;
import android.view.View;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.viewpagerindicator.LinePageIndicator;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    Menu mMenu;
    MenuItem miSearch;
    MenuItem miWheel;
    MenuItem miWatch;
    MenuItem miLogging;

    TextView tvSpeed;
    TextView tvTemperature;
    TextView tvCurrent;
    TextView tvPower;
    TextView tvVoltage;
    TextView tvBattery;
    TextView tvFanStatus;
    TextView tvTopSpeed;
    TextView tvDistance;
    TextView tvModel;
    TextView tvName;
    TextView tvVersion;
    TextView tvSerial;
    TextView tvTotalDistance;
    TextView tvRideTime;
    TextView tvMode;

    WheelView wheelView;

    private WheelLog wheelLog;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private String mDeviceAddress;
    private int mConnectionState = BluetoothLeService.STATE_DISCONNECTED;
    private boolean doubleBackToExitPressedOnce = false;
    private Snackbar snackbar;
    int viewPagerPage = 0;

    private final String TAG = "MainActivity";
    private static final int RESULT_PERMISSION_REQUEST_CODE = 10;
    private static final int RESULT_DEVICE_SCAN_REQUEST = 20;
    private static final int RESULT_REQUEST_ENABLE_BT = 30;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, getResources().getString(R.string.error_bluetooth_not_initialised));
                Toast.makeText(MainActivity.this, R.string.error_bluetooth_not_initialised, Toast.LENGTH_SHORT).show();
                finish();
            }

            if (mBluetoothLeService.getConnectionState() == BluetoothLeService.STATE_DISCONNECTED &&
                    mDeviceAddress != null && !mDeviceAddress.isEmpty())
                connectToWheel();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mBluetoothUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Constants.ACTION_BLUETOOTH_CONNECTING.equals(action)) {
                Log.d(TAG, "Bluetooth Connecting");
                setConnectionState(BluetoothLeService.STATE_CONNECTING);
            } else if (Constants.ACTION_BLUETOOTH_CONNECTED.equals(action) && mConnectionState != BluetoothLeService.STATE_CONNECTED) {
                Log.d(TAG, "Bluetooth connected");
                configureDisplay(wheelLog.getWheelType());
                setConnectionState(BluetoothLeService.STATE_CONNECTED);
            } else if (Constants.ACTION_BLUETOOTH_DISCONNECTED.equals(action)) {
                Log.d(TAG, "Bluetooth disconnected");
                setConnectionState(BluetoothLeService.STATE_DISCONNECTED);
            } else if (Constants.ACTION_WHEEL_DATA_AVAILABLE.equals(action)) {
                if (wheelLog.getWheelType() == Constants.WHEEL_TYPE_KINGSONG) {
                    if (wheelLog.getName().isEmpty())
                        sendBroadcast(new Intent(Constants.ACTION_REQUEST_KINGSONG_NAME_DATA));
                    else if (wheelLog.getSerial().isEmpty())
                        sendBroadcast(new Intent(Constants.ACTION_REQUEST_KINGSONG_SERIAL_DATA));
                }
                updateScreen();
            } else if (Constants.ACTION_PEBBLE_SERVICE_STARTED.equals(action)) {
                if (miWatch != null) {
                    miWatch.setIcon(R.drawable.ic_action_watch_orange);
                    miWatch.setTitle(R.string.stop_pebble_service);
                }
            } else if (Constants.ACTION_LOGGING_SERVICE_STARTED.equals(action)) {
                if (intent.hasExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION)) {
                    String filePath = intent.getStringExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION);
                    showSnackBar(getResources().getString(R.string.started_logging) + filePath, 5000);
                }
            }
        }
    };

    private void setConnectionState(int connectionState) {

        switch (connectionState) {
            case BluetoothLeService.STATE_CONNECTED:
                if (mDeviceAddress != null && !mDeviceAddress.isEmpty())
                    SettingsManager.setLastAddr(getApplicationContext(), mDeviceAddress);
                break;
            case BluetoothLeService.STATE_CONNECTING:
                if (mConnectionState == BluetoothLeService.STATE_CONNECTING)
                    showSnackBar(R.string.bluetooth_direct_connect_failed);
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                break;
        }
        mConnectionState = connectionState;
        setMenuIconStates();
    }

    private void setMenuIconStates() {
        if (mMenu == null)
            return;

        if (mDeviceAddress == null || mDeviceAddress.isEmpty()) {
            miWheel.setEnabled(false);
            miWheel.getIcon().setAlpha(64);
        }

        switch (mConnectionState) {
            case BluetoothLeService.STATE_CONNECTED:
                miWheel.setIcon(R.drawable.ic_action_wheel_orange);
                miWheel.setTitle(R.string.disconnect_from_wheel);
                miSearch.setEnabled(false);
                miSearch.getIcon().setAlpha(64);
                break;
            case BluetoothLeService.STATE_CONNECTING:
                miWheel.setIcon(R.drawable.anim_wheel_icon);
                miWheel.setTitle(R.string.disconnect_from_wheel);
                ((AnimationDrawable) miWheel.getIcon()).start();
                miSearch.setEnabled(false);
                miSearch.getIcon().setAlpha(64);
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                miWheel.setIcon(R.drawable.ic_action_wheel_white);
                miWheel.setTitle(R.string.connect_to_wheel);
                miSearch.setEnabled(true);
                miSearch.getIcon().setAlpha(255);
                break;
        }

        if (PebbleService.isInstanceCreated()) {
            miWatch.setIcon(R.drawable.ic_action_watch_orange);
        } else {
            miWatch.setIcon(R.drawable.ic_action_watch_white);
        }

        if (LoggingService.isInstanceCreated()) {
            miLogging.setTitle(R.string.stop_data_service);
            miLogging.setIcon(R.drawable.ic_action_logging_orange);
        } else {
            miLogging.setTitle(R.string.start_data_service);
            miLogging.setIcon(R.drawable.ic_action_logging_white);
        }
    }
    
    private void configureDisplay(int wheelType) {
        TextView tvWaitText = (TextView) findViewById(R.id.tvWaitText);
        TextView tvTitleSpeed = (TextView) findViewById(R.id.tvTitleSpeed);
        TextView tvTitleMaxSpeed = (TextView) findViewById(R.id.tvTitleTopSpeed);
        TextView tvTitleBattery = (TextView) findViewById(R.id.tvTitleBattery);
        TextView tvTitleDistance = (TextView) findViewById(R.id.tvTitleDistance);
        TextView tvTitleRideTime = (TextView) findViewById(R.id.tvTitleRideTime);
        TextView tvTitleVoltage = (TextView) findViewById(R.id.tvTitleVoltage);
        TextView tvTitleCurrent = (TextView) findViewById(R.id.tvTitleCurrent);
        TextView tvTitlePower = (TextView) findViewById(R.id.tvTitlePower);
        TextView tvTitleTemperature = (TextView) findViewById(R.id.tvTitleTemperature);
        TextView tvTitleFanStatus = (TextView) findViewById(R.id.tvTitleFanStatus);
        TextView tvTitleMode = (TextView) findViewById(R.id.tvTitleMode);
        TextView tvTitleTotalDistance = (TextView) findViewById(R.id.tvTitleTotalDistance);
        TextView tvTitleName = (TextView) findViewById(R.id.tvTitleName);
        TextView tvTitleModel = (TextView) findViewById(R.id.tvTitleModel);
        TextView tvTitleVersion = (TextView) findViewById(R.id.tvTitleVersion);
        TextView tvTitleSerial = (TextView) findViewById(R.id.tvTitleSerial);

        if (wheelType == Constants.WHEEL_TYPE_KINGSONG) {
            tvWaitText.setVisibility(View.GONE);
            tvTitleSpeed.setVisibility(View.VISIBLE);
            tvSpeed.setVisibility(View.VISIBLE);
            tvTitleMaxSpeed.setVisibility(View.VISIBLE);
            tvTopSpeed.setVisibility(View.VISIBLE);
            tvTitleBattery.setVisibility(View.VISIBLE);
            tvBattery.setVisibility(View.VISIBLE);
            tvTitleDistance.setVisibility(View.VISIBLE);
            tvDistance.setVisibility(View.VISIBLE);
            tvTitleRideTime.setVisibility(View.VISIBLE);
            tvRideTime.setVisibility(View.VISIBLE);
            tvTitleVoltage.setVisibility(View.VISIBLE);
            tvVoltage.setVisibility(View.VISIBLE);
            tvTitleCurrent.setVisibility(View.VISIBLE);
            tvCurrent.setVisibility(View.VISIBLE);
            tvTitlePower.setVisibility(View.VISIBLE);
            tvPower.setVisibility(View.VISIBLE);
            tvTitleTemperature.setVisibility(View.VISIBLE);
            tvTemperature.setVisibility(View.VISIBLE);
            tvTitleFanStatus.setVisibility(View.VISIBLE);
            tvFanStatus.setVisibility(View.VISIBLE);
            tvTitleMode.setVisibility(View.VISIBLE);
            tvMode.setVisibility(View.VISIBLE);
            tvTitleTotalDistance.setVisibility(View.VISIBLE);
            tvTotalDistance.setVisibility(View.VISIBLE);
            tvTitleName.setVisibility(View.VISIBLE);
            tvName.setVisibility(View.VISIBLE);
            tvTitleModel.setVisibility(View.VISIBLE);
            tvModel.setVisibility(View.VISIBLE);
            tvTitleVersion.setVisibility(View.VISIBLE);
            tvVersion.setVisibility(View.VISIBLE);
            tvTitleSerial.setVisibility(View.VISIBLE);
            tvSerial.setVisibility(View.VISIBLE);

        } else if (wheelType == Constants.WHEEL_TYPE_GOTWAY) {
            tvWaitText.setVisibility(View.GONE);
            tvTitleSpeed.setVisibility(View.VISIBLE);
            tvSpeed.setVisibility(View.VISIBLE);
            tvTitleMaxSpeed.setVisibility(View.VISIBLE);
            tvTopSpeed.setVisibility(View.VISIBLE);
            tvTitleBattery.setVisibility(View.VISIBLE);
            tvBattery.setVisibility(View.VISIBLE);
            tvTitleDistance.setVisibility(View.VISIBLE);
            tvDistance.setVisibility(View.VISIBLE);
            tvTitleRideTime.setVisibility(View.VISIBLE);
            tvRideTime.setVisibility(View.VISIBLE);
            tvTitleVoltage.setVisibility(View.VISIBLE);
            tvVoltage.setVisibility(View.VISIBLE);
            tvTitleCurrent.setVisibility(View.VISIBLE);
            tvCurrent.setVisibility(View.VISIBLE);
            tvTitlePower.setVisibility(View.VISIBLE);
            tvPower.setVisibility(View.VISIBLE);
            tvTitleTemperature.setVisibility(View.VISIBLE);
            tvTemperature.setVisibility(View.VISIBLE);
            tvTitleTotalDistance.setVisibility(View.VISIBLE);
            tvTotalDistance.setVisibility(View.VISIBLE);

        } else {
            tvWaitText.setVisibility(View.VISIBLE);
            tvTitleSpeed.setVisibility(View.GONE);
            tvSpeed.setVisibility(View.GONE);
            tvTitleMaxSpeed.setVisibility(View.GONE);
            tvTopSpeed.setVisibility(View.GONE);
            tvTitleBattery.setVisibility(View.GONE);
            tvBattery.setVisibility(View.GONE);
            tvTitleDistance.setVisibility(View.GONE);
            tvDistance.setVisibility(View.GONE);
            tvTitleRideTime.setVisibility(View.GONE);
            tvRideTime.setVisibility(View.GONE);
            tvTitleVoltage.setVisibility(View.GONE);
            tvVoltage.setVisibility(View.GONE);
            tvTitleCurrent.setVisibility(View.GONE);
            tvCurrent.setVisibility(View.GONE);
            tvTitlePower.setVisibility(View.GONE);
            tvPower.setVisibility(View.GONE);
            tvTitleTemperature.setVisibility(View.GONE);
            tvTemperature.setVisibility(View.GONE);
            tvTitleFanStatus.setVisibility(View.GONE);
            tvFanStatus.setVisibility(View.GONE);
            tvTitleMode.setVisibility(View.GONE);
            tvMode.setVisibility(View.GONE);
            tvTitleTotalDistance.setVisibility(View.GONE);
            tvTotalDistance.setVisibility(View.GONE);
            tvTitleName.setVisibility(View.GONE);
            tvName.setVisibility(View.GONE);
            tvTitleModel.setVisibility(View.GONE);
            tvModel.setVisibility(View.GONE);
            tvTitleVersion.setVisibility(View.GONE);
            tvVersion.setVisibility(View.GONE);
            tvTitleSerial.setVisibility(View.GONE);
            tvSerial.setVisibility(View.GONE);
        }
    }

    private void updateScreen() {
        if (viewPagerPage == 0) {
            wheelView.setSpeed(wheelLog.getSpeed());
            wheelView.setBattery(wheelLog.getBatteryLevel());
            wheelView.setTemperature(wheelLog.getTemperature());
            wheelView.setRideTime(wheelLog.getCurrentTimeString());
            wheelView.setTopSpeed(wheelLog.getTopSpeedDouble());
            wheelView.setDistance(wheelLog.getDistanceDouble());
            wheelView.setTotalDistance(wheelLog.getTotalDistanceDouble());
            wheelView.setVoltage(wheelLog.getVoltageDouble());
            wheelView.setCurrent(wheelLog.getPowerDouble());
        } else if (viewPagerPage == 1) {
            tvSpeed.setText(String.format(Locale.US, "%.1f km/h", wheelLog.getSpeedDouble()));
            tvVoltage.setText(String.format(Locale.US, "%.2fV", wheelLog.getVoltageDouble()));
            tvTemperature.setText(String.format(Locale.US, "%d°C", wheelLog.getTemperature()));
            tvCurrent.setText(String.format(Locale.US, "%.2fA", wheelLog.getCurrentDouble()));
            tvPower.setText(String.format(Locale.US, "%.2fW", wheelLog.getPowerDouble()));
            tvBattery.setText(String.format(Locale.US, "%d%%", wheelLog.getBatteryLevel()));
            tvFanStatus.setText(wheelLog.getFanStatus() == 0 ? "Off" : "On");
            tvTopSpeed.setText(String.format(Locale.US, "%.1f km/h", wheelLog.getTopSpeedDouble()));
            tvDistance.setText(String.format(Locale.US, "%.2f km", wheelLog.getDistanceDouble()));
            tvTotalDistance.setText(String.format(Locale.US, "%.2f km", wheelLog.getTotalDistanceDouble()));
            tvVersion.setText(String.format(Locale.US, "%.2f", wheelLog.getVersion()/100.0));
            tvName.setText(wheelLog.getName());
            tvModel.setText(wheelLog.getModel());
            tvSerial.setText(wheelLog.getSerial());
            tvRideTime.setText(wheelLog.getCurrentTimeString());
            tvMode.setText(getResources().getStringArray(R.array.modes)[wheelLog.getMode()]);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPageAdapter adapter = new ViewPageAdapter(this);
        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(adapter);
        LinePageIndicator titleIndicator = (LinePageIndicator)findViewById(R.id.indicator);
        titleIndicator.setViewPager(pager);
        pager.addOnPageChangeListener(pageChangeListener);

        mDeviceAddress = SettingsManager.getLastAddr(getApplicationContext());
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        wheelLog = (WheelLog) getApplicationContext();

        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvCurrent = (TextView) findViewById(R.id.tvCurrent);
        tvPower = (TextView) findViewById(R.id.tvPower);
        tvTemperature = (TextView) findViewById(R.id.tvTemperature);
        tvVoltage = (TextView) findViewById(R.id.tvVoltage);
        tvBattery = (TextView) findViewById(R.id.tvBattery);
        tvFanStatus = (TextView) findViewById(R.id.tvFanStatus);
        tvTopSpeed = (TextView) findViewById(R.id.tvTopSpeed);
        tvDistance = (TextView) findViewById(R.id.tvDistance);
        tvTotalDistance = (TextView) findViewById(R.id.tvTotalDistance);
        tvModel = (TextView) findViewById(R.id.tvModel);
        tvName = (TextView) findViewById(R.id.tvName);
        tvVersion = (TextView) findViewById(R.id.tvVersion);
        tvSerial = (TextView) findViewById(R.id.tvSerial);
        tvRideTime = (TextView) findViewById(R.id.tvRideTime);
        tvMode = (TextView) findViewById(R.id.tvMode);
        wheelView = (WheelView) findViewById(R.id.wheelView);
        TextClock textClock = (TextClock) findViewById(R.id.textClock);
        textClock.setTypeface(Typefaces.get(this, "fonts/prime.otf"));
        wheelView.setMaxSpeed(300);
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        } else if (!mBluetoothAdapter.isEnabled()) {
            // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
            // fire an intent to display a dialog asking the user to grant permission to enable it.
            if (!mBluetoothAdapter.isEnabled())
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), RESULT_REQUEST_ENABLE_BT);
        } else {
            startBluetoothService();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mBluetoothLeService != null &&
                mConnectionState != mBluetoothLeService.getConnectionState())
            setConnectionState(mBluetoothLeService.getConnectionState());

        if (wheelLog.getWheelType() > 0)
            configureDisplay(wheelLog.getWheelType());

        registerReceiver(mBluetoothUpdateReceiver, BluetoothLeService.makeIntentFilter());
        updateScreen();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        setMenuIconStates();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBluetoothUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (PebbleService.isInstanceCreated())
            stopPebbleService();

        if (LoggingService.isInstanceCreated())
            stopService(new Intent(getApplicationContext(), LoggingService.class));

        if (mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            stopService(new Intent(getApplicationContext(), BluetoothLeService.class));
            mBluetoothLeService = null;
        }
        wheelLog.reset();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;
        miSearch = mMenu.findItem(R.id.miSearch);
        miWheel = mMenu.findItem(R.id.miWheel);
        miWatch = mMenu.findItem(R.id.miWatch);
        miLogging = mMenu.findItem(R.id.miLogging);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.miSearch:
                startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), RESULT_DEVICE_SCAN_REQUEST);
                return true;
            case R.id.miWheel:
                if (mConnectionState == BluetoothLeService.STATE_DISCONNECTED)
                    connectToWheel();
                else
                    disconnectFromWheel();
                return true;
            case R.id.miLogging:
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkExternalFilePermission())
                    requestExternalFilePermission();
                else
                {
                    if (!LoggingService.isInstanceCreated())
                        startLoggingService();
                    else
                        stopLoggingService();
                }
                return true;
            case R.id.miWatch:
                if (!PebbleService.isInstanceCreated())
                    startPebbleService();
                else
                    stopPebbleService();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            finish();
            return;
        }

        doubleBackToExitPressedOnce = true;
        showSnackBar(R.string.back_to_exit);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }

    ViewPager.SimpleOnPageChangeListener pageChangeListener = new ViewPager.SimpleOnPageChangeListener(){
        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            viewPagerPage = position;
            updateScreen();
        }
    };

    private void showSnackBar(int msg) { showSnackBar(getString(msg)); }
    private void showSnackBar(String msg) { showSnackBar(msg, 2000); }
    private void showSnackBar(String msg, int timeout) {
        if (snackbar == null) {
            View mainView = findViewById(R.id.main_view);
            snackbar = Snackbar
                    .make(mainView, "", Snackbar.LENGTH_LONG);
            snackbar.getView().setBackgroundResource(R.color.primary_dark);
            snackbar.setDuration(timeout);
        }
        snackbar.setText(msg);
        snackbar.show();
    }


    private void stopLoggingService() { startLoggingService(false); }
    private void startLoggingService() { startLoggingService(true); }
    private void startLoggingService(boolean start) {
        Intent dataLoggerServiceIntent = new Intent(getApplicationContext(), LoggingService.class);
        if (start) {
            startService(dataLoggerServiceIntent);
            miLogging.setIcon(R.drawable.ic_action_logging_orange);
            miLogging.setTitle(R.string.stop_data_service);
        } else {
            stopService(dataLoggerServiceIntent);
            miLogging.setIcon(R.drawable.ic_action_logging_white);
            miLogging.setTitle(R.string.start_data_service);
        }
    }
    private void stopPebbleService() { startPebbleService(false);}
    private void startPebbleService() { startPebbleService(true);}
    private void startPebbleService(boolean start) {
        Intent pebbleServiceIntent = new Intent(getApplicationContext(), PebbleService.class);
        if (start) {
            startService(pebbleServiceIntent);
        } else {
            stopService(pebbleServiceIntent);
            miWatch.setIcon(R.drawable.ic_action_watch_white);
            miWatch.setTitle(R.string.start_pebble_service);
        }
    }

    private void startBluetoothService() {
        Intent bluetoothServiceIntent = new Intent(getApplicationContext(), BluetoothLeService.class);
        startService(bluetoothServiceIntent);
        bindService(bluetoothServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void connectToWheel() { connectToWheel(true);}
    private void disconnectFromWheel() { connectToWheel(false);}
    private void connectToWheel(boolean connect) {

        if (connect) {
            Boolean connecting = mBluetoothLeService.connect(mDeviceAddress);
            if (!connecting)
                showSnackBar(R.string.connection_failed);
        }
        else {
            mBluetoothLeService.disconnect();
            mBluetoothLeService.close();
        }
    }

    private boolean checkExternalFilePermission(){
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestExternalFilePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)){
            Toast.makeText(this, "External write permission is required to write logs. Please allow in App Settings for additional functionality.", Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, RESULT_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case RESULT_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    startLoggingService();
                else
                    Toast.makeText(this, "External write permission is required to write logs. Please allow in App Settings for additional functionality.", Toast.LENGTH_LONG).show();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_DEVICE_SCAN_REQUEST:
                if (resultCode == RESULT_OK) {
                    mDeviceAddress = data.getStringExtra("MAC");
                    wheelLog.reset();
                    updateScreen();
                    mBluetoothLeService.close();
                    connectToWheel();
                }
                break;
            case RESULT_REQUEST_ENABLE_BT:
                if (mBluetoothAdapter.isEnabled())
                    startBluetoothService();
                else {
                    Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
    }
}