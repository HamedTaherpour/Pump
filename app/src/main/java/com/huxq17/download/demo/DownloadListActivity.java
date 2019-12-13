package com.huxq17.download.demo;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.huxq17.download.Pump;
import com.huxq17.download.core.DownloadInfo;
import com.huxq17.download.utils.LogUtil;
import com.huxq17.download.demo.installapk.APK;
import com.huxq17.download.message.DownloadListener;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class DownloadListActivity extends AppCompatActivity {
    public static void start(Context context, String tag) {
        Intent intent = new Intent(context, DownloadListActivity.class);
        intent.putExtra("tag", tag);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    DownloadListener downloadObserver = new DownloadListener() {
        @Override
        public void onProgress(int progress) {
            DownloadInfo downloadInfo = getDownloadInfo();
            DownloadViewHolder viewHolder = (DownloadViewHolder) downloadInfo.getExtraData();
            if (viewHolder != null) {
                DownloadInfo tag = map.get(viewHolder);
                if (tag != null && tag.getId().equals(downloadInfo.getId())) {
                    viewHolder.bindData(downloadInfo, getStatus());
                }
            }
        }

        @Override
        public void onFailed() {
            super.onFailed();
            LogUtil.e("onFailed code=" + getDownloadInfo().getErrorCode());
        }
    };
    private HashMap<DownloadViewHolder, DownloadInfo> map = new HashMap<>();
    private RecyclerView recyclerView;
    private DownloadAdapter downloadAdapter;
    private List<DownloadInfo> downloadInfoList;
    private String tag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tag = getIntent().getStringExtra("tag");
        setContentView(R.layout.activity_download_list);
        downloadObserver.enable();
        recyclerView = findViewById(R.id.rvDownloadList);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        //Get all download list.
        downloadInfoList = TextUtils.isEmpty(tag) ? Pump.getAllDownloadList() : Pump.getDownloadListByTag(tag);

        //Sort download list if need.
        Collections.sort(downloadInfoList, new Comparator<DownloadInfo>() {
            @Override
            public int compare(DownloadInfo o1, DownloadInfo o2) {
                return (int) (o1.getCreateTime() - o2.getCreateTime());
            }
        });
        recyclerView.setLayoutManager(linearLayoutManager);
        downloadAdapter = new DownloadAdapter(map, downloadInfoList);
        recyclerView.setAdapter(downloadAdapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        downloadObserver.disable();
        for (DownloadInfo downloadInfo : downloadInfoList) {
            Pump.stop(downloadInfo);
        }
//        Pump.shutdown();
    }

    public static class DownloadAdapter extends RecyclerView.Adapter<DownloadViewHolder> {
        List<? extends DownloadInfo> downloadInfoList;
        HashMap<DownloadViewHolder, DownloadInfo> map;

        public DownloadAdapter(HashMap<DownloadViewHolder, DownloadInfo> map, List<DownloadInfo> downloadInfoList) {
            this.downloadInfoList = downloadInfoList;
            this.map = map;
        }

        @NonNull
        @Override
        public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_download_list, viewGroup, false);
            return new DownloadViewHolder(v, this);
        }

        @Override
        public void onBindViewHolder(@NonNull DownloadViewHolder viewHolder, int i) {
            DownloadInfo downloadInfo = downloadInfoList.get(i);
            downloadInfo.setExtraData(viewHolder);
            map.put(viewHolder, downloadInfo);

            viewHolder.bindData(downloadInfo, downloadInfo.getStatus());
        }

        public void delete(DownloadViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            downloadInfoList.remove(position);
            notifyItemRemoved(position);
            map.remove(viewHolder);
        }

        @Override
        public int getItemCount() {
            return downloadInfoList.size();
        }
    }

    public static class DownloadViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        ProgressBar progressBar;
        TextView tvName;
        TextView tvStatus;
        TextView tvSpeed;
        TextView tvDownload;
        DownloadInfo downloadInfo;
        DownloadInfo.Status status;
        AlertDialog dialog;

        public DownloadViewHolder(@NonNull View itemView, final DownloadAdapter adapter) {
            super(itemView);
            progressBar = itemView.findViewById(R.id.pb_progress);
            tvName = itemView.findViewById(R.id.tv_name);
            tvStatus = itemView.findViewById(R.id.bt_status);
            tvSpeed = itemView.findViewById(R.id.tv_speed);
            tvDownload = itemView.findViewById(R.id.tv_download);
            tvStatus.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            dialog = new AlertDialog.Builder(itemView.getContext())
                    .setTitle("Confirm delete?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            adapter.delete(DownloadViewHolder.this);
                            Pump.delete(downloadInfo);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .create();
        }

        public void bindData(DownloadInfo downloadInfo, DownloadInfo.Status status) {
            this.downloadInfo = downloadInfo;
            this.status = status;
            tvName.setText(downloadInfo.getName());
            String speed = "";
            int progress = downloadInfo.getProgress();
            progressBar.setProgress(progress);
            switch (status) {
                case STOPPED:
                    tvStatus.setText("Start");
                    break;
                case PAUSING:
                    tvStatus.setText("Pausing");
                    break;
                case PAUSED:
                    tvStatus.setText("Continue");
                    break;
                case WAIT:
                    tvStatus.setText("Waiting");
                    break;
                case RUNNING:
                    tvStatus.setText("Pause");
                    speed = downloadInfo.getSpeed();
                    break;
                case FINISHED:
                    tvStatus.setText("Install");
                    break;
                case FAILED:
                    tvStatus.setText("Retry");
                    break;
            }
            tvSpeed.setText(speed);
            long completedSize = downloadInfo.getCompletedSize();
            long totalSize = downloadInfo.getContentLength();
            tvDownload.setText(Util.getDataSize(completedSize) + "/" + Util.getDataSize(totalSize));
        }

        @Override
        public void onClick(View v) {
            if (v == tvStatus) {
                switch (status) {
                    case STOPPED:
                    case PAUSED:
                    case FAILED:
                        Pump.resume(downloadInfo);
                        break;
                    case WAIT:
                        //do nothing.
                        break;
                    case RUNNING:
                        Pump.pause(downloadInfo);
                        break;
                    case FINISHED:
                        APK.with(itemView.getContext())
                                .from(downloadInfo.getFilePath())
                                .install();
                        break;
                }
            }

        }

        @Override
        public boolean onLongClick(View v) {
            dialog.show();
            return true;
        }
    }

}
