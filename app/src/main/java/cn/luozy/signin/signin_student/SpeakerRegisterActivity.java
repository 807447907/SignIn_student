package cn.luozy.signin.signin_student;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeakerVerifier;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechListener;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.VerifierListener;
import com.iflytek.cloud.VerifierResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class SpeakerRegisterActivity extends AppCompatActivity {

    private static final int PWD_TYPE_NUM = 3;
    private int mPwdType = PWD_TYPE_NUM;

    private TextView textView_title;
    private TextView textView_speaker_password;
    private TextView textView_message;


    private SpeakerVerifier mVerifier;
    // 声纹AuthId，用户在云平台的身份标识，也是声纹模型的标识
    // 请使用英文字母或者字母和数字的组合，勿使用中文字符
    private String mAuthId;
    // 数字声纹密码
    private String mNumPwd;
    // 数字声纹密码段，默认有5段
    private String[] mNumPwdSegs;

    private Toast mToast;

    private String student_id;
    private String student_token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speaker_register);

        textView_title = (TextView) findViewById(R.id.textView_title);
        textView_speaker_password = (TextView) findViewById(R.id.textView_password);
        textView_message = (TextView) findViewById(R.id.textView_tips);

        mToast = Toast.makeText(SpeakerRegisterActivity.this, "", Toast.LENGTH_SHORT);

        student_id = getIntent().getStringExtra("student_id");
        student_token = getIntent().getStringExtra("student_token");
        mAuthId = "student_" + student_id;

        startGetPasswordList();
    }

    private void speakerComplete() {
        Intent intent = new Intent(SpeakerRegisterActivity.this, MainActivity.class);
        intent.putExtra("student_id", student_id);
        intent.putExtra("student_token", student_token);
        startActivity(intent);
        finish();
    }

    private void startGetPasswordList() {
        mVerifier = SpeakerVerifier.createVerifier(SpeakerRegisterActivity.this, null);
        mVerifier.setParameter(SpeechConstant.ISV_PWDT, "" + mPwdType);
        mVerifier.getPasswordList(mPwdListenter);
    }

    private void startRegister() {
        mVerifier.setParameter(SpeechConstant.PARAMS, null);
        mVerifier.setParameter(SpeechConstant.ISV_AUDIO_PATH,
                Environment.getExternalStorageDirectory().getAbsolutePath() + "/msc/test.pcm");

        mVerifier.setParameter(SpeechConstant.ISV_PWD, mNumPwd);
        textView_speaker_password.setText(mNumPwdSegs[0]);
        textView_message.setText("训练 第" + 1 + "遍，剩余4遍");

        mVerifier.setParameter(SpeechConstant.AUTH_ID, mAuthId);
        // 设置业务类型为注册
        mVerifier.setParameter(SpeechConstant.ISV_SST, "train");
        // 设置声纹密码类型
        mVerifier.setParameter(SpeechConstant.ISV_PWDT, "" + mPwdType);
        // 开始注册
        mVerifier.startListening(mRegisterListener);
    }

    private void startVerify() {
        mVerifier.setParameter(SpeechConstant.PARAMS, null);
        mVerifier.setParameter(SpeechConstant.ISV_AUDIO_PATH,
                Environment.getExternalStorageDirectory().getAbsolutePath() + "/msc/verify.pcm");
        mVerifier = SpeakerVerifier.getVerifier();
        // 设置业务类型为验证
        mVerifier.setParameter(SpeechConstant.ISV_SST, "verify");

        // 数字密码注册需要传入密码
        String verifyPwd = mVerifier.generatePassword(8);
        mVerifier.setParameter(SpeechConstant.ISV_PWD, verifyPwd);
        textView_speaker_password.setText(verifyPwd);

        mVerifier.setParameter(SpeechConstant.AUTH_ID, mAuthId);
        mVerifier.setParameter(SpeechConstant.ISV_PWDT, "" + mPwdType);
        // 开始验证
        mVerifier.startListening(mVerifyListener);
    }

    private void performModelOperation(String operation, SpeechListener listener) {
        // 清空参数
        mVerifier.setParameter(SpeechConstant.PARAMS, null);
        mVerifier.setParameter(SpeechConstant.ISV_PWDT, "" + mPwdType);

        // 设置auth_id，不能设置为空
        mVerifier.sendRequest(operation, mAuthId, listener);
    }


    private SpeechListener mPwdListenter = new SpeechListener() {
        @Override
        public void onEvent(int eventType, Bundle params) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            String result = new String(buffer);
            switch (mPwdType) {
                case PWD_TYPE_NUM:
                    StringBuffer numberString = new StringBuffer();
                    try {
                        JSONObject object = new JSONObject(result);
                        if (!object.has("num_pwd")) {
                            return;
                        }

                        JSONArray pwdArray = object.optJSONArray("num_pwd");
                        numberString.append(pwdArray.get(0));
                        for (int i = 1; i < pwdArray.length(); i++) {
                            numberString.append("-" + pwdArray.get(i));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mNumPwd = numberString.toString();
                    mNumPwdSegs = mNumPwd.split("-");
                    performModelOperation("que", mModelOperationListener);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (null != error && ErrorCode.SUCCESS != error.getErrorCode()) {
                showTip("获取失败：" + error.getErrorCode());
            }
        }
    };

    private SpeechListener mModelOperationListener = new SpeechListener() {

        @Override
        public void onEvent(int eventType, Bundle params) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            String result = new String(buffer);
            try {
                JSONObject object = new JSONObject(result);
                String cmd = object.getString("cmd");
                int ret = object.getInt("ret");

                if ("que".equals(cmd)) {
                    if (ret == ErrorCode.SUCCESS) {
                        textView_title.setText("开始验证声纹");
                        startVerify();
                    } else if (ret == ErrorCode.MSP_ERROR_FAIL) {
                        textView_title.setText("开始注册声纹");
                        startRegister();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (null != error && ErrorCode.SUCCESS != error.getErrorCode()) {
                showTip("操作失败：" + error.getPlainDescription(true));
            }
        }
    };

    private VerifierListener mRegisterListener = new VerifierListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
        }

        @Override
        public void onResult(VerifierResult result) {
            if (result.ret == ErrorCode.SUCCESS) {
                switch (result.err) {
                    case VerifierResult.MSS_ERROR_IVP_GENERAL:
                        showTip("内核异常");
                        // mShowMsgTextView.setText("内核异常");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_EXTRA_RGN_SOPPORT:
                        showTip("训练达到最大次数");
                        //  mShowRegFbkTextView.setText("训练达到最大次数");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_TRUNCATED:
                        showTip("出现截幅");
                        //  mShowRegFbkTextView.setText("出现截幅");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_MUCH_NOISE:
                        showTip("太多噪音");
                        //  mShowRegFbkTextView.setText("太多噪音");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_UTTER_TOO_SHORT:
                        showTip("录音太短");
                        //  mShowRegFbkTextView.setText("录音太短");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_TEXT_NOT_MATCH:
                        showTip("训练失败，您所读的文本不一致");
                        //  mShowRegFbkTextView.setText("训练失败，您所读的文本不一致");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_TOO_LOW:
                        showTip("音量太低");
                        // mShowRegFbkTextView.setText("音量太低");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_NO_ENOUGH_AUDIO:
                        showTip("音频长达不到自由说的要求");
                        // mShowMsgTextView.setText("音频长达不到自由说的要求");
                    default:
                        // mShowRegFbkTextView.setText("");
                        //showTip("训练达到最大次数");
                        break;
                }

                if (result.suc == result.rgn) {
                    speakerComplete();
                    showTip("注册成功");
                    //mResultEditText.setText("您的数字密码声纹ID：\n" + result.vid);
                } else {
                    int nowTimes = result.suc + 1;
                    int leftTimes = result.rgn - nowTimes;
                    textView_speaker_password.setText(mNumPwdSegs[nowTimes - 1]);
                    textView_message.setText("训练 第" + nowTimes + "遍，剩余" + leftTimes + "遍");
                }
            } else {
                // mShowMsgTextView.setText("注册失败，请重新开始。");
                showTip("注册失败，请重新开始。");
            }
        }

        // 保留方法，暂不用
        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle arg3) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

        @Override
        public void onError(SpeechError error) {
            if (error.getErrorCode() == ErrorCode.MSP_ERROR_ALREADY_EXIST) {
                showTip("模型已存在，如需重新注册，请先删除");
            } else {
                showTip("onError Code：" + error.getPlainDescription(true));
            }
        }

        @Override
        public void onEndOfSpeech() {
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            showTip("开始说话");
        }
    };

    private VerifierListener mVerifyListener = new VerifierListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
        }

        @Override
        public void onResult(VerifierResult result) {
            if (result.ret == 0) {
                speakerComplete();
                showTip("验证通过");
                // 验证通过
                //  mShowMsgTextView.setText();
            } else {
                // 验证不通过
                switch (result.err) {
                    case VerifierResult.MSS_ERROR_IVP_GENERAL:
                        showTip("内核异常");
                        // mShowMsgTextView.setText("内核异常");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_TRUNCATED:
                        showTip("出现截幅");
                        //  mShowRegFbkTextView.setText("出现截幅");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_MUCH_NOISE:
                        showTip("太多噪音");
                        //  mShowRegFbkTextView.setText("太多噪音");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_UTTER_TOO_SHORT:
                        showTip("录音太短");
                        //  mShowRegFbkTextView.setText("录音太短");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_TEXT_NOT_MATCH:
                        showTip("训练失败，您所读的文本不一致");
                        //  mShowRegFbkTextView.setText("训练失败，您所读的文本不一致");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_TOO_LOW:
                        showTip("音量太低");
                        // mShowRegFbkTextView.setText("音量太低");
                        break;
                    case VerifierResult.MSS_ERROR_IVP_NO_ENOUGH_AUDIO:
                        showTip("音频长达不到自由说的要求");
                        // mShowMsgTextView.setText("音频长达不到自由说的要求");
                    default:
                        //mShowMsgTextView.setText("验证不通过");
                        break;
                }
            }
        }

        // 保留方法，暂不用
        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle arg3) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

        @Override
        public void onError(SpeechError error) {

            switch (error.getErrorCode()) {
                case ErrorCode.MSP_ERROR_NOT_FOUND:
                    //  mShowMsgTextView.setText("模型不存在，请先注册");
                    break;

                default:
                    showTip("onError Code：" + error.getPlainDescription(true));
                    break;
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }
    };

    @Override
    protected void onDestroy() {
        if (null != mVerifier) {
            mVerifier.stopListening();
            mVerifier.destroy();
        }
        super.onDestroy();
    }

    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }
}
