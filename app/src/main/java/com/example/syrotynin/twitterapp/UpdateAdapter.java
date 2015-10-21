package com.example.syrotynin.twitterapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.example.syrotynin.twitterapp.model.StatusData;
import com.squareup.picasso.Picasso;

import java.io.InputStream;
import java.net.URL;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Created by Syrotynin on 17.10.2015.
 */
public class UpdateAdapter extends SimpleCursorAdapter {

    /**twitter developer key*/
    public final static String TWIT_KEY = "jOurmg1tXb7DyCcUOH9z0E4jW";//alter
    /**twitter developer secret*/
    public final static String TWIT_SECRET = "HsqwjNinL2xD1kXax71IESQh5cyem392yeNC03DfoGueDYptqz";//alter

    /**strings representing database column names to map to views*/
    static final String[] from = { "update_text", "user_screen",
            "update_time", "user_img" };
    /**view item IDs for mapping database record values to*/
    static final int[] to = { R.id.updateText, R.id.userScreen,
            R.id.updateTime, R.id.userImg };

    private String LOG_TAG = "UpdateAdapter";

    /**
     * constructor sets up adapter, passing 'from' data and 'to' views
     * @param context
     * @param c
     */
    public UpdateAdapter(Context context, Cursor c) {
        super(context, R.layout.update, c, from, to);
    }

    /*
 * Bind the data to the visible views
 */
    @Override
    public void bindView(View row, Context context, Cursor cursor) {
        super.bindView(row, context, cursor);

        try {
            //get profile image
            ImageView profPic = (ImageView)row.findViewById(R.id.userImg);

            Picasso.with(context)
                    .load(cursor.getString(cursor.getColumnIndex("user_img")))
                    .into(profPic);
        }
        catch(Exception te) {
            Log.e(LOG_TAG, te.getMessage());
        }

        //get the update time
        long createdAt = cursor.getLong(cursor.getColumnIndex("update_time"));
        //get the update time view
        TextView textCreatedAt = (TextView)row.findViewById(R.id.updateTime);
        //adjust the way the time is displayed to make it human-readable
        textCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt) + " ");

        //get the status ID
        long statusID = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
        //get the user name
        String statusName = cursor.getString(cursor.getColumnIndex("user_screen"));
        //create a StatusData object to store these
        StatusData tweetData = new StatusData(statusID, statusName);
        //set the status data object as tag for both retweet and reply buttons in this view
        row.findViewById(R.id.retweet).setTag(tweetData);
        row.findViewById(R.id.reply).setTag(tweetData);
        //setup onclick listeners for the retweet and reply buttons
        row.findViewById(R.id.retweet).setOnClickListener(tweetListener);
        row.findViewById(R.id.reply).setOnClickListener(tweetListener);
        //setup  onclick for the user screen name within the tweet
        row.findViewById(R.id.userScreen).setOnClickListener(tweetListener);
    }

    /**
     * tweetListener handles clicks of reply and retweet buttons
     * - also handles clicking the user name within a tweet
     */
    private View.OnClickListener tweetListener = new View.OnClickListener() {
        //onClick method
        public void onClick(View v) {
            //which view was clicked
            switch(v.getId()) {
                //reply button pressed
                case R.id.reply: {
                    //implement reply

                    //create an intent for sending a new tweet
                    Intent replyIntent = new Intent(v.getContext(), TweetActivity.class);
                    //get the data from the tag within the button view
                    StatusData theData = (StatusData)v.getTag();
                    //pass the status ID
                    replyIntent.putExtra("tweetID", theData.getID());
                    //pass the user name
                    replyIntent.putExtra("tweetUser", theData.getUser());
                    //go to the tweet screen
                    v.getContext().startActivity(replyIntent);

                    break;
                }
                //retweet button pressed
                case R.id.retweet: {
                    //implement retweet
                    new RetweetTask(v.getContext(), (StatusData)v.getTag()).execute();
                    break;
                }
                //user has pressed tweet user name
                case R.id.userScreen:
                    //implement visiting user profile

                    //get the user screen name
                    TextView tv = (TextView)v.findViewById(R.id.userScreen);
                    String userScreenName = tv.getText().toString();
                    //open the user's profile page in the browser
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://twitter.com/" + userScreenName));
                    v.getContext().startActivity(browserIntent);

                    break;
                default:
                    break;
            }
        }
    };

    private class RetweetTask extends AsyncTask<Void, Void, Boolean>
    {
        private Context appContext;
        private StatusData data;


        public RetweetTask(Context appContext, StatusData data){
            this.appContext = appContext;
            this.data = data;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Boolean result = false;

            //get context
            //Context appCont = v.getContext();
            //get preferences for user access
            SharedPreferences tweetPrefs = appContext.getSharedPreferences(MainActivity.TWIT_PREFS, 0);
            String userToken = tweetPrefs.getString(MainActivity.USER_TOKEN, null);
            String userSecret = tweetPrefs.getString(MainActivity.USER_SECRET, null);

            //create new Twitter configuration
            Configuration twitConf = new ConfigurationBuilder()
                    .setOAuthConsumerKey(TWIT_KEY)
                    .setOAuthConsumerSecret(TWIT_SECRET)
                    .setOAuthAccessToken(userToken)
                    .setOAuthAccessTokenSecret(userSecret)
                    .build();

            //create Twitter instance for retweeting
            Twitter retweetTwitter = new TwitterFactory(twitConf).getInstance();

            try
            {
                //retweet, passing the status ID from the tag
                retweetTwitter.retweetStatus(data.getID());
                result = true;
            }
            catch(TwitterException te) {
                Log.e(LOG_TAG, te.getMessage());
            }

            return result;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            CharSequence text = "";
            if(result.booleanValue())
            {
                text = "Retweeted!";
            }
            else{
                text = "Retweet failed!";
            }

            Toast toast = Toast.makeText(appContext, text, Toast.LENGTH_SHORT);
            toast.show();
        }
    }
}
