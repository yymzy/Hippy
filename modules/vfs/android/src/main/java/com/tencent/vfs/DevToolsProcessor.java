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

package com.tencent.vfs;

import androidx.annotation.NonNull;
import com.tencent.vfs.ResourceDataHolder.RequestFrom;
import com.tencent.vfs.VfsManager.ProcessorCallback;

public class DevToolsProcessor extends Processor {

    private final long mRuntimeId;

    public DevToolsProcessor(long runtimeId) {
        mRuntimeId = runtimeId;
    }

    @Override
    public boolean handleRequestSync(@NonNull ResourceDataHolder holder) {
        onNetworkRequest(holder);
        return false;
    }

    @Override
    public void handleRequestAsync(@NonNull ResourceDataHolder holder, @NonNull ProcessorCallback callback) {
        onNetworkRequest(holder);
        super.handleRequestAsync(holder, callback);
    }

    @Override
    public void handleResponseAsync(@NonNull ResourceDataHolder holder, @NonNull ProcessorCallback callback) {
        onNetworkResponse(holder);
        super.handleResponseAsync(holder, callback);
    }

    @Override
    public void handleResponseSync(@NonNull ResourceDataHolder holder) {
        onNetworkResponse(holder);
        super.handleResponseSync(holder);
    }

    private void onNetworkRequest(ResourceDataHolder holder) {
        if (holder.requestFrom == RequestFrom.NATIVE) {
            return;
        }
        holder.requestId = String.valueOf(System.currentTimeMillis());
        onNetworkRequest(mRuntimeId, holder.requestId, holder);
    }

    private void onNetworkResponse(ResourceDataHolder holder) {
        if (holder.requestFrom == RequestFrom.NATIVE) {
            return;
        }
        onNetworkResponse(mRuntimeId, holder.requestId, holder);
    }

    /**
     * Network Request notification for devtools
     */
    @SuppressWarnings("JavaJniMissingFunction")
    public native void onNetworkRequest(long id, String requestId, ResourceDataHolder holder);

    /**
     * Network Response notification for devtools
     */
    @SuppressWarnings("JavaJniMissingFunction")
    public native void onNetworkResponse(long id, String requestId, ResourceDataHolder holder);
}
