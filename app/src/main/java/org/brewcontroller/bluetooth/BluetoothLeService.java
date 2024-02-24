package org.brewcontroller.bluetooth;

import android.app.Service;
import android.bluetooth.*;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;

public class BluetoothLeService extends Service {

    public static final UUID UUID_REAL_TIME_TEMPERATURE_MEASUREMENT = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    public static final UUID UUID_DESTINATION_TEMPERATURE_MEASUREMENT = UUID.fromString("3625d6cc-3226-4497-9d42-a6df047f4300");
    public static final UUID UUID_CONTROLLER_SERVICE = UUID.fromString("dce32293-581b-4ce1-b9b0-29634f77e412");
    public static final String ACTION_TEMPERATURE_CHANGED = "TEMPERATURE_CHANGED";
    public static final String ACTION_TEMPERATURE_READ = "TEMPERATURE_READ";
    public static final String ACTION_DESTINATION_TEMPERATURE = "DESTINATION_TEMPERATURE";
    private static final UUID UUID_CLIENT_CHARACTERISTICS_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private Binder binder = new LocalBinder();

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTED = 2;


    public static final String TAG = "BluetoothLeService";
    private BluetoothGatt bluetoothGatt;

    private BluetoothAdapter bluetoothAdapter;

    public boolean initialize() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        return true;
    }


    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        byte[] value = characteristic.getValue();
        ByteBuffer wrap = ByteBuffer.wrap(value);
        ByteBuffer order = wrap.order(ByteOrder.LITTLE_ENDIAN);
        intent.putExtra("EXTRA_DATA", order.getFloat());
        sendBroadcast(intent);
    }


    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                broadcastUpdate(ACTION_GATT_CONNECTED);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            this.onCharacteristicRead(gatt, characteristic, characteristic.getValue(), status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            this.onCharacteristicChanged(gatt, characteristic, characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value)  {
            if (UUID_REAL_TIME_TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid())) {
                broadcastUpdate(ACTION_TEMPERATURE_READ, characteristic);
            }
            if (UUID_DESTINATION_TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid())) {
                broadcastUpdate(ACTION_DESTINATION_TEMPERATURE, characteristic);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "Descriptor wrote "+descriptor.getUuid()+"status: "+status);
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (UUID_REAL_TIME_TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid())) {
                    broadcastUpdate(ACTION_TEMPERATURE_READ, characteristic);
                }
                if (UUID_DESTINATION_TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid())) {
                    broadcastUpdate(ACTION_DESTINATION_TEMPERATURE, characteristic);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered received: " + status);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            }
        }
    };

    public void discoverServices() {
        bluetoothGatt.discoverServices();
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null) return null;
        return bluetoothGatt.getServices();
    }

    public void setupCharacteristic(BluetoothGattCharacteristic characteristic){
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTICS_CONFIGURATION);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        bluetoothGatt.writeDescriptor(descriptor);
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);

    }


    public boolean connectDisconnect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        try {
//            if(bluetoothGatt!=null &&)
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            // connect to the GATT server on the device
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
            return bluetoothGatt.connect();
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Device not found with provided address.  Unable to connect.");
            return false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setNewDestinationValue(String text) {
        BluetoothGattService bluetoothGattService = getSupportedGattServices().stream().filter(item -> item.getUuid().equals(BluetoothLeService.UUID_CONTROLLER_SERVICE)).findFirst().get();
        BluetoothGattCharacteristic destinationTempCharacteristic = bluetoothGattService.getCharacteristic(BluetoothLeService.UUID_DESTINATION_TEMPERATURE_MEASUREMENT);
        destinationTempCharacteristic.setValue(text);
        destinationTempCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(destinationTempCharacteristic);
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }


}
