package com.example.syrotynin.twitterapp;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.example.syrotynin.twitterapp.model.DataManager;

import java.util.List;

import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Created by Syrotynin on 17.10.2015.
 */
public class TimelineService extends Service {
    /**developer account key for this app*/
    public final static String TWIT_KEY = "jOurmg1tXb7DyCcUOH9z0E4jW";
    /**developer secret for the app*/
    public final static String TWIT_SECRET = "HsqwjNinL2xD1kXax71IESQh5cyem392yeNC03DfoGueDYptqz";
    /**twitter object*/
    private Twitter timelineTwitter;

    /**database helper object*/
    private DataManager niceHelper;
    /**timeline database*/
    private SQLiteDatabase niceDB;

    /**shared preferences for user details*/
    private SharedPreferences nicePrefs;
    /**handler for updater*/
    private Handler niceHandler;
    /**delay between fetching new tweets*/
    private static int mins = 1;//alter to suit
    private static final long FETCH_DELAY = mins * (20*1000);  // 20 seconds delay
    //debugging tag
    private String LOG_TAG = "TimelineService";

    @Override
    public void onCreate() {
        super.onCreate();
        //setup the class
        //get prefs
        nicePrefs = getSharedPreferences(MainActivity.TWIT_PREFS, 0);
        //get database helper
        niceHelper = new DataManager(this);
        //get the database
        niceDB = niceHelper.getWritableDatabase();

        //get user preferences
        String userToken = nicePrefs.getString(MainActivity.USER_TOKEN, null);
        String userSecret = nicePrefs.getString(MainActivity.USER_SECRET, null);

        //create new configuration
        Configuration twitConf = new ConfigurationBuilder()
                .setOAuthConsumerKey(TWIT_KEY)
                .setOAuthConsumerSecret(TWIT_SECRET)
                .setOAuthAccessToken(userToken)
                .setOAuthAccessTokenSecret(userSecret)
                .build();
        //instantiate new twitter
        timelineTwitter = new TwitterFactory(twitConf).getInstance();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStart(intent, startId);
        //get handler
        niceHandler = new Handler();
        //add to run queue
        startUpdatingTweets();
        //return sticky
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //stop the updating
        stopRepeatingTask();
        niceDB.close();
    }

    void startUpdatingTweets()
    {
        mHandlerTask.run();
    }

    void stopRepeatingTask()
    {
        niceHandler.removeCallbacks(mHandlerTask);
    }

    Runnable mHandlerTask = new Runnable()
    {
        @Override
        public void run() {
            new TwitterRetriveTask().execute();
            niceHandler.postDelayed(mHandlerTask, FETCH_DELAY);
        }
    };

    private class TwitterRetriveTask extends AsyncTask<Void, Void, Boolean>
    {
        @Override
        protected Boolean doInBackground(Void... aVoid) {
            //check for updates - assume none
            boolean statusChanges = false;

            try {
                //fetch timeline
                //retrieve the new home timeline tweets as a list
                List<twitter4j.Status> homeTimeline = timelineTwitter.getHomeTimeline();
                //iterate through new status updates
                for (twitter4j.Status statusUpdate : homeTimeline)
                {
                    //call the getValues method of the data helper class, passing the new updates
                    ContentValues timelineValues = DataManager.getValues(statusUpdate);
                    //if the database already contains the updates they will not be inserted
                    niceDB.insertOrThrow("home", null, timelineValues);
                    //confirm we have new updates
                    statusChanges = true;
                }
            }
            catch (Exception te) {
                Log.e(LOG_TAG, "Exception: " + te);
            }

            return statusChanges;
        }

        @Override
        protected void onPostExecute(Boolean statusChanged) {
            //if we have new updates, send a Broadcast
            if (statusChanged)
            {
                //this should be received in the main timeline class
                sendBroadcast(new Intent("TWITTER_UPDATES"));
            }
        }
    }
}
