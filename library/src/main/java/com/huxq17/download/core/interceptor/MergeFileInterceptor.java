package com.huxq17.download.core.interceptor;

import android.text.TextUtils;

import com.huxq17.download.DownloadProvider;
import com.huxq17.download.ErrorCode;
import com.huxq17.download.OnVerifyMd5Listener;
import com.huxq17.download.PumpFactory;
import com.huxq17.download.config.IDownloadConfigService;
import com.huxq17.download.core.DownloadDetailsInfo;
import com.huxq17.download.core.DownloadInfo;
import com.huxq17.download.core.DownloadInterceptor;
import com.huxq17.download.core.DownloadRequest;
import com.huxq17.download.core.task.DownloadTask;
import com.huxq17.download.db.DBService;
import com.huxq17.download.utils.FileUtil;
import com.huxq17.download.utils.LogUtil;

import java.io.File;
import java.io.FilenameFilter;

import static com.huxq17.download.utils.Util.DOWNLOAD_PART;

public class MergeFileInterceptor implements DownloadInterceptor {
    private DownloadDetailsInfo downloadInfo;
    private DownloadRequest downloadRequest;

    @Override
    public DownloadInfo intercept(DownloadChain chain) {
        downloadRequest = chain.request();
        downloadInfo = downloadRequest.getDownloadInfo();
        DownloadTask downloadTask = downloadInfo.getDownloadTask();

        synchronized (downloadTask.getLock()) {
            long contentLength = downloadInfo.getContentLength();
            long completedSize = downloadInfo.getCompletedSize();
            if (!downloadInfo.isSupportBreakpoint()) {
                checkDownloadResult(contentLength, completedSize);
                return downloadInfo.snapshot();
            }

            File tempDir = downloadInfo.getTempDir();
            File[] downloadPartFiles = tempDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith(DOWNLOAD_PART);
                }
            });
            if (contentLength > 0 && completedSize == contentLength && downloadPartFiles != null
                    && downloadPartFiles.length == downloadTask.getRequest().getThreadNum()) {
                File file = downloadInfo.getDownloadFile();
                downloadInfo.deleteDownloadFile();
                long startTime = System.currentTimeMillis();
                boolean mergeSuccess = false;
                if (downloadPartFiles.length == 1) {
                    if (FileUtil.renameTo(downloadPartFiles[0], file)) {
                        mergeSuccess = true;
                    }
                } else {
                    if (FileUtil.mergeFiles(downloadPartFiles, file)) {
                        mergeSuccess = true;
                    }
                }
                downloadInfo.deleteTempDir();
                if (mergeSuccess) {
                    LogUtil.d("Merge " + downloadInfo.getName() + " spend=" +
                            (System.currentTimeMillis() - startTime) + "; file.length=" + file.length());
                    checkDownloadResult(contentLength, completedSize);
                } else {
                    downloadInfo.setErrorCode(ErrorCode.MERGE_FILE_FAIELD);
                }
            }
        }
        return downloadInfo.snapshot();
    }

    private void checkDownloadResult(long contentLength, long completedSize) {
        File downloadFile = downloadInfo.getDownloadFile();
        long downloadFileLength = downloadFile == null ? 0 : downloadFile.length();
        if (downloadFileLength == contentLength &&
                isMd5Equals(downloadInfo.getMd5(), downloadInfo.getDownloadFile(), downloadRequest.getOnVerifyMd5Listener())) {
            DownloadProvider.CacheBean cacheBean = downloadInfo.getCacheBean();
            if (cacheBean != null) {
                DBService.getInstance().updateCache(cacheBean);
            }
            if (downloadRequest.getOnDownloadSuccessListener() != null) {
                downloadRequest.getOnDownloadSuccessListener().onDownloadSuccess(downloadInfo.getDownloadFile(), downloadRequest);
            }
            downloadInfo.setFinished(1);
            downloadInfo.setStatus(DownloadInfo.Status.FINISHED);
            downloadInfo.setCompletedSize(completedSize);
        } else {
            downloadInfo.setStatus(DownloadInfo.Status.FAILED);
        }
    }

    private boolean isMd5Equals(String md5, File downloadFile, OnVerifyMd5Listener listener) {
        if (listener == null) {
            listener = PumpFactory.getService(IDownloadConfigService.class).getOnVerifyMd5Listener();
        }
        if (!TextUtils.isEmpty(md5) && listener != null) {
            return listener.onVerifyMd5(md5, downloadFile);
        }
        return true;
    }
}