package com.example.bme590.android_dualworldmemory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ToggleButton;
import android.os.Handler;
import android.widget.*;
import android.os.Parcelable;
import android.os.HandlerThread;
import java.lang.Runnable;
import android.graphics.Color;

import java.util.Timer;
import java.util.TimerTask;
import android.os.Handler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.ToggleButton;

import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbAccessory;

import org.w3c.dom.Text;

/*
Android to Arduino:

NewGame = 101

WinnerIdentity: (only passed when gameOver)
    Player A = 35
    Player B = 67

----
Arduino to Android

PlayerChange = 22

ConfirmMessageFromAndroid = 99

*/

public class MainActivity extends AppCompatActivity {

    /** Android/Arduino Communication Variables **/

    // TAG is used to debug in Android logcat console
    private static final String TAG = "ArduinoAccessory";

    private static final String
        ACTION_USB_PERMISSION = "com.example.bme590.android_dualworldmemory.action.USB_PERMISSION";

    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;
    private ToggleButton buttonLED;

    UsbAccessory mAccessory;
    ParcelFileDescriptor mFileDescriptor;
    FileInputStream mInputStream;
    FileOutputStream mOutputStream;

    final Context context = this; //Need self-referential context for dialog alerts

    /** Custom Variables **/
    int[] tileAssignment; //Indices 0-8 corresponding to tile coordinates
    // (left->right, top->bottom), values in array correspond to image identity
    boolean currentPlayer; //true = Player A; false = Player B
    int scoreA, scoreB; //Respective scores for Players A and B
    boolean[] tilesActive; //Tracks which tiles are permanently flipped
    int[] recentlyFlippedTiles; //ID of most recently flipped tiles
    boolean gameOver; //State variable indicating whether game is still in session

    /** Custom Constants **/
    private static final int[] tileArray = new int[]
            {
                    R.id.imageButton1,
                    R.id.imageButton2,
                    R.id.imageButton3,
                    R.id.imageButton4,
                    R.id.imageButton5,
                    R.id.imageButton6,
                    R.id.imageButton7,
                    R.id.imageButton8,
                    R.id.imageButton9
            };   //The IDs of the actual tiles (ImageButtons) themselves

    private static final int[] sourceImage = new int[]
            {
                    R.drawable.image1,
                    R.drawable.image2,
                    R.drawable.image3,
                    R.drawable.image4,
                    R.drawable.image5,
            };   //Holds the 5 different image(IDs) used for tile content

    private static final int PLAYER_A_TITLE = R.id.score_labelA;
    private static final int PLAYER_B_TITLE = R.id.score_labelB;
    private static final int PLAYER_A_SCORE_ID = R.id.score_valueA;
    private static final int PLAYER_B_SCORE_ID = R.id.score_valueB;
    private static final int NEW_GAME_BUTTON_ID = R.id.new_game_button;
    private static final int NEXT_TURN_BUTTON_ID = R.id.next_turn_button;
    private static final int EMPTY_VAL = -343;
    private static final int DEVIL_IMAGE = R.drawable.image5;
    private static final int UNFLIPPED_IMAGE_ID = R.drawable.imageblank;


