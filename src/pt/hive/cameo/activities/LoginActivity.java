/*
 Hive Cameo Framework
 Copyright (c) 2008-2020 Hive Solutions Lda.

 This file is part of Hive Cameo Framework.

 Hive Cameo Framework is free software: you can redistribute it and/or modify
 it under the terms of the Apache License as published by the Apache
 Foundation, either version 2.0 of the License, or (at your option) any
 later version.

 Hive Cameo Framework is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 Apache License for more details.

 You should have received a copy of the Apache License along with
 Hive Cameo Framework. If not, see <http://www.apache.org/licenses/>.

 __author__    = João Magalhães <joamag@hive.pt>
 __version__   = 1.0.0
 __revision__  = $LastChangedRevision$
 __date__      = $LastChangedDate$
 __copyright__ = Copyright (c) 2008-2020 Hive Solutions Lda.
 __license__   = Apache License, Version 2.0
 */

package pt.hive.cameo.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import pt.hive.cameo.Info;
import pt.hive.cameo.ProxyRequest;
import pt.hive.cameo.ProxyRequestDelegate;
import pt.hive.cameo.R;
import pt.hive.cameo.util.Layout;

public class LoginActivity extends Activity implements ProxyRequestDelegate {

    /**
     * The path (URL) as a string for the route that is going to be used
     * to perform the login operation.
     */
    protected String loginPath = null;

    /**
     * The identifier (as an integer) of the resource to be displayed as
     * the logo in the associated login view.
     */
    protected int logoId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // sets the complete set of information that is considered to be the
        // base for the login activity
        this.setBase();

        // sets the layout part of the display, this should make sure that
        // all of the elements of the main are properly started
        this.setLayout();
    }

    protected void login() {
        // retrieves the reference to both the username and the password from the
        // corresponding visual elements, these are going to be used as part of
        // the parameters to tbe used in the remote request
        EditText username = this.findViewById(this.getUsernameId());
        EditText password = this.findViewById(this.getPasswordId());

        // creates the list of parameters tha are going to be used for the login
        // request execution (to be sent to the proxy request)
        List<List<String>> parameters = new LinkedList<>();
        parameters.add(new LinkedList<>(Arrays.asList("username", username.getText().toString())));
        parameters.add(new LinkedList<>(Arrays.asList("password", password.getText().toString())));

        // creates a new proxy request object with the current instance as the
        // context and the login path as target, then sets the current instance
        // as the delegate and sets also a series of extra parameters and triggers
        // the initial execution instruction to start the request process
        ProxyRequest request = new ProxyRequest(this, this.loginPath);
        request.setDelegate(this);
        request.setShowDialog(true);
        request.setParameters(parameters);
        request.setUseSession(false);
        request.execute();
    }

    protected void setBase() {
        // tries to retrieve the extras set of parameters from the intent
        // and in case they exist, tries to retrieve the login path (parameter)
        Bundle extras = this.getIntent().getExtras();
        if (extras != null) {
            this.loginPath = extras.getString("LOGIN_PATH");
            this.logoId = extras.getInt("LOGO_ID");
        }

        // removes the title bar from the window (improves readability)
        // this should make things clear on the login
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    protected void setLayout() {
        // saves a reference to the current instance under the self variable
        // so that it may be used by any clojure method
        final LoginActivity self = this;

        // retrieves the identifier of the layout view and then sets it
        // as the base content view for the activity
        int layoutId = this.getLayoutId();
        this.setContentView(layoutId);

        // in case we've received a valid logo identifier the logo image must be
        // updated with the associated resource (customized view)
        if (this.logoId != 0) {
            Drawable logoResource = Layout.getDrawable(this.logoId, this);
            ImageView logo = this.findViewById(this.getLogoId());
            logo.setImageDrawable(logoResource);
        }

        // retrieves the password edit text field and updates it to the
        // sans serif typeface and then updates the transformation method
        EditText password = this.findViewById(this.getPasswordId());
        if (password != null) {
            password.setTypeface(Typeface.SANS_SERIF);
            password.setTransformationMethod(new PasswordTransformationMethod());
            password.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        self.login();
                    }
                    return false;
                }
            });
        }

        // retrieves the reference to the sign in button in the current activity
        // and registers the current instance as the click listener
        Button signIn = findViewById(this.getSigninId());
        if (signIn != null) {
            signIn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    self.login();
                }
            });
        }
    }

    protected int getLayoutId() {
        return R.layout.login;
    }

    protected int getUsernameId() {
        return R.id.username;
    }

    protected int getPasswordId() {
        return R.id.password;
    }

    protected int getSigninId() {
        return R.id.sign_in;
    }

    protected int getLogoId() {
        return R.id.logo;
    }

    protected int getErrorId() {
        return R.id.error;
    }

    @Override
    public void didReceiveJson(ProxyRequest request, JSONObject data) {
        try {
            // verifies if there's an exception set in the received data, if
            // that's the case the exception must be presented to the user
            boolean hasException = data.has("exception");
            if (hasException) {
                this.handleException(data);
                return;
            }

            // retrieves the value for the session id that has just been
            // retrieve by the remote call and sets it under the current
            // preferences settings so that it may be used latter
            String sessionId = data.getString("session_id");
            String username = data.getString("username");
            SharedPreferences preferences = this.getSharedPreferences("cameo", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("sessionId", sessionId);
            editor.putString("username", username);
            editor.apply();

            // finishes the current activity as it no longer needs to be
            // present for the login operation (already logged in)
            this.finish();
        } catch (JSONException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void didReceiveError(ProxyRequest request, Object error) {
        Log.d(Info.TAG, String.format("Error received in login request: %s", error));
        TextView errorText = this.findViewById(this.getErrorId());
        if (errorText != null) {
            errorText.setText(error.toString());
            errorText.setVisibility(View.VISIBLE);
        }
    }

    public void handleException(JSONObject data) {
        try {
            JSONObject exception = data.getJSONObject("exception");
            String message = exception.getString("message");
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Error in submission");
            alertDialog.setMessage(message);
            alertDialog.show();
        } catch (JSONException exception) {
            throw new RuntimeException(exception);
        }
    }
}
