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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements Connector.IDoorListener {

	private Button button;
	private TextView doorState;
	private Connector connector;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		button = (Button) findViewById(R.id.button);
		button.setOnClickListener(button_listener);
		doorState = (TextView) findViewById(R.id.doorState);
		connector = new Connector(this,
				PreferenceManager.getDefaultSharedPreferences(this));
	}

	@Override
	protected void onStart() {
		super.onStart();
		connector.open();
	}

	@Override
	protected void onStop() {
		connector.close();
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_about:
			startActivity(new Intent(
					Intent.ACTION_VIEW,
					Uri.parse("market://details?id=" + getPackageName())));
			return true;
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private final View.OnClickListener button_listener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			connector.pressButton();
		}
	};

	@Override
	public void onDoorClosed() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				doorState.setText(R.string.door_closed);
			}
		});
	}

	@Override
	public void onDoorMoving() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				doorState.setText(R.string.door_moving);
			}
		});
	}

	@Override
	public void onDoorOpened() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				doorState.setText(R.string.door_opened);
			}
		});
	}

	@Override
	public void onDoorInvalid() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				doorState.setText(R.string.door_invalid);
			}
		});
	}

	@Override
	public void onConnectionLost() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				doorState.setText(R.string.connection_problem);
			}
		});
	}
}
