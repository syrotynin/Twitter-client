package com.example.syrotynin.twitterapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.example.syrotynin.twitterapp.model.DataManager;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

/**
 * Created by Syrotynin on 16.10.2015.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    /**developer account key for this app*/
    public final static String TWIT_KEY = "jOurmg1tXb7DyCcUOH9z0E4jW";
    /**developer secret for the app*/
    public final static String TWIT_SECRET = "HsqwjNinL2xD1kXax71IESQh5cyem392yeNC03DfoGueDYptqz";
    /**app url*/
    public final static String TWIT_URL = "sergey-android:///";

    /**Twitter instance*/
    private Twitter niceTwitter;
    /**request token for accessing user account*/
    private RequestToken niceRequestToken;
    /**shared preferences to store user details*/
    private SharedPreferences nicePrefs;

    //for error logging
    private String LOG_TAG = "Twitter App";//alter for your Activity name

    public final static String TWIT_PREFS = "TwitPrefs";
    public final static String USER_TOKEN = "user_token";
    public final static String USER_SECRET = "user_secret";
    public final static String OAUTH_VERIFIER = "oauth_verifier";

    //async request types
    private final static int APP_AUTH_TOKEN_TYPE = 0;
    private final static int USER_ACCESS_TOKEN_TYPE = 1;

    private Uri twitURI;

    /**main view for the home timeline*/
    private ListView homeTimeline;
    /**database helper for update data*/
    private DataManager timelineHelper;
    /**update database*/
    private SQLiteDatabase timelineDB;
    /**cursor for handling data*/
    private Cursor timelineCursor;
    /**adapter for mapping data*/
    private UpdateAdapter timelineAdapter;
    /**Broadcast receiver for when new updates are available*/
    private BroadcastReceiver statusReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //get the preferences for the app
        nicePrefs = getSharedPreferences(TWIT_PREFS, 0);

        //find out if the user preferences are set
        if(nicePrefs.getString(USER_TOKEN, null)==null) {

            //no user preferences so prompt to sign in
            setContentView(R.layout.main);

            //get a twitter instance for authentication
            niceTwitter = new TwitterFactory().getInstance();
            //pass developer key and secret
            niceTwitter.setOAuthConsumer(TWIT_KEY, TWIT_SECRET);
            //try to get request token
            new TwitterRequestTask().execute(APP_AUTH_TOKEN_TYPE);

            //setup button for click listener
            Button signIn = (Button)findViewById(R.id.signin);
            signIn.setOnClickListener(this);
        }
        else
        {
            //user preferences are set - get timeline
            setupTimeline();
        }
    }

    @Override
    public void onClick(View v) {
        //find view
        switch(v.getId()) {
            //sign in button pressed
            case R.id.signin:
                //take user to twitter authentication web page to allow app access to their twitter account
                String authURL = niceRequestToken.getAuthenticationURL();
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authURL)));
                break;

            //user has pressed tweet button
            case R.id.tweetbtn:
                //launch tweet activity
                startActivity(new Intent(this, TweetActivity.class));
                break;

            default:
                break;
        }
    }

    /*
 * onNewIntent fires when user returns from Twitter authentication Web page
 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //get the retrieved data
        twitURI = intent.getData();

        new TwitterRequestTask().execute(USER_ACCESS_TOKEN_TYPE);
        setupTimeline();
    }

    private void setupTimeline() {
        Log.v(LOG_TAG, "setting up timeline");
        setContentView(R.layout.timeline);

        //setup onclick listener for tweet button
        LinearLayout tweetClicker = (LinearLayout)findViewById(R.id.tweetbtn);
        tweetClicker.setOnClickListener(this);

        try {

            //get the timeline

            //get reference to the list view
            homeTimeline = (ListView)findViewById(R.id.homeList);

            //instantiate database helper
            timelineHelper = new DataManager(this);

            //get the database
            timelineDB = timelineHelper.getReadableDatabase();

            //query the database, most recent tweets first
            timelineCursor = timelineDB.query
                    ("home", null, null, null, null, null, "update_time DESC");
            //manage the updates using a cursor
            startManagingCursor(timelineCursor);
            //instantiate adapter
            timelineAdapter = new UpdateAdapter(this, timelineCursor);

            //this will make the app populate the new update data in the timeline view
            homeTimeline.setAdapter(timelineAdapter);

            //instantiate receiver class for finding out when new updates are available
            statusReceiver = new TwitterUpdateReceiver();
            //register for updates
            registerReceiver(statusReceiver, new IntentFilter("TWITTER_UPDATES"));
            //start the Service for updates now
            this.getApplicationContext().startService(new Intent(this.getApplicationContext(), TimelineService.class));
        }
        catch(Exception te) {
            Log.e(LOG_TAG, "Failed to fetch timeline: " + te.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            //stop the updater Service
            stopService(new Intent(this, TimelineService.class));
            //remove receiver register
            unregisterReceiver(statusReceiver);
            //close the database
            timelineDB.close();
        }
        catch(Exception se) { Log.e(LOG_TAG, "unable to stop Service or receiver"); }
    }

    private class TwitterRequestTask extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... params) {

            int i = params[0];
            switch (i){
                case APP_AUTH_TOKEN_TYPE:
                    getAuthRequestToken();
                    break;

                case USER_ACCESS_TOKEN_TYPE:
                    getUserAccessToken();
                    break;

                default:break;
            }


            return null;
        }

        private void getAuthRequestToken(){
            try
            {
                //get authentication request token
                niceRequestToken = niceTwitter.getOAuthRequestToken(TWIT_URL);
            }
            catch(TwitterException te) {
                Log.e(LOG_TAG, "TE " + te.getMessage());
            }
        }

        private void getUserAccessToken(){
            if(twitURI!=null && twitURI.toString().startsWith(TWIT_URL))
            {
                //is verifcation - get the returned data
                String oaVerifier = twitURI.getQueryParameter(OAUTH_VERIFIER);

                //attempt to retrieve access token
                try
                {
                    //try to get an access token using the returned data from the verification page
                    AccessToken accToken = niceTwitter.getOAuthAccessToken(niceRequestToken, oaVerifier);

                    //add the token and secret to shared prefs for future reference
                    nicePrefs.edit()
                            .putString(USER_TOKEN, accToken.getToken())
                            .putString(USER_SECRET, accToken.getTokenSecret())
                            .commit();
                }
                catch (TwitterException te) {
                    Log.e(LOG_TAG, "Failed to get access token: " + te.getMessage());
                }

            }
        }
    }

    /**
     * Class to implement Broadcast receipt for new updates
     */
    class TwitterUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int rowLimit = 100;
            if(DatabaseUtils.queryNumEntries(timelineDB, "home")>rowLimit) {
                String deleteQuery = "DELETE FROM home WHERE "+BaseColumns._ID+" NOT IN " +
                        "(SELECT "+ BaseColumns._ID+" FROM home ORDER BY "+"update_time DESC " +
                        "limit "+rowLimit+")";
                timelineDB.execSQL(deleteQuery);
            }

            timelineCursor = timelineDB.query("home", null, null, null, null, null, "update_time DESC");
            startManagingCursor(timelineCursor);
            timelineAdapter = new UpdateAdapter(context, timelineCursor);
            homeTimeline.setAdapter(timelineAdapter);
        }
    }

}
