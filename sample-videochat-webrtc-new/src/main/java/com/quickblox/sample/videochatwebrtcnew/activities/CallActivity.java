package com.quickblox.sample.videochatwebrtcnew.activities;


import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.quickblox.sample.videochatwebrtcnew.R;
import com.quickblox.sample.videochatwebrtcnew.SessionManager;
import com.quickblox.sample.videochatwebrtcnew.definitions.Consts;
import com.quickblox.sample.videochatwebrtcnew.fragments.AudioConversationFragment;
import com.quickblox.sample.videochatwebrtcnew.fragments.BaseConversationFragment;
import com.quickblox.sample.videochatwebrtcnew.fragments.IncomeCallFragment;
import com.quickblox.sample.videochatwebrtcnew.fragments.VideoConversationFragment;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.QBRTCClient;
import com.quickblox.videochat.webrtc.QBRTCConfig;
import com.quickblox.videochat.webrtc.QBRTCException;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientConnectionCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientSessionCallbacks;

import org.webrtc.VideoCapturerAndroid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by tereha on 16.02.15.
 *
 */
public class CallActivity extends BaseLogginedUserActivity implements QBRTCClientSessionCallbacks, QBRTCClientConnectionCallbacks {


    private static final String TAG = CallActivity.class.getSimpleName();
    private static final String ADD_OPPONENTS_FRAGMENT_HANDLER = "opponentHandlerTask";
    private static final long TIME_BEGORE_CLOSE_CONVERSATION_FRAGMENT = 3;
    private static final String INCOME_WINDOW_SHOW_TASK_THREAD = "INCOME_WINDOW_SHOW";
    public static final String OPPONENTS_CALL_FRAGMENT = "opponents_call_fragment";
    public static final String INCOME_CALL_FRAGMENT = "income_call_fragment";
    public static final String CONVERSATION_CALL_FRAGMENT = "conversation_call_fragment";
    public static final String CALLER_NAME = "caller_name";
    public static final String SESSION_ID = "sessionID";
    public static final String START_CONVERSATION_REASON = "start_conversation_reason";

    public static ArrayList<QBUser> qbOpponentsList;
    private Runnable showIncomingCallWindowTask;
    private Handler showIncomingCallWindowTaskHandler;
    private BroadcastReceiver wifiStateReceiver;
    private boolean closeByWifiStateAllow = true;
    private String hangUpReason;
    private boolean isInCommingCall;
//    private boolean isLastConnectionStateEnabled;
    private boolean isInFront;
    private Consts.CALL_DIRECTION_TYPE call_direction_type;
    private QBRTCTypes.QBConferenceType call_type;
    private List<Integer> opponentsList;
    private Map<String, String> userInfo;
    int counterCallbaks;
    private MediaPlayer ringtone;

    public static void start(Context context, QBRTCTypes.QBConferenceType qbConferenceType,
                             List<Integer> opponentsIds, Map<String, String> userInfo,
                             Consts.CALL_DIRECTION_TYPE callDirectionType){
        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(Consts.CALL_DIRECTION_TYPE_EXTRAS, callDirectionType);
        intent.putExtra(Consts.CALL_TYPE_EXTRAS, qbConferenceType);
        intent.putExtra(Consts.USER_INFO_EXTRAS, (Serializable) userInfo);
        intent.putExtra(Consts.OPPONENTS_LIST_EXTRAS, (Serializable) opponentsIds);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initQBRTCConnectionListener();
        initWiFiManagerListener();

        if (getIntent().getExtras() != null) {
            parseIntentExtras(getIntent().getExtras());
        }
        Log.d(TAG, "onCreate()" + call_direction_type);
        if (call_direction_type == Consts.CALL_DIRECTION_TYPE.INCOMING){
            isInCommingCall = true;
            Log.d(TAG, "onCreate()" + call_direction_type);
            addIncomeCallFragment(SessionManager.getCurrentSession());

        } else if (call_direction_type == Consts.CALL_DIRECTION_TYPE.OUTGOING){
            isInCommingCall = false;
            Log.d(TAG, "onCreate()" + call_direction_type);
            addConversationFragment(opponentsList, call_type, call_direction_type);
        }

        Log.d(TAG, "Activity. Thread id: " + Thread.currentThread().getId());
    }

