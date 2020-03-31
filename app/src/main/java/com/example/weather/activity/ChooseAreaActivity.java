package com.example.weather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.weather.R;
import com.example.weather.model.City;
import com.example.weather.model.County;
import com.example.weather.model.Province;
import com.example.weather.model.WeatherDB;
import com.example.weather.util.HttpCallbackListener;
import com.example.weather.util.HttpUtil;
import com.example.weather.util.Utility;

import java.util.ArrayList;
import java.util.List;

//遍历省市县数据
public class ChooseAreaActivity extends Activity {
    private static final int LEVEL_PROVINCE=0;
    private static final int LEVEL_CITY=1;
    private static final int LEVEL_COUNTY=2;
    private ProgressDialog progressDialog;//?????
    private TextView titleText;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private WeatherDB weatherDB;
    private List<String> dataList=new ArrayList<String>();//???
    private List<Province> provinceList;// /省列表
    private List<City> cityList;//市列表
    private List<County> countyList;//县列表
    private Province selctedProvince;//选中的省份
    private City selectedCity;//选中的市
    private int currentLevel;//当前选中的级别
    private boolean isFromWeatherActivity;//是否从WeatherActivity中跳转过来

    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_area);

        listView=(ListView)findViewById(R.id.list_view);
        titleText=(TextView)findViewById(R.id.title_text);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        weatherDB=WeatherDB.getInstance(this);

       //isFromWeatherActivity=getIntent().getBooleanExtra("from_weather_avtivity",false);
        SharedPreferences prefs= PreferenceManager.getDefaultSharedPreferences(this);
        // 已经选择了城市且不是从WeatherActivity跳转过来，才会直接跳转到WeatherActivity
        if (prefs.getBoolean("city_selected", false)
                && !isFromWeatherActivity) {
            Intent intent = new Intent(this, WeatherActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int index, long arg3) {
                if (currentLevel==LEVEL_PROVINCE){
                    selctedProvince=provinceList.get(index);
                    queryCities();
                }
                else if(currentLevel==LEVEL_CITY){
                    selectedCity=cityList.get(index);
                    queryCounties();
                }
                else if (currentLevel==LEVEL_COUNTY){
                    String countyCode = countyList.get(index).getCountyCode();
                    Intent intent = new Intent(ChooseAreaActivity.this,
                            WeatherActivity.class);
                    intent.putExtra("county_code", countyCode);
                    startActivity(intent);
                    finish();
                }
            }
        });
        queryProvinces();//加载省级数据
    }

    //查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器上查询
    private void queryProvinces(){
        provinceList=weatherDB.loadProvinces();//在数据库中读取省级信息
        if (provinceList.size()>0) {
            dataList.clear();
            for (Province province:provinceList){
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText("中国");
            currentLevel=LEVEL_PROVINCE;
        }
        else {
            queryFromServer(null,"province");//????
        }
    }

    //查询选中省内所有的市，优先从数据库查询，如果没有查询到再去服务器上查询
    private void queryCities(){
        cityList=weatherDB.loadCities(selctedProvince.getId());
        if (cityList.size()>0) {
            dataList.clear();
            for (City city:cityList){
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText(selctedProvince.getProvinceName());
            currentLevel=LEVEL_CITY;
        }
        else {
            queryFromServer(selctedProvince.getProvinceCode(),"city");//????
        }
    }

    //查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询
    private void queryCounties(){
        countyList=weatherDB.loadCounties(selectedCity.getId());
        if (countyList.size()>0) {
            dataList.clear();
            for (County county:countyList){
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText(selectedCity.getCityName());
            currentLevel=LEVEL_COUNTY;
        }
        else {
            queryFromServer(selectedCity.getCityCode(),"county");//????
        }
    }

    //根据传入的代号和类型从服务器上查询省市县数据
    private void queryFromServer(final String code,final String type){
        String address;
        if (!TextUtils.isEmpty(code)){
            address="http://www.weather.com.cn/data/list3/city"+code+".xml";
        }else{
            address="http://www.weather.com.cn/data/list3/city.xml";
        }
        showProgressDialog();
        //确认查询地址后，调用HttpUtil的sendHttpRequest()方法来向服务器发送请求，响应的数据会回调到 onFinish()方法中
        HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
            @Override
            public void onFinish(String response) {
                boolean result=false;
                if("province".equals(type)){
                    //调用 Utility 的handleProvincesResponse()方法来解析和处理服务器返回的数据，并存储到数据库中
                    result= Utility.handleProvincesResponse(weatherDB,response);
                }else if("city".equals(type)){
                    result=Utility.handleCitiesResponse(weatherDB,response,selctedProvince.getId());
                }else if("county".equals(type)){
                    result=Utility.handleCountiesResponse(weatherDB,response,selectedCity.getId());
                }

                if (result){
                    // 通过runOnUiThread()方法回到主线程处理逻辑,实现原理基于异步消息处理机制
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                // 通过runOnUiThread()方法回到主线程处理逻辑
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(ChooseAreaActivity.this,
                                "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });


    }

    //显示进度对话框
    private void showProgressDialog(){
        if (progressDialog==null){
            progressDialog=new ProgressDialog(this);
            progressDialog.setMessage("正在加载......");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    // 关闭进度对话框
    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    //* 捕获Back按键，根据当前的级别来判断，此时应该返回市列表、省列表、还是直接退出。
    public void onBackPressed() {
        if (currentLevel == LEVEL_COUNTY) {
            queryCities();
        } else if (currentLevel == LEVEL_CITY) {
            queryProvinces();
        } else {
           /*if (isFromWeatherActivity){
               Intent intent=new Intent(this,WeatherActivity.class);
               startActivity(intent);
           }*/
           finish();
        } }
}
