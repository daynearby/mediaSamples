package com.github.daynearby.javacvsamples;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    /**
     * data list
     */
    private List<String> dataList = Arrays.asList("Camera-record");//

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView itemListView = findViewById(R.id.id_item_list);
        ArrayAdapter<String> mAdapter = new ArrayAdapter<String>(this,R.layout.item_options,dataList );
        itemListView.setAdapter(mAdapter);
        itemListView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        switch (i){
            case 0://camera recorder
                startActivity(new Intent(this, RecordActivity.class));
                break;
        }
    }

}
