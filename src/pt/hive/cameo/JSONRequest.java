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

package pt.hive.cameo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * Main class for the remote HTTP request that is responsible
 * for handling the connection and the serialization/deserialization.
 *
 * @author João Magalhães <joamag@hive.pt>
 */
public class JSONRequest {

    /**
     * The delegate that is going to be notified about the changes
     * in the state for the connection (success and failure).
     */
    private JSONRequestDelegate delegate = null;

    /**
     * The (Android) context that is going to be used for the retrieval
     * of some global (application) values (eg: settings).
     */
    private Context context = null;

    /**
     * The string based URL to be used in the JSON request, should be a full
     * and canonical URL value.
     */
    private String url = null;

    /**
     * A list contain a series of list for the complete parameters to be sent
     * in the request, this is going to be either sent via GET parameter if
     * there's no payload or as URLEncoded if it's a POST request.
     */
    private List<List<String>> parameters = null;

    /**
     * The name of the HTTP method (eg: GET, POST, DELETE, etc.) that is going
     * to be used in the JSON request.
     */
    private String requestMethod = null;

    /**
     * The body as a JSON object to be sent as the payload of the request.
     */
    private JSONObject body = null;

    /**
     * The timestamp of the last received response or error from the server
     * side, may be used to determine the state.
     */
    private long lastResponse = 0;

    /**
     * Small meta information object that may be used to provide some extra
     * content to the caller method/instance.
     */
    private Object meta = null;

    /**
     * The last connection that has been created for the handling of this
     * JSON request, may be used for diagnostics.
     */
    private HttpURLConnection urlConnection = null;

    public JSONRequest() {
    }

    public JSONRequest(String url) {
        this();
        this.url = url;
    }

    public JSONRequest(String url, List<List<String>> parameters) {
        this(url);
        this.parameters = parameters;
    }