    /** Android Initialization and Communication Functions **/
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory "
                                + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY); {
                    if (accessory != null && accessory.equals(mAccessory)) {
                        closeAccessory();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) { //TODO: Simply a Bookmark for onCreate method
        super.onCreate(savedInstanceState);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        if (getLastNonConfigurationInstance() != null) {
            mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
            openAccessory(mAccessory);
        }
        setContentView(R.layout.activity_main);

        initializeGame();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("deprecation")
    @Override
    // public Object onRetainNonConfigurationInstance() {
    public Object onRetainCustomNonConfigurationInstance() {
        if (mAccessory != null) {
            return mAccessory;
        } else {
            return super.onRetainNonConfigurationInstance();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mInputStream != null && mOutputStream != null) {
            return;
        }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        closeAccessory();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    private void openAccessory(UsbAccessory mAccessory2) {
        mFileDescriptor = mUsbManager.openAccessory(mAccessory2);
        if (mFileDescriptor != null) {
            mAccessory = mAccessory2;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Log.d(TAG, "accessory opened");
        } else {
            Log.d(TAG, "accessory open fail");
        }
    }


    private void closeAccessory() {
        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    /** BEGIN FUNCTIONS TO MANAGE ANDROID/ARDUINO COMMUNICATION **/

    public String androidRead(){
        if(mInputStream != null){
            byte[] buffer = new byte[1];

            try{
                mInputStream.read(buffer);
            } catch (IOException e){
                Log.e(TAG, "read failed", e);
            }

            TextView debugger = (TextView) findViewById(R.id.debugOut);
            if(buffer[0] > 0){
                int tee = buffer[0];
                String sendOut = "" + tee;
                debugger.setText("wha");
                debugger.setText(sendOut);

                return sendOut;

            }
            return "";
        }
        else {
            //blah
            return "";
        }
    }

    public void androidWrite(String dataOut){
        int i = Integer.parseInt(dataOut);
        byte[] out = new byte[1];
        out[0] = (byte) i;

        if(mOutputStream != null){
            try {
                mOutputStream.write(out, 0, 1);
            }
            catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }


    /** BEGIN FUNCTIONS FOR ANDROID GAME CONTROL **/

    public void initializeGame(){
        TextView playerAScoreText = (TextView) findViewById(PLAYER_A_SCORE_ID);
        TextView playerBScoreText = (TextView) findViewById(PLAYER_B_SCORE_ID);
        TextView playerATitle = (TextView) findViewById(PLAYER_A_TITLE);
        TextView playerBTitle = (TextView) findViewById(PLAYER_B_TITLE);

        initializeTiles();

        shuffleArray(tileAssignment);

        currentPlayer = true;
        gameOver = false;

        scoreA = scoreB = 0;              //Update player scores and respective displays
        playerAScoreText.setText("0");
        playerBScoreText.setText("0");

        playerATitle.setTypeface(null, Typeface.BOLD);   //Set up current player text marker
        playerBTitle.setTypeface(null, Typeface.NORMAL);

        tilesActive = new boolean[9];
        recentlyFlippedTiles = new int[2];
        Arrays.fill(tilesActive, true);
        Arrays.fill(recentlyFlippedTiles, EMPTY_VAL);
    }

    private void initializeTiles() {

        ImageButton currentImg;
        tileAssignment = new int[]{1, 2, 3, 4, 5, 1, 2, 3, 4};

        for (int i = 0; i < tileArray.length; i++) {
            currentImg = (ImageButton) findViewById(tileArray[i]);
            currentImg.setBackgroundResource(R.drawable.imageblank);
        }

    }

    private int[] shuffleArray(int[] array)
    {
        int index, temp;
        Random random = new Random();
        for (int i = array.length - 1; i > 0; i--)
        {
            index = random.nextInt(i + 1);
            temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }

        return array;
    }

    public void monitorTiles(View v){ //TODO
        int tileID = v.getId();
        ImageButton currentTile = (ImageButton) findViewById(tileID);

        int whichImage = tileAssignment[imageIDtoIndex(tileID)]-1;

        if(recentlyFlippedTiles[0] == EMPTY_VAL){ //Current tile is first tile flipped
            recentlyFlippedTiles[0] = tileID;
            currentTile.setBackgroundResource(sourceImage[whichImage]);

        } else if(recentlyFlippedTiles[1] == EMPTY_VAL){ //Current tile is second tile flipped
            recentlyFlippedTiles[1] = tileID;
            currentTile.setBackgroundResource(sourceImage[whichImage]);
        }

        if(howManyTilesFlipped() == 8){
            tilesActive[imageIDtoIndex(recentlyFlippedTiles[0])] = false;
            gameOver = true;
        }

    }

    private int imageIDtoIndex(int id){
        for(int i = 0; i < tileArray.length; i++){
            if(id == tileArray[i]){
                return i;
            }
        }
        return -1;
    }

    public void gameControlButtonSentry(View v){ //TODO: Add player change
        int buttonID = v.getId();

        if(buttonID == NEW_GAME_BUTTON_ID){ //TODO: Add Arduino command to restart game
            initializeGame();

        } else if(buttonID == NEXT_TURN_BUTTON_ID){ //TODO: Add Arduino command for next turn
            tileFlipManagement();
        }

        recentlyFlippedTiles[0] = recentlyFlippedTiles[1] = EMPTY_VAL;

    }

    private void tileFlipManagement(){
        int firstTileFlippedID, secondTileFlippedID;
        ImageButton firstTileFlipped;

        firstTileFlippedID = recentlyFlippedTiles[0];
        secondTileFlippedID = recentlyFlippedTiles[1];

        firstTileFlipped = (ImageButton) findViewById(firstTileFlippedID);

        if(gameOver){
            askReplay();
        }
        else{
            if(recentlyFlippedTiles[1] == EMPTY_VAL){
                firstTileFlipped.setBackgroundResource(UNFLIPPED_IMAGE_ID);
                recentlyFlippedTiles[0] = EMPTY_VAL;
            }
            else{
                checkPair(firstTileFlippedID, secondTileFlippedID);

                if(howManyTilesFlipped() < 8) {
                    playerChange();
                }
            }

        }
    }

    private void playerChange(){
        int playerTitle;
        TextView tv;
        tv = (TextView) findViewById(PLAYER_A_TITLE);
        tv.setTypeface(null, Typeface.NORMAL);
        tv = (TextView) findViewById(PLAYER_B_TITLE);
        tv.setTypeface(null, Typeface.NORMAL);

        currentPlayer = !currentPlayer;

        playerTitle = currentPlayer ? PLAYER_A_TITLE : PLAYER_B_TITLE;
        tv = (TextView) findViewById(playerTitle);
        tv.setTypeface(null, Typeface.BOLD);
    }

    private void checkPair(int tile1_ID, int tile2_ID){
        int content1, content2, whichImage1, whichImage2;
        ImageButton ib1, ib2;

        whichImage1 = tileAssignment[imageIDtoIndex(tile1_ID)]-1;
        whichImage2 = tileAssignment[imageIDtoIndex(tile2_ID)]-1;

        content1 = sourceImage[whichImage1];
        content2 = sourceImage[whichImage2];

        ib1 = (ImageButton) findViewById(tile1_ID);
        ib2 = (ImageButton) findViewById(tile2_ID);

        if(content1 == content2){
            increaseScore();
            tilesActive[imageIDtoIndex(tile1_ID)] = false;
            tilesActive[imageIDtoIndex(tile2_ID)] = false;
        } else{
            ib1.setBackgroundResource(UNFLIPPED_IMAGE_ID);
            ib2.setBackgroundResource(UNFLIPPED_IMAGE_ID);
        }

    }

    public void increaseScore(){
        TextView tv;

        if(currentPlayer){
            scoreA += 20;
            tv = (TextView) findViewById(PLAYER_A_SCORE_ID);
            tv.setText("" + scoreA);
        } else{
            scoreB += 20;
            tv = (TextView) findViewById(PLAYER_B_SCORE_ID);
            tv.setText("" + scoreB);
        }

    }

    public void askReplay() { //This produces a dialog box to start a new game or not.
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if(scoreA > scoreB){
            builder.setTitle(R.string.PA_Win);
        } else if(scoreA < scoreB){
            builder.setTitle(R.string.PB_Win);
        } else{
            builder.setTitle(R.string.Draw);
        }

        builder
                .setMessage(R.string.replay_prompt)
                .setPositiveButton(R.string.Accept, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        initializeGame(); //Restarts the game
                    }
                })
                .setNegativeButton(R.string.Reject, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainActivity.this.finish(); //Exit the app
                    }
                });

        AlertDialog alertDialog = builder.create();

        alertDialog.show();
    }


    private int howManyTilesFlipped() {
        int count = 0;

        for(int i = 0; i < tilesActive.length; ++i) {
            if(!tilesActive[i]){
                count++;
            }
        }

        return count;
    }
}
