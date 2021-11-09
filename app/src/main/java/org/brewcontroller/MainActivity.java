package org.brewcontroller;

import android.bluetooth.*;
import android.content.*;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.google.android.material.textfield.TextInputEditText;
import org.brewcontroller.bluetooth.BluetoothLeService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BluetoothLeService bluetoothService;

    private String deviceAddress="C4:4F:33:53:7A:57";

    static ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new      ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        setContentView(R.layout.activity_main);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bluetoothService = ((BluetoothLeService.LocalBinder) service).getService();
            if (bluetoothService != null) {
                if (!bluetoothService.initialize()) {
                    Log.e(BluetoothLeService.TAG, "Unable to initialize Bluetooth");
                    finish();
                }
                // perform device connection
                bluetoothService.connect(deviceAddress);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothService = null;
        }
    };


    // Demonstrates how to iterate through the supported GATT
    // Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the
    // ExpandableListView on the UI.
    private void triggerTemperatureRead(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        BluetoothGattService bluetoothGattService = gattServices.stream().filter(item -> item.getUuid().equals(BluetoothLeService.UUID_CONTROLLER_SERVICE)).findFirst().get();
        BluetoothGattCharacteristic realtimeTempCharacteristic = bluetoothGattService.getCharacteristic(BluetoothLeService.UUID_REAL_TIME_TEMPERATURE_MEASUREMENT);
        BluetoothGattCharacteristic destinationTempCharacteristic = bluetoothGattService.getCharacteristic(BluetoothLeService.UUID_DESTINATION_TEMPERATURE_MEASUREMENT);
        bluetoothService.readCharacteristic(realtimeTempCharacteristic);
        bluetoothService.readCharacteristic(destinationTempCharacteristic);

    }

    private void updateTemperature(float extra_data){
        TextView viewById = findViewById(R.id.currentTemperature);
        viewById.setText(String.valueOf(extra_data));
    }
    private void updateDestinationTemperature(float extra_data){
        TextView viewById = findViewById(R.id.destinationTemperature);
        viewById.setText(String.valueOf(extra_data));
    }


        private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                 if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                    bluetoothService.discoverServices();
                }  if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                    updateConnectionState(R.string.disconnected);
                } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                     updateConnectionState(R.string.connected);
                     // Show all the supported services and characteristics on the user interface.
                    triggerTemperatureRead(bluetoothService.getSupportedGattServices());
                } else if (BluetoothLeService.ACTION_TEMPERATURE_READ.equals(action)) {
                     // Show all the supported services and characteristics on the user interface.
                    updateTemperature(intent.getFloatExtra("EXTRA_DATA", -127.0f));
                } else if (BluetoothLeService.ACTION_DESTINATION_TEMPERATURE.equals(action)) {
                     // Show all the supported services and characteristics on the user interface.
                    updateDestinationTemperature(intent.getFloatExtra("EXTRA_DATA", -127.0f));
                }
            }
        };


    private void updateConnectionState(int connected) {
        Button viewById = (Button) findViewById(R.id.connect);
        viewById.setText(connected);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothService != null) {
            final boolean result = bluetoothService.connect(deviceAddress);
            Log.d(BluetoothLeService.TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    public void onClick(View view){
   //     bluetoothService.connect(deviceAddress);
    }

    public void setNewDestinationValue(View view){
        TextInputEditText text = (TextInputEditText) findViewById(R.id.newTempInputField);
        bluetoothService.setNewDestinationValue(text.getEditableText().toString());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_TEMPERATURE_READ);
        intentFilter.addAction(BluetoothLeService.ACTION_TEMPERATURE_CHANGED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DESTINATION_TEMPERATURE);
        return intentFilter;
    }
}