    private void parseIntentExtras(Bundle extras) {
        call_direction_type = (Consts.CALL_DIRECTION_TYPE) extras.getSerializable(
                Consts.CALL_DIRECTION_TYPE_EXTRAS);
        call_type = (QBRTCTypes.QBConferenceType) extras.getSerializable(Consts.CALL_TYPE_EXTRAS);
        opponentsList = (List<Integer>) extras.getSerializable(Consts.OPPONENTS_LIST_EXTRAS);
        userInfo = (Map<String, String>) extras.getSerializable(Consts.USER_INFO_EXTRAS);
    }

    private void initQBRTCConnectionListener() {

        QBRTCClient.getInstance().setCameraErrorHendler(new VideoCapturerAndroid.CameraErrorHandler() {
            @Override
            public void onCameraError(final String s) {
                CallActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CallActivity.this, s, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        // Add activity as callback to RTCClient
        QBRTCClient.getInstance().addSessionCallbacksListener(this);
        QBRTCClient.getInstance().addConnectionCallbacksListener(this);
    }

    private void initWiFiManagerListener() {
        wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "WIFI was changed");
                processCurrentWifiState(context);
            }
        };
    }

    protected void processCurrentWifiState(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wifi.isWifiEnabled()) {
//            isLastConnectionStateEnabled = false;
            Log.d(TAG, "WIFI is turned off");
            if (closeByWifiStateAllow) {
                if (SessionManager.getCurrentSession() != null) {
                    Log.d(TAG, "currentSession NOT null");
                    // Close session safely
                    disableConversationFragmentButtons();
//                    stopConversationFragmentBeeps();
                    stopOutBeep();

                    hangUpCurrentSession();

                    hangUpReason = Consts.WIFI_DISABLED;
                } else {
                    Log.d(TAG, "Call finish() on activity");
                    finish();
                }
            } else {
                Log.d(TAG, "WIFI is turned on");
                showToast(R.string.NETWORK_ABSENT);
            }
        } else {
//            isLastConnectionStateEnabled = true;
        }
    }

    private void disableConversationFragmentButtons() {
//        BaseConversationFragment fragment = (QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO.equals(
//                call_type)) ? new VideoConversationFragment() : new AudioConversationFragment();
        BaseConversationFragment fragment = (BaseConversationFragment) getFragmentManager().findFragmentByTag(CONVERSATION_CALL_FRAGMENT);
        if (fragment != null) {
            fragment.actionButtonsEnabled(false);
        }
    }

    private BaseConversationFragment createConnversationFragment(QBRTCTypes.QBConferenceType call_type){
        BaseConversationFragment fragment = (QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO.equals(
                call_type)) ? new VideoConversationFragment() : new AudioConversationFragment();
        return fragment;
    }

    public Fragment getCurrentFragmentByTag(String tag){

        if (tag.equals(Consts.INCOME_CALL_FRAGMENT)){
            return getFragmentManager().findFragmentByTag(tag);
        } else if (tag.equals(Consts.CONVERSATION_CALL_FRAGMENT)){
            return getFragmentManager().findFragmentByTag(tag);
        } else {
            return null;
        }
    }


    private void initIncommingCallTask() {
        showIncomingCallWindowTaskHandler = new Handler(Looper.myLooper());
        showIncomingCallWindowTask = new Runnable() {
            @Override
            public void run() {
                IncomeCallFragment incomeCallFragment = (IncomeCallFragment) getFragmentManager().findFragmentByTag(INCOME_CALL_FRAGMENT);
                if (incomeCallFragment == null) {
                    BaseConversationFragment conversationFragment = (BaseConversationFragment) getFragmentManager().findFragmentByTag(CONVERSATION_CALL_FRAGMENT);
                    if (conversationFragment != null) {
                        disableConversationFragmentButtons();
                        stopOutBeep();
                        hangUpCurrentSession();
                    }
                } else {
                    rejectCurrentSession();
                }
                Toast.makeText(CallActivity.this, "Call was stopped by timer", Toast.LENGTH_LONG).show();
            }
        };
    }

    public void rejectCurrentSession() {
        if (SessionManager.getCurrentSession() != null) {
            SessionManager.getCurrentSession().rejectCall(new HashMap<String, String>());
        }
        finish();
    }

    public void hangUpCurrentSession() {
        if (SessionManager.getCurrentSession() != null) {
            SessionManager.getCurrentSession().hangUp(new HashMap<String, String>());
        }
        finish();
    }

    private void startIncomeCallTimer() {
        showIncomingCallWindowTaskHandler.postAtTime(showIncomingCallWindowTask, SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(QBRTCConfig.getAnswerTimeInterval()));
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopIncomeCallTimer() {
        Log.d(TAG, "stopIncomeCallTimer");
        showIncomingCallWindowTaskHandler.removeCallbacks(showIncomingCallWindowTask);
    }


    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(wifiStateReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopOutBeep();
        unregisterReceiver(wifiStateReceiver);
    }

    private void forbidenCloseByWifiState() {
        closeByWifiStateAllow = false;
    }


    // ---------------Chat callback methods implementation  ----------------------//

    @Override
    public void onReceiveNewSession(final QBRTCSession session) {

    }

    @Override
    public void onUserNotAnswer(QBRTCSession session, Integer userID) {
        showToast(R.string.noAnswer);
        finish();
        Log.d(TAG, "Stop new session. Device now is busy");
    }

    @Override
    public void onStartConnectToUser(QBRTCSession session, Integer userID) {
        showToast(R.string.checking);
        stopOutBeep();
    }

    @Override
    public void onCallRejectByUser(QBRTCSession session, Integer userID, Map<String, String> userInfo) {
        showToast(R.string.rejected);
        stopOutBeep();
        finish();
    }

    @Override
    public void onConnectionClosedForUser(QBRTCSession session, Integer userID) {
        showToast(R.string.closed);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Close app after session close of network was disabled
                Log.d(TAG, "onConnectionClosedForUser()");
                if (hangUpReason != null && hangUpReason.equals(Consts.WIFI_DISABLED)) {
                    Intent returnIntent = new Intent();
                    setResult(Consts.CALL_ACTIVITY_CLOSE_WIFI_DISABLED, returnIntent);
                    finish();
                }
            }
        });
        Log.d(TAG, "onConnectionClosedForUser " + counterCallbaks++);
    }

    @Override
    public void onConnectedToUser(QBRTCSession session, final Integer userID) {
        forbidenCloseByWifiState();

        Log.d(TAG, "onStartConnectToUser");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isInCommingCall) {
                    stopIncomeCallTimer();
                }
                showToast(R.string.connected);

                startTimer();