    /**
     * Converts the provided input stream into a valid string sequence eligible
     * to be used by more conventional method.
     *
     * @param stream The stream that is going to be used for reading the complete
     *               set of data and convert it into a "simple" string value.
     * @return The final string value retrieved from the input stream.
     * @throws IOException Raised when the provided stream is not valid meaning
     *                     that it's not possible to read data from it.
     */
    private static String convertStreamToString(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();

        try {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                builder.append(line + "\n");
            }
        } finally {
            stream.close();
        }
        return builder.toString();
    }

    public String load() {
        try {
            return this.execute();
        } catch (final Exception exception) {
            if (this.delegate != null) {
                this.delegate.didReceiveError(this, exception);
            }
        } finally {
            this.lastResponse = System.currentTimeMillis();
        }
        return null;
    }

    public String execute() throws IOException, JSONException {
        // defines the default result value as simple null value
        String result = null;

        // constructs the final URL string value according to the provided
        // set of parameters defined in the JSON request instance
        String url = this.constructUrl();

        // uses the constructed URL string value to create a URL instance
        // and then uses that same instance to build an HTTP URL connection
        URL _url = new URL(url);
        this.urlConnection = (HttpURLConnection) _url.openConnection();

        // writes the series of package related values to the connection so
        // that all the possible information is set on it
        this.writePackage(urlConnection);

        // writes the series of extra values to the connection so that all the
        // possible information is set on it
        this.writeExtras(urlConnection);

        // in case the request method is defined sets it on the
        // current URL connection value
        if (this.requestMethod != null) {
            this.urlConnection.setRequestMethod(this.requestMethod);
        }

        // in case there's a valid body payload defined sets that same payload
        // in the current URL connection
        if (this.body != null) {
            this.writeBody(this.urlConnection);
        }

        // creates the buffered input stream from the input stream that is
        // "exposed" by the URL connection
        InputStream stream = new BufferedInputStream(this.urlConnection.getInputStream());

        try {
            // retrieves the contents from the input stream and then converts
            // this same result into a string based result value, notice that
            // the main interaction with the server side occurs by request the
            // input stream, so this call should trigger the main interaction
            result = JSONRequest.convertStreamToString(stream);
        } finally {
            // closes the base stream as it's not longer going to be used for
            // any kind of read operation
            stream.close();
        }

        // creates a JSON object from the provided data (may raise exception)
        // this object may be safely used for JSON structured operations
        JSONObject data = new JSONObject(result);

        // verifies if there's a delegate currently defined and if that's
        // the case calls it using the proper strategy
        if (this.delegate != null) {
            this.delegate.didReceiveJson(this, data);
        }

        // returns the final string based result (contents) to
        // the caller method
        return result;
    }

    /**
     * Constructs the complete URL value taking into account the
     * currently defined parameters that are going to be part as
     * GET based values/parameters.
     *
     * @return The string containing the complete URL with the
     * GET parameters already included.
     */
    private String constructUrl() {
        if (this.parameters == null || this.parameters.isEmpty()) {
            return this.url;
        }
        String parameters = this.constructParameters();
        String url = String.format("%s?%s", this.url, parameters);
        return url;
    }

    /**
     * Constructs the multiple GET parameters that are going to be sent
     * together with the URL request.
     *
     * @return The final GET parameters serialized as a string.
     */
    private String constructParameters() {
        StringBuilder buffer = new StringBuilder();
        for (List<String> parameter : this.parameters) {
            String parameterS = String.format("%s=%s&", parameter.get(0), parameter.get(1));
            buffer.append(parameterS);
        }
        return buffer.toString();
    }

    private void writeBody(URLConnection urlConnection) throws IOException {
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Content-Type", "application/json");
        OutputStream output = urlConnection.getOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(output);
        writer.write(this.body.toString());
        writer.flush();
    }

    private void writePackage(URLConnection urlConnection) {
        // sets the initial info value as invalid, useful for latter
        // null verification operations
        PackageInfo info = null;

        // in case there's no valid context defined must return the
        // control flow immediately to avoid possible issues
        if (this.context == null) {
            return;
        }

        // retrieves the reference to the package manager instance
        // that is going to be used for package retrieval
        PackageManager manager = this.context.getPackageManager();
        if (manager == null) {
            return;
        }

        // tries to retrieve the information regarding the current package
        // if there's a problem in such retrieve the control flow is returned
        try {
            info = manager.getPackageInfo(this.context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException exception) {
        }
        if (info == null) {
            return;
        }

        // writes the multiple package related values to the as properties these
        // are going to be passed as HTTP header latter on
        if (info.packageName != null) {
            urlConnection.setRequestProperty("X-Android-Package", info.packageName);
        }
        if (info.versionName != null) {
            urlConnection.setRequestProperty("X-Android-Package-Version", info.versionName);
        }
    }

    private void writeExtras(URLConnection urlConnection) {
        // sets a series of general diagnostics purpose headers related with
        // the current device (eg: OS version, manufacturer, model, etc.)
        if (Build.MODEL != null) {
            urlConnection.setRequestProperty("X-Android-Model", Build.MODEL);
        }
        if (Build.MANUFACTURER != null) {
            urlConnection.setRequestProperty("X-Android-Manufacturer", Build.MANUFACTURER);
        }
        if (Build.PRODUCT != null) {
            urlConnection.setRequestProperty("X-Android-Product", Build.PRODUCT);
        }
        if (Build.VERSION.RELEASE != null) {
            urlConnection.setRequestProperty("X-Android-Version", Build.VERSION.RELEASE);
        }
        if (Build.VERSION.SDK_INT != 0) {
            urlConnection.setRequestProperty("X-Android-Sdk", String.valueOf(Build.VERSION.SDK_INT));
        }
    }

    /**
     * Retrieves the HTTP status code associated with the underlying
     * response (if any).
     *
     * @return The status code for the last response if any or a zero
     * invalid value otherwise.
     */
    public int getResponseCode() {
        if (this.urlConnection == null) {
            return 0;
        }

        try {
            return this.urlConnection.getResponseCode();
        } catch (IOException e) {
            return 0;
        }
    }

    public JSONRequestDelegate getDelegate() {
        return this.delegate;
    }

    public void setDelegate(JSONRequestDelegate delegate) {
        this.delegate = delegate;
    }

    public Context getContext() {
        return this.context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<List<String>> getParameters() {
        return this.parameters;
    }

    public void setParameters(List<List<String>> parameters) {
        this.parameters = parameters;
    }

    public String getRequestMethod() {
        return this.requestMethod;
    }

    public void setRequestMethod(String method) {
        this.requestMethod = method;
    }

    public JSONObject getBody() {
        return this.body;
    }

    public void setBody(JSONObject body) {
        this.body = body;
    }

    public long getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(long lastResponse) {
        this.lastResponse = lastResponse;
    }

    public Object getMeta() {
        return this.meta;
    }

    public void setMeta(Object meta) {
        this.meta = meta;
    }
}
