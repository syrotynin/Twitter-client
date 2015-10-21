package com.example.syrotynin.twitterapp.model;

/**
 * Created by Syrotynin on 18.10.2015.
 */
public class StatusData {
    /**tweet ID*/
    private long tweetID;
    /**user screen name of tweeter*/
    private String tweetUser;

    /**
     * Constructor receives ID and user name
     * @param tweetID
     * @param tweetUser
     */
    public StatusData(long tweetID, String tweetUser) {
        //instantiate variables
        this.tweetID = tweetID;
        this.tweetUser = tweetUser;
    }

    /**
     * Get the ID of the tweet
     * @return tweetID as a long
     */
    public long getID() {
        return tweetID;
    }

    /**
     * Get the user screen name for the tweet
     * @return tweetUser as a String
     */
    public String getUser() {
        return tweetUser;
    }
}