//                showToast(R.string.connected);

                Log.d(TAG, "onConnectedToUser() is started");

                BaseConversationFragment fragment = (BaseConversationFragment) getFragmentManager().findFragmentByTag(CONVERSATION_CALL_FRAGMENT);
                if (fragment != null) {
                    fragment.actionButtonsEnabled(true);
                }
            }
        });
    }


    @Override
    public void onDisconnectedTimeoutFromUser(QBRTCSession session, Integer userID) {
//        setStateTitle(userID, R.string.time_out, View.INVISIBLE);
//        if (isLastConnectionStateEnabled) {
//            showToast(R.string.NETWORK_ABSENT);
//        } else {
            showToast(R.string.time_out);
//        }
    }

    @Override
    public void onConnectionFailedWithUser(QBRTCSession session, Integer userID) {
//        setStateTitle(userID, R.string.failed, View.INVISIBLE);
        showToast(R.string.failed);
    }

    @Override
    public void onError(QBRTCSession qbrtcSession, QBRTCException e) {
//        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSessionClosed(final QBRTCSession session) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Log.d(TAG, "Session " + session.getSessionID() + " start stop session");
                String curSession = (SessionManager.getCurrentSession() == null) ? null : SessionManager.getCurrentSession().getSessionID();
                Log.d(TAG, "Session " + curSession + " is current" );

                if (session.equals(SessionManager.getCurrentSession())) {

                    if (isInCommingCall) {
                        stopIncomeCallTimer();
                    }

                    Log.d(TAG, "Stop session");
                     finish();

                    // Remove current session
                    Log.d(TAG, "Remove current session");
                    Log.d("Crash", "onSessionClosed. Set session to null");
                    SessionManager.setCurrentSession(null);

                    stopTimer();
                    closeByWifiStateAllow = true;
                    processCurrentWifiState(CallActivity.this);
                }
            }
        });
    }

    @Override
    public void onSessionStartClose(final QBRTCSession session) {
        stopOutBeep();
        Log.d(TAG, "Start stopping session");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BaseConversationFragment fragment = (BaseConversationFragment) getFragmentManager().findFragmentByTag(CONVERSATION_CALL_FRAGMENT);
                if (fragment != null && session.equals(SessionManager.getCurrentSession())) {
                    fragment.actionButtonsEnabled(false);
                }
            }
        });
    }

    @Override
    public void onDisconnectedFromUser(QBRTCSession session, Integer userID) {
//        setStateTitle(userID, R.string.disconnected, View.INVISIBLE);
        showToast(R.string.disconnected);
    }

    private void showToast(final int message) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
                Toast.makeText(getApplicationContext(), getString(message), Toast.LENGTH_LONG).show();
