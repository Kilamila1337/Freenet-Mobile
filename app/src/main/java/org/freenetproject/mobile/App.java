package org.freenetproject.mobile;

import android.app.Application;
import android.content.Context;

public class App extends Application {
    private static App instance = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    /**
     * @return The application instance, so classes without a context can reach one.
     */
    public static App getInstance() {
        return instance;
    }

    /**
     * @return The application context, or null if the process has not fully started yet.
     */
    public static Context getAppContext() {
        return instance == null ? null : instance.getApplicationContext();
    }
}
