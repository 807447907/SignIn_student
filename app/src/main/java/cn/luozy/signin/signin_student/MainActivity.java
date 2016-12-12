package cn.luozy.signin.signin_student;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static java.net.URLEncoder.encode;

public class MainActivity extends AppCompatActivity {
    private final String searchURL = "https://signin.luozy.cn/api/student/search";
    private final String signInURL = "https://signin.luozy.cn/api/student/sign_in";

    private String student_id;
    private String student_token;
    private Toast mToast;

    private MyAdapter mAdapter;
    private HashSet<String> bluetoothList = new HashSet<>();
    private ArrayList<HashMap<String, Object>> signInList = new ArrayList<>();

    private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mToast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);

        student_id = getIntent().getStringExtra("student_id");
        student_token = getIntent().getStringExtra("student_token");

        mAdapter = new MyAdapter(this);
        ListView listViewSignIn = (ListView) findViewById(R.id.listViewSignIn);
        listViewSignIn.setAdapter(mAdapter);

        startSearchBluetooth();
    }

    private void startSearchBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showTip("您的设备不支持蓝牙");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivityForResult(intent, 0);
            return;
        }

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    bluetoothList.add(device.getAddress());
                    System.out.println(device.getAddress());
                    startRefreshSignInList();
                }
            }
        };
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        bluetoothAdapter.startDiscovery();
        showTip("开始搜寻附近的签到。");
    }

    private void startRefreshSignInList() {
        new Thread() {
            public void run() {
                attemptGetSignInList();
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 0:
                if (resultCode == 0) {
                    showTip("请允许开启蓝牙设备");
                } else {
                    showTip("蓝牙设备已开启");
                }
                break;
            default:
                break;
        }
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String resp;
            switch (msg.what) {
                case 0:
                    resp = msg.getData().getString("resp");
                    System.out.println(resp);

                    ArrayList<HashMap<String, Object>> tempList = new ArrayList<>();
                    try {
                        JSONTokener jsonTokener = new JSONTokener(resp);
                        JSONObject jsonObject = (JSONObject) jsonTokener.nextValue();
                        if (jsonObject.getInt("status") != 0) {
                            JSONObject errors = jsonObject.getJSONObject("errors");
                            String errorMsg = null;
                            if (errors.has("student_id")) {
                                errorMsg = errors.getJSONArray("student_id").getString(0);
                            } else if (errors.has("student_token")) {
                                errorMsg = errors.getJSONArray("student_token").getString(0);
                            }
                            showTip(errorMsg);
                            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                            return;
                        }

                        JSONArray data = jsonObject.getJSONArray("data");
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.getJSONObject(i);
                            HashMap<String, Object> params = new HashMap<>();
                            params.put("signIn_id", item.getInt("signIn_id"));
                            params.put("signIn_name", item.getString("signIn_name"));
                            params.put("signIned", item.getBoolean("signIned"));
                            tempList.add(params);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    signInList.clear();
                    signInList.addAll(tempList);
                    mAdapter.notifyDataSetChanged();
                    break;

                case 1:
                    resp = msg.getData().getString("resp");
                    try {
                        JSONTokener jsonTokener = new JSONTokener(resp);
                        JSONObject jsonObject = (JSONObject) jsonTokener.nextValue();
                        if (jsonObject.getInt("status") == 0) {
                            showTip(jsonObject.getString("msg"));
                        } else {
                            JSONObject errors = jsonObject.getJSONObject("errors");
                            boolean exit = false;
                            String errorMsg = null;
                            if (errors.has("student_id")) {
                                errorMsg = errors.getJSONArray("student_id").getString(0);
                                exit = true;
                            } else if (errors.has("student_token")) {
                                errorMsg = errors.getJSONArray("student_token").getString(0);
                                exit = true;
                            } else if (errors.has("signIn_id")) {
                                errorMsg = errors.getJSONArray("signIn_id").getString(0);
                            }
                            showTip(errorMsg);
                            if (exit) {
                                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                startActivity(intent);
                                finish();
                                return;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    startRefreshSignInList();
                    break;
                default:
                    break;
            }
        }
    };

    private void attemptGetSignInList() {
        JSONArray jsonArray = new JSONArray();
        for (String i : bluetoothList) {
            jsonArray.put(i);
        }

        Map<String, String> params = new HashMap<>();
        params.put("student_id", student_id);
        params.put("student_token", student_token);
        params.put("bluetooth_list", jsonArray.toString());
        String resp = postRequest(searchURL, params);

        if (!resp.isEmpty()) {
            Bundle bundle = new Bundle();
            bundle.putString("resp", resp);
            Message message = new Message();
            message.setData(bundle);
            message.what = 0;
            handler.sendMessage(message);
        }
    }

    private void attemptSignIn(final Integer signIn_id) {
        Map<String, String> params = new HashMap<>();
        params.put("student_id", student_id);
        params.put("student_token", student_token);
        params.put("signIn_id", signIn_id.toString());

        String resp = postRequest(signInURL, params);
        if (!resp.isEmpty()) {
            Bundle bundle = new Bundle();
            bundle.putString("resp", resp);
            Message message = new Message();
            message.setData(bundle);
            message.what = 1;
            handler.sendMessage(message);
        }
    }

    public static String buildParam(Map<String, String> params) {
        String res = "";
        try {
            Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
            Map.Entry<String, String> entry;
            if (it.hasNext()) {
                entry = it.next();
                res += entry.getKey() + "=" + encode(entry.getValue(), "UTF-8");
            }
            while (it.hasNext()) {
                entry = it.next();
                res += "&" + entry.getKey() + "=" + encode(entry.getValue(), "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return res;
    }

    public static String postRequest(String path, Map<String, String> params) {
        URL url;
        String resp = "";
        try {
            url = new URL(path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setDoOutput(true);
            conn.setDoInput(true);

            PrintWriter printWriter = new PrintWriter(conn.getOutputStream());
            printWriter.write(buildParam(params));
            printWriter.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;

            while ((line = br.readLine()) != null) {
                resp += line;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resp;
    }

    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }

    public class MyAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        MyAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return signInList.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {

            View myView = mInflater.inflate(R.layout.list_item, null);

            TextView textViewTitle = (TextView) myView.findViewById(R.id.textViewTitle);
            final Button buttonSignIn = (Button) myView.findViewById(R.id.buttonSignIn);

            final Integer signIn_id = (Integer) signInList.get(position).get("signIn_id");
            final String signIn_name = signInList.get(position).get("signIn_name").toString();
            final boolean signIned = (boolean) signInList.get(position).get("signIned");


            textViewTitle.setText(signIn_name);

            if (signIned) {
                buttonSignIn.setText("已签到");
                buttonSignIn.setEnabled(false);
            } else {
                buttonSignIn.setText("立即签到");
                buttonSignIn.setEnabled(true);
                buttonSignIn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new Thread() {
                            public void run() {
                                attemptSignIn(signIn_id);
                            }
                        }.start();
                    }
                });
            }


            return myView;
        }
    }
}
