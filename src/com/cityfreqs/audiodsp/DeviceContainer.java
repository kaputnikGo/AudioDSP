package com.cityfreqs.audiodsp;

import android.hardware.usb.UsbDevice;

public class DeviceContainer {
	// container for usb device info and
	// possible functions
	// vendor-id="1234" product-id="5678" class="255" subclass="66" protocol="1" 
	private UsbDevice device;
	
	public DeviceContainer() {
		// defaults of no values
		device = null;
	}
	
	public DeviceContainer(UsbDevice device) {
		this.device = device;
	}
	
	public void setUsbDevice(UsbDevice device) {
		this.device = device;
	}
	
	public UsbDevice getDevice() {
		return device;
	}
	
	public boolean hasDevice() {
		return device != null;
	}
	
/*
* specific getters   
*/  
	public String getDeviceName() {
		return device.getDeviceName();
	}	
	public int getVendorId() {
		return device.getVendorId();
	}
	public int getDeviceId() {
		return device.getDeviceId();
	}
	public int getProductId() {
		return device.getProductId();
	}
	public int getUsbClass() {
		return device.getDeviceClass();
	}
	public int getUsbSubClass() {
		return device.getDeviceSubclass();
	}
	public int getUsbProtocol() {
		return device.getDeviceProtocol();
	}
}