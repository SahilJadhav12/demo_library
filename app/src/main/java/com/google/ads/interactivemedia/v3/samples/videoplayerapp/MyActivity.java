package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import static androidx.media3.common.C.CONTENT_TYPE_HLS;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.multidex.MultiDex;
import com.google.ads.interactivemedia.v3.api.AdEvent;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import android.widget.Toast;

/** Main Activity. */
@OptIn(markerClass = UnstableApi.class)
public class MyActivity extends Activity {

  public static final String KEY_ADS_LOADER_STATE = "ads_loader_state";
  public static final String SAMPLE_ASSET_KEY = "0Zhrslv7S0qgwRWRV3ZV8g";
  public static final String LOG_TAG = "ImaExoPlayerExample";

  public PlayerView playerView;
  public TextView logText;
  public ExoPlayer player;
  public ImaServerSideAdInsertionMediaSource.AdsLoader adsLoader;
  public ImaServerSideAdInsertionMediaSource.AdsLoader.State adsLoaderState;
  public Timer timer;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_my);
    MultiDex.install(this);

    playerView = findViewById(R.id.player_view);

    // Checks if there is a saved AdsLoader state to be used later when initiating the AdsLoader.
    if (savedInstanceState != null) {
      Bundle adsLoaderStateBundle = savedInstanceState.getBundle(KEY_ADS_LOADER_STATE);
      if (adsLoaderStateBundle != null) {
        adsLoaderState =
            ImaServerSideAdInsertionMediaSource.AdsLoader.State.CREATOR.fromBundle(
                adsLoaderStateBundle);
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    // Attempts to save the AdsLoader state to handle app backgrounding.
    if (adsLoaderState != null) {
      outState.putBundle(KEY_ADS_LOADER_STATE, adsLoaderState.toBundle());
    }
  }

  public void releasePlayer() {
    // Set the player references to null and release the player's resources.
    playerView.setPlayer(null);
    player.release();
    player = null;

    // Release the adsLoader state so that it can be initiated again.
    adsLoaderState = adsLoader.release();
  }

  // Create a server side ad insertion (SSAI) AdsLoader.
  public ImaServerSideAdInsertionMediaSource.AdsLoader createAdsLoader() {
    ImaServerSideAdInsertionMediaSource.AdsLoader.Builder adsLoaderBuilder =
        new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(this, playerView);

    // Attempts to set the AdsLoader state if available from a previous session.
    if (adsLoaderState != null) {
      adsLoaderBuilder.setAdsLoaderState(adsLoaderState);
    }

    return adsLoaderBuilder.setAdEventListener(buildAdEventListener()).build();
  }

  public AdEvent.AdEventListener buildAdEventListener() {
    logText = findViewById(R.id.logText);
    logText.setMovementMethod(new ScrollingMovementMethod());

    AdEvent.AdEventListener imaAdEventListener =
        event -> {
          AdEvent.AdEventType eventType = event.getType();
          if (eventType == AdEvent.AdEventType.AD_PROGRESS) {
            return;
          }
          String log = "IMA event: " + eventType;
          if (logText != null) {
            logText.append(log + "\n");
          }
          Log.i(LOG_TAG, log);
        };

    return imaAdEventListener;
  }

  public void initializePlayer() {
    adsLoader = createAdsLoader();

    // Set up the factory for media sources, passing the ads loader.
    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

    DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

    // MediaSource.Factory to create the ad sources for the current player.
    ImaServerSideAdInsertionMediaSource.Factory adsMediaSourceFactory =
        new ImaServerSideAdInsertionMediaSource.Factory(adsLoader, mediaSourceFactory);

    // 'mediaSourceFactory' is an ExoPlayer component for the DefaultMediaSourceFactory.
    // 'adsMediaSourceFactory' is an ExoPlayer component for a MediaSource factory for IMA server
    // side inserted ad streams.
    mediaSourceFactory.setServerSideAdInsertionMediaSourceFactory(adsMediaSourceFactory);

    // Create a SimpleExoPlayer and set it as the player for content and ads.
    player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
    playerView.setPlayer(player);
    adsLoader.setPlayer(player);

    // Build an IMA SSAI media item to prepare the player with.
    Uri ssaiLiveUri =
        new ImaServerSideAdInsertionUriBuilder()
            .setAssetKey(SAMPLE_ASSET_KEY)
            .setFormat(CONTENT_TYPE_HLS) // Use CONTENT_TYPE_DASH for dash streams.
            .build();

    // Create the MediaItem to play, specifying the stream URI.
    MediaItem ssaiMediaItem = MediaItem.fromUri(ssaiLiveUri);

    // Prepare the content and ad to be played with the ExoPlayer.
    player.setMediaItem(ssaiMediaItem);
    player.prepare();

    // Set PlayWhenReady. If true, content and ads will autoplay.
    player.setPlayWhenReady(false);
  }

  public void trackingAPI(String devicesID,String seasonsID,String assetKeys,String appName,String contentProvider,String showsID,String episodesID) {
    // Call the method every second for the first 10 seconds
    timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
      int tickCount = 1;

      @Override
      public void run() {
        if (tickCount <= 10) {
          // Call your method here
          Log.d("TrackingAPI", "timer 10 sec --- >>>> " + tickCount);
          String randomNumber = String.valueOf(Calendar.getInstance().getTimeInMillis());
          String url = "https://i.jsrdn.com/i/1.gif?" +
                  "r=" + randomNumber +/// A random number
                  "&e=vs" + tickCount +/// vplay, ff, vs1, vs2, etc.
                  "&u=" + devicesID +/// Device specific ID
                  "&i=" + seasonsID +/// dai_session_id
                  "&v=" + seasonsID +/// dai_asset_key
                  "&f=" + assetKeys +
                  "&m=" + appName + /// App Name
                  "&p=" + contentProvider +/// The "content_provider" value from the feed
                  "&show=" + showsID +///The show’s "id" element from the feed
                  "&ep=" + episodesID;

          trackingPixel(url);
          tickCount++;
        } else {
          // After 10 seconds, cancel the current timer
          timer.cancel();

          // Start a new timer to call the method every 60 seconds
          timer = new Timer();
          timer.scheduleAtFixedRate(new TimerTask() {
            int minuteCount = 0;

            @Override
            public void run() {
              // Call your method here
              if(minuteCount != 0){
                Log.d("TrackingAPI", "after timer " + minuteCount + " min --- >>>> " + (minuteCount ) * 60);
                String randomNumber = String.valueOf(Calendar.getInstance().getTimeInMillis());
                String url = "https://i.jsrdn.com/i/1.gif?" +
                        "r=" + randomNumber +/// A random number
                        "&e=vs" + (minuteCount * 60) +/// vplay, ff, vs1, vs2, etc.
                        "&u=" + devicesID +/// Device specific ID
                        "&i=" + seasonsID +/// dai_session_id
                        "&v=" + seasonsID +/// dai_asset_key
                        "&f=" + assetKeys +
                        "&m=" + appName + /// App Name
                        "&p=" + contentProvider +/// The "content_provider" value from the feed
                        "&show=" + showsID +///The show’s "id" element from the feed
                        "&ep=" + episodesID;
                trackingPixel(url);
              }
              minuteCount++;
            }
          }, 0, 60 * 1000); // 60 seconds interval
        }
      }
    }, 0, 1000); // 1-second interval
  }

  public void trackingPixel(String url) {
    // RequestQueue initialized
    Log.d("trackingPixel", "Call URl --- >>>> " + url);

    RequestQueue mRequestQueue = Volley.newRequestQueue(this);

    // String Request initialized
    StringRequest mStringRequest = new StringRequest(Request.Method.GET, url,
            new Response.Listener<String>() {
              public void onResponse(String response) {
                Log.d("trackingPixel",response.toString());
              }
            }, new Response.ErrorListener() {
      public void onErrorResponse(VolleyError error) {
        Log.d("trackingPixel onError","Error get here");
      }
    });
    Log.d("trackingPixel API CALL","mRequestQueue");
    mRequestQueue.add(mStringRequest);
  }
}