//            }
//        });
    }

//    private void setStateTitle(final Integer userID, final int stringID, final int progressBarVisibility) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                View opponentItemView = findViewById(userID);
//                if (opponentItemView != null) {
//                    TextView connectionStatus = (TextView) opponentItemView.findViewById(R.id.connectionStatus);
//                    connectionStatus.setText(getString(stringID));
//
//                    ProgressBar connectionStatusPB = (ProgressBar) opponentItemView.findViewById(R.id.connectionStatusPB);
//                    connectionStatusPB.setVisibility(progressBarVisibility);
//                    Log.d(TAG, "Opponent state changed to " + getString(stringID));
//                }
//            }
//        });
//    }

    @Override
    public void onReceiveHangUpFromUser(QBRTCSession session, final Integer userID) {
        if (session.equals(SessionManager.getCurrentSession())) {

            // TODO update view of this user
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                setStateTitle(userID, R.string.hungUp, View.INVISIBLE);
                    showToast(R.string.hungUp);
                }
            });
            finish();
        }
    }

    private void addIncomeCallFragment(QBRTCSession session) {

        Log.d(TAG, "QBRTCSession in addIncomeCallFragment is " + session);
        Log.d(TAG, "isInFront = " + isInFront);
        if(session != null) {
            initIncommingCallTask();
            startIncomeCallTimer();

            Fragment fragment = new IncomeCallFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, INCOME_CALL_FRAGMENT).commit();
        } else {
            Log.d(TAG, "SKIP addIncomeCallFragment method");
        }
    }

    public void addConversationFragment (List<Integer> opponents,
                                          QBRTCTypes.QBConferenceType qbConferenceType,
                                          Consts.CALL_DIRECTION_TYPE callDirectionType){

        if (SessionManager.getCurrentSession() == null && callDirectionType == Consts.CALL_DIRECTION_TYPE.OUTGOING){
            startOutBeep();
            try {
                QBRTCSession newSessionWithOpponents = QBRTCClient.getInstance().createNewSessionWithOpponents(opponents, qbConferenceType);
                Log.d("Crash", "addConversationFragmentStartCall. Set session " + newSessionWithOpponents);
                SessionManager.setCurrentSession(newSessionWithOpponents);
            } catch (IllegalStateException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        BaseConversationFragment fragment = (QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO.equals(
                qbConferenceType)) ? new VideoConversationFragment() : new AudioConversationFragment();

        Bundle bundle = new Bundle();
        bundle.putInt(Consts.CALL_DIRECTION_TYPE_EXTRAS, callDirectionType.ordinal());
        fragment.setArguments(bundle);

        getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, CONVERSATION_CALL_FRAGMENT).commit();
    }

    private void startOutBeep() {
        ringtone = MediaPlayer.create(this, R.raw.beep);
        ringtone.setLooping(true);
        ringtone.start();
    }

    public void stopOutBeep() {
        if (ringtone != null) {
            try {
                ringtone.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            ringtone.release();
            ringtone = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (SessionManager.getCurrentSession() != null){
            if (SessionManager.getCurrentSession().getState() == QBRTCSession.QBRTCSessionState.QB_RTC_SESSION_ACTIVE) {
                hangUpCurrentSession();
            } else {
                rejectCurrentSession();
            }
            SessionManager.setCurrentSession(null);
        }
        removeActivityAsCallbackToRTCClient();
    }

    private void removeActivityAsCallbackToRTCClient() {
        // Remove activity as callback from RTCClient
        QBRTCClient.getInstance().removeSessionsCallbacksListener(this);
        QBRTCClient.getInstance().removeConnectionCallbacksListener(this);
    }

    @Override
    public void onAttachedToWindow() {
        this.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    @Override
    public void onBackPressed() {

    }
}

