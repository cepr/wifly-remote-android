/*
 * Copyright 2014 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wiflyremote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import android.content.SharedPreferences;
import android.util.Log;

/**
 * Created by cedric on 3/12/14.
 */
public class Connector extends Thread {

	public interface IDoorListener {
		/**
		 * Called when the door is opened.
		 */
		public void onDoorOpened();

		/**
		 * Called when the door is neither opened, nor closed.
		 */
		public void onDoorMoving();

		/**
		 * Called when the door is closed.
		 */
		public void onDoorClosed();

		/**
		 * Called when the door state is invalid.
		 */
		public void onDoorInvalid();
		
		/**
		 * Connection lost.
		 */
		public void onConnectionLost();
	}

	private final static String TAG = "WiFly Connector";
	private Socket socket;
	private StringBuilder buffer = new StringBuilder();
	private boolean opened = false;
	private boolean press_button = false;
	private OutputStream output;
	private InputStream input;
	private IDoorListener listener;
	private static final int STATE_MOVING = 0;
	private static final int STATE_CLOSED = 1;
	private static final int STATE_OPENED = 2;
	private static final int STATE_INVALID = 3;
	private int doorState = -1;
	private SharedPreferences prefs;
	private static final int TIMEOUT_MS = 3000;

	public Connector(IDoorListener listener, SharedPreferences prefs) {
		this.listener = listener;
		this.prefs = prefs;
		start();
	}

	public synchronized void pressButton() {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "pressButton()");
		}
		press_button = true;
		notify();
	}

	public synchronized void open() {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "open()");
		}
		opened = true;
		notify();
	}

	public synchronized void close() {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "Closing socket");
		}
		opened = false;
		try {
			input.close();
			input = null;
		} catch (Exception e) {
			// Ignore the error
		}
		try {
			output.close();
			output = null;
		} catch (Exception e) {
			// Ignore the error
		}
		try {
			socket.close();
		} catch (Exception e) {
			// Ignore the error
		}
		notify();
	}

	@Override
	public void run() {

		// InterruptedException
		try {

			// Thread loop, we never leave it.
			while (true) {

				// The connector stays asleep until "open()" gets called
				synchronized (this) {
					while (!opened) {
						wait();
					}
				}

				try {
					// Connect to the WiFly module
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "Opening socket");
					}
					socket = new Socket(
							prefs.getString("host_address", null),
							Integer.parseInt(prefs.getString("host_port", null)));
					socket.setReuseAddress(true);
					socket.setTcpNoDelay(true);
					socket.setSoTimeout(TIMEOUT_MS);
					output = socket.getOutputStream();
					input = socket.getInputStream();

					// Enter the password
					enterPassword();

					// Main polling loop
					while (opened) {
						if (listener != null) {

							// Read the two sensors
							int top_sensor = get_sensor(2);
							if (BuildConfig.DEBUG) {
								Log.d(TAG, "Door opened sensor = " + top_sensor);
							}
							int bottom_sensor = get_sensor(3);
							if (BuildConfig.DEBUG) {
								Log.d(TAG, "Door closed sensor = "
										+ bottom_sensor);
							}

							// Determine the door state
							int state = ((top_sensor < 100000) ? 2 : 0)
									| ((bottom_sensor < 100000) ? 1 : 0);

							// Compare to the previous state and notify the
							// listener if changed
							if (state != doorState) {
								doorState = state;
								switch (state) {
								case STATE_CLOSED:
									listener.onDoorClosed();
									break;
								case STATE_INVALID:
									listener.onDoorInvalid();
									break;
								case STATE_MOVING:
									listener.onDoorMoving();
									break;
								case STATE_OPENED:
									listener.onDoorOpened();
									break;
								}
							}
						}
						boolean p;
						synchronized (this) {
							wait(1000);
							p = press_button;
							press_button = false;
						}
						if (p) {
							toggle_door();
						}
					}
				} catch (Exception e) {
					// If anything goes wrong with the socket, try again.
					e.printStackTrace();
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "Retrying in 3 seconds");
					}
				}
				if (listener != null) {
					doorState = -1;
					listener.onConnectionLost();
				}
				Thread.sleep(3000);
			}
		} catch (InterruptedException e) {
			// Android is killing us
			return;
		}
	}

	private void enterPassword() throws IOException {
		expect("PASS?");
		send(prefs.getString("password", null) + "\r");
		expect("AOK");
		send("$$$");
		expect("CMD\r\n");
	}

	private void toggle_door() throws IOException, InterruptedException {
		send("set sys output 2\r");
		expect("AOK");
		Thread.sleep(250);
		send("set sys output 0\r");
		expect("AOK");
	}

	private int get_sensor(int id) throws IOException {
		// Send the command
		send("show q " + id + "\r");
		expect("show q " + id + "\r\r\n8");
		while (buffer.length() < 5) {
			read();
		}
		int ret = Integer.valueOf(buffer.substring(0, 5), 16);
		expect(">");
		return ret;
	}

	private StringBuilder read() throws IOException {
		byte[] buf = new byte[1024];
		int size = input.read(buf);
		buffer.append(new String(buf, 0, size));
		if (BuildConfig.DEBUG) {
			Log.d(TAG,
					"Input buffer = "
							+ buffer.toString().replaceAll("\r", "\\\\r")
									.replaceAll("\n", "\\\\n"));
		}
		return buffer;
	}

	private void send(String data) throws IOException {
		if (BuildConfig.DEBUG) {
			Log.d(TAG,
					"Sending "
							+ data.replaceAll("\r", "\\\\r").replaceAll("\n",
									"\\\\n"));
		}
		output.write(data.getBytes());
	}

	private void expect(String data) throws IOException {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "Expecting "
					+ data.replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n"));
		}
		int offset;
		while ((offset = buffer.toString().indexOf(data)) < 0) {
			read();
		}
		buffer.delete(0, offset + data.length());
	}
}
