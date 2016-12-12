package cn.luozy.signin.signin_student;

import android.app.Application;

import com.iflytek.cloud.SpeechUtility;


/**
 * Created by marvels on 2016/12/12.
 */

public class AppStart extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 应用程序入口处调用,避免手机内存过小，杀死后台进程,造成SpeechUtility对象为null
        // 设置你申请的应用appid
        SpeechUtility.createUtility(this, "appid=" + getString(R.string.app_id));
    }
}
