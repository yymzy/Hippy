/* Tencent is pleased to support the open source community by making Hippy available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tencent.mtt.hippy.devsupport;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tencent.mtt.hippy.HippyGlobalConfigs;
import com.tencent.mtt.hippy.utils.LogUtils;
import com.tencent.mtt.hippy.utils.UIThreadUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Stack;

@SuppressWarnings({"unused"})
public class DevServerImpl implements View.OnClickListener, DevServerInterface,
    DevExceptionDialog.OnReloadListener,
    DevRemoteDebugManager.RemoteDebugExceptionHandler, LiveReloadController.LiveReloadCallback {

  private static final String TAG = "DevServerImpl";

  final DevServerHelper mFetchHelper;
  DevServerCallBack mServerCallBack;
  ProgressDialog mProgressDialog;
  DevExceptionDialog mExceptionDialog;
  private final DevServerConfig mServerConfig;
  private final HashMap<Context, DevFloatButton> mHostButtonMap;
  // 一个 DevServerImpl 实例可管理多个 HippyRootView 的调试，对应多个DebugButton
  private final Stack<DevFloatButton> mDebugButtonStack;
  private final LiveReloadController mLiveReloadController;

  DevServerImpl(HippyGlobalConfigs configs, String serverHost, String bundleName, String remoteServerUrl) {
    mFetchHelper = new DevServerHelper(configs, serverHost, remoteServerUrl);
    mServerConfig = new DevServerConfig(serverHost, bundleName);
    mDebugButtonStack = new Stack<>();
    mHostButtonMap = new HashMap<>();
    mLiveReloadController = new LiveReloadController(mFetchHelper);

    showProgressDialog();
  }

  private void showProgressDialog() {
    Context host = null;
    if (mDebugButtonStack.size() > 0) {
      host = mDebugButtonStack.peek().getContext();
    }

    if (host == null) {
      return;
    }

    if (mProgressDialog == null) {
      mProgressDialog = new ProgressDialog(host);
      mProgressDialog.setCancelable(true);
      mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    }
    mProgressDialog.show();
  }

  @Override
  public void onClick(final View v) {
    final boolean isLiveReloadEnable = mServerConfig.enableLiveDebug();
    if (v.getContext() instanceof Application) {
      LogUtils.e(TAG, "Hippy context is an Application, so can not show a dialog!");
    } else {
      new AlertDialog.Builder(v.getContext()).setItems(
          new String[]{"Reload"},
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              if (which == 0) {
                reload();
              }
            }
          }).show();
    }
  }

  void startLiveDebug() {
    if (mServerConfig.enableLiveDebug()) {
      mLiveReloadController.startLiveReload(this);
    } else {
      mLiveReloadController.stopLiveReload();
    }

  }

  @Override
  public String createResourceUrl(String resName) {
    return mFetchHelper
        .createBundleURL(mServerConfig.getServerHost(), resName, mServerConfig.enableRemoteDebug(),
            false, false);
  }

  @Override
  public void onLoadResourceSucceeded() {
    if (mProgressDialog != null) {
      mProgressDialog.dismiss();
    }
  }

  @Override
  public void onLoadResourceFailed(@NonNull String url, @Nullable String errorMessage) {
    DevServerException exception = new DevServerException("Could not connect to development server." + "URL: " + url
            + "  try to :adb reverse tcp:38989 tcp:38989 , message : " + errorMessage);
    if (mDebugButtonStack.isEmpty()) {
      mServerCallBack.onInitDevError(exception);
    } else {
      handleException(exception);
    }
  }

  @Override
  public String createDebugUrl(String host, String componentName, String debugClientId) {
    return mFetchHelper.createDebugURL(host, !TextUtils.isEmpty(componentName) ? componentName :
            mServerConfig.getBundleName(), debugClientId);
  }

  @Override
  public void reload() {
    if (mServerCallBack != null) {
      mServerCallBack.onDevBundleReLoad();
    }
  }

  @Override
  public void setDevServerCallback(DevServerCallBack devServerCallback) {
    this.mServerCallBack = devServerCallback;
  }

  @Override
  public void attachToHost(Context context) {
    DevFloatButton debugButton = new DevFloatButton(context);
    debugButton.setOnClickListener(this);

    if (context instanceof Activity) {
      // 添加到Activity的根部，这就稳当了。
      ViewGroup decorView = (ViewGroup)((Activity)context).getWindow().getDecorView();
      decorView.addView(debugButton);
    }

    mHostButtonMap.put(context, debugButton);
    mDebugButtonStack.push(debugButton);
  }

  @Override
  public void detachFromHost(Context context) {
    DevFloatButton button = mHostButtonMap.get(context);
    if (button != null) {
      mDebugButtonStack.remove(button);
      mHostButtonMap.remove(context);
      ViewParent parent = button.getParent();
      if (parent instanceof ViewGroup) {
        ((ViewGroup) parent).removeView(button);
      }
    }
  }

  @Override
  public void handleException(final Throwable throwable) {
    if (mProgressDialog != null) {
      mProgressDialog.dismiss();
    }

    if (mDebugButtonStack.size() <= 0) {
      return;
    }

    if (mExceptionDialog != null && mExceptionDialog.isShowing()) {
      return;
    }

    UIThreadUtils.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (mDebugButtonStack.size() > 0) {
          // 用栈顶那个context
          mExceptionDialog = new DevExceptionDialog(mDebugButtonStack.peek().getContext());
          mExceptionDialog.handleException(throwable);
          mExceptionDialog.setOnReloadListener(DevServerImpl.this);
          mExceptionDialog.show();
        }
      }
    });

  }

  @Override
  public void onReload() {
    reload();
  }

  @SuppressWarnings("unused")
  @Override
  public void onHandleRemoteDebugException(Throwable t) {
    if (mDebugButtonStack.isEmpty()) {
      mServerCallBack.onInitDevError(t);
    } else {
      handleException(t);
    }
  }

  @Override
  public void onCompileSuccess() {
    reload();
  }

  @Override
  public void onLiveReloadReady() {
    reload();
  }
}
